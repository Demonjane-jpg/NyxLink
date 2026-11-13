using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;

namespace BedMirror;

/// <summary>Byte-transparent forwarding to loopback Sunshine. Never forwards its admin port.</summary>
public sealed class LanRelay : IDisposable
{
    readonly CancellationTokenSource stop = new();
    readonly List<TcpListener> listeners = [];
    readonly List<UdpClient> udp = [];
    readonly ConcurrentDictionary<string, UdpClient> peers = new();
    readonly ConcurrentDictionary<TcpClient, byte> connections = new();
    readonly SemaphoreSlim tcpSlots = new(24);
    public static readonly int[] TcpPorts = [47984, 47989, 48010];
    public static readonly int[] UdpPorts = [47998, 47999, 48000];
    public LanRelay(IPAddress address)
    {
        var active = HostConfig.Addresses();
        if (!active.Contains(address)) throw new ArgumentException("Relay must bind an active private LAN address.");
        var bindAddresses = active.Prepend(address).Distinct().ToArray();
        try
        {
            foreach (var bindAddress in bindAddresses)
            {
                try
                {
                    foreach (var port in TcpPorts) { var listener = new TcpListener(bindAddress, port); listener.Start(16); listeners.Add(listener); _ = Accept(listener, port); }
                    foreach (var port in UdpPorts) { var socket = new UdpClient(new IPEndPoint(bindAddress, port)); socket.Client.ReceiveBufferSize = 4 * 1024 * 1024; socket.Client.SendBufferSize = 4 * 1024 * 1024; udp.Add(socket); _ = ForwardDatagrams(socket, port); }
                }
                catch when (!bindAddress.Equals(address)) { }
            }
        }
        catch { Dispose(); throw; }
    }
    async Task Accept(TcpListener listener, int port)
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                var client = await listener.AcceptTcpClientAsync(stop.Token);
                if (client.Client.RemoteEndPoint is not IPEndPoint remote || !HostConfig.IsLocal(remote.Address) || !tcpSlots.Wait(0)) { client.Dispose(); continue; }
                connections.TryAdd(client, 0); _ = ForwardStream(client, port);
            }
            catch (Exception e) when (e is OperationCanceledException or ObjectDisposedException or SocketException) { if (!stop.IsCancellationRequested) await Task.Delay(100); }
        }
    }
    async Task ForwardStream(TcpClient client, int port)
    {
        using var target = new TcpClient(AddressFamily.InterNetwork);
        using (client)
        using (var timeout = CancellationTokenSource.CreateLinkedTokenSource(stop.Token))
        {
            try
            {
                timeout.CancelAfter(TimeSpan.FromSeconds(5));
                await target.ConnectAsync(IPAddress.Loopback, port, timeout.Token);
                timeout.CancelAfter(Timeout.InfiniteTimeSpan);
                client.NoDelay = target.NoDelay = true;
                var inbound = client.GetStream(); var outbound = target.GetStream();
                await Task.WhenAny(inbound.CopyToAsync(outbound, timeout.Token), outbound.CopyToAsync(inbound, timeout.Token));
                timeout.Cancel();
            }
            catch (Exception e) when (e is IOException or SocketException or OperationCanceledException or ObjectDisposedException) { }
            finally { connections.TryRemove(client, out _); tcpSlots.Release(); }
        }
    }
    async Task ForwardDatagrams(UdpClient listener, int port)
    {
        while (!stop.IsCancellationRequested)
        {
            try
            {
                var packet = await listener.ReceiveAsync(stop.Token);
                if (!HostConfig.IsLocal(packet.RemoteEndPoint.Address)) continue;
                var key = port + "/" + packet.RemoteEndPoint;
                if (!peers.TryGetValue(key, out var target))
                {
                    if (peers.Count >= 24) continue;
                    target = new UdpClient(new IPEndPoint(IPAddress.Loopback, 0));
                    target.Connect(IPAddress.Loopback, port);
                    target.Client.ReceiveBufferSize = 4 * 1024 * 1024;
                    target.Client.SendBufferSize = 4 * 1024 * 1024;
                    peers[key] = target;
                    _ = ReturnDatagrams(listener, target, packet.RemoteEndPoint, key);
                }
                await target.SendAsync(packet.Buffer, stop.Token);
            }
            catch (Exception e) when (e is SocketException or OperationCanceledException or ObjectDisposedException) { if (!stop.IsCancellationRequested) await Task.Delay(50); }
        }
    }
    async Task ReturnDatagrams(UdpClient listener, UdpClient target, IPEndPoint client, string key)
    {
        try
        {
            while (!stop.IsCancellationRequested)
            {
                using var idle = CancellationTokenSource.CreateLinkedTokenSource(stop.Token); idle.CancelAfter(TimeSpan.FromSeconds(40));
                var packet = await target.ReceiveAsync(idle.Token);
                await listener.SendAsync(packet.Buffer, client, stop.Token);
            }
        }
        catch (Exception e) when (e is SocketException or OperationCanceledException or ObjectDisposedException) { }
        finally { peers.TryRemove(key, out _); target.Dispose(); }
    }
    public void Dispose()
    {
        if (stop.IsCancellationRequested) return;
        stop.Cancel(); foreach (var l in listeners) l.Stop(); foreach (var l in udp) l.Dispose(); foreach (var p in peers.Values) p.Dispose(); foreach (var c in connections.Keys) c.Dispose();
    }
}
