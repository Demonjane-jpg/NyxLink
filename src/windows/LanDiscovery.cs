using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace BedMirror;

public sealed class LanDiscovery : IDisposable
{
    public const int Port = 47996;
    const string Protocol = "nyxlink/1";
    readonly SunshineHost host;
    readonly UdpClient socket;
    readonly IPAddress address;
    readonly IPAddress mask;
    readonly string fingerprint;
    readonly object sync = new();
    PairingAttempt? pending;
    long nextRequest;
    bool disposed;
    public event Action<PairingAttempt>? PairingRequested;
    public event Action<PairingAttempt, bool>? PairingFinished;

    public sealed class PairingAttempt
    {
        public required IPAddress Address { get; init; }
        public required string Token { get; init; }
        public required string Code { get; init; }
        public required long Deadline { get; init; }
        public CancellationTokenSource Cancellation { get; } = new();
    }

    public LanDiscovery(SunshineHost host)
    {
        this.host = host;
        address = IPAddress.Parse(host.Address);
        mask = NetworkInterface.GetAllNetworkInterfaces()
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .First(info => info.Address.Equals(address)).IPv4Mask;
        fingerprint = Convert.ToHexString(SHA256.HashData(host.ServerCertificate.RawData)).ToLowerInvariant();
        socket = new UdpClient(new IPEndPoint(IPAddress.Any, Port));
        _ = Receive();
    }

    public static string DisplayCode(string pin)
    {
        if (pin.Length != 4 || !pin.All(char.IsAsciiDigit)) throw new ArgumentException("Expected a four-digit streaming PIN.");
        return pin + (pin.Sum(digit => digit - '0') % 10).ToString(System.Globalization.CultureInfo.InvariantCulture);
    }

    public static bool SameSubnet(IPAddress local, IPAddress peer, IPAddress mask)
    {
        var a = local.GetAddressBytes();
        var b = peer.GetAddressBytes();
        var m = mask.GetAddressBytes();
        return a.Length == 4 && b.Length == 4 && m.Length == 4 &&
            Enumerable.Range(0, 4).All(i => (a[i] & m[i]) == (b[i] & m[i]));
    }

    async Task Receive()
    {
        while (true)
        {
            UdpReceiveResult packet;
            try { packet = await socket.ReceiveAsync().ConfigureAwait(false); }
            catch (ObjectDisposedException) { return; }
            catch (SocketException)
            {
                lock (sync) { if (disposed) return; }
                await Task.Delay(100).ConfigureAwait(false);
                continue;
            }
            try
            {
                var peer = packet.RemoteEndPoint.Address;
                if (packet.Buffer.Length > 256 || !HostConfig.IsLocal(peer) ||
                    !host.AllowReconnect || (!host.Ready && !host.Privacy)) continue;
                var fields = Encoding.ASCII.GetString(packet.Buffer).Split(' ');
                if (fields.Length != 3 || fields[0] != Protocol || fields[2].Length != 32 || !fields[2].All(char.IsAsciiHexDigit)) continue;
                if (!SameSubnet(address, peer, mask) && fields[1] != "discover") continue;
                object? response = fields[1] switch
                {
                    "discover" => new { protocol = Protocol, token = fields[2], name = Environment.MachineName, fingerprint, remoteAddress = HostConfig.RemoteAddresses().FirstOrDefault()?.ToString() ?? "" },
                    "pair" => new { protocol = Protocol, token = fields[2], status = RequestPairing(peer, fields[2]) },
                    "cancel" => new { protocol = Protocol, token = fields[2], status = Cancel(peer, fields[2]) },
                    _ => null
                };
                if (response != null)
                    await socket.SendAsync(JsonSerializer.SerializeToUtf8Bytes(response), packet.RemoteEndPoint).ConfigureAwait(false);
            }
            catch (Exception e) when (e is SocketException or ObjectDisposedException) { }
        }
    }

    string RequestPairing(IPAddress peer, string token)
    {
        PairingAttempt attempt;
        lock (sync)
        {
            if (disposed || !host.Ready) return "unavailable";
            if (pending != null)
                return pending.Token == token && pending.Address.Equals(peer) && !pending.Cancellation.IsCancellationRequested ? "ready" : "busy";
            if (Environment.TickCount64 < nextRequest) return "busy";
            nextRequest = Environment.TickCount64 + 5000;
            attempt = new PairingAttempt
            {
                Address = peer, Token = token, Deadline = Environment.TickCount64 + 90000,
                Code = DisplayCode(RandomNumberGenerator.GetInt32(10000).ToString("D4", System.Globalization.CultureInfo.InvariantCulture))
            };
            pending = attempt;
        }
        PairingRequested?.Invoke(attempt);
        _ = CompletePairing(attempt);
        return "ready";
    }

    string Cancel(IPAddress peer, string token)
    {
        lock (sync)
        {
            if (pending is { } attempt && attempt.Token == token && attempt.Address.Equals(peer))
                attempt.Cancellation.Cancel();
        }
        return "cancelled";
    }

    async Task CompletePairing(PairingAttempt attempt)
    {
        bool success = false;
        string? pairingId = null;
        try
        {
            while (!attempt.Cancellation.IsCancellationRequested && Environment.TickCount64 < attempt.Deadline && host.Ready)
            {
                var list = (await host.Get("/api/pin").ConfigureAwait(false))["pairings"]?.AsArray();
                var matching = list?.FirstOrDefault(item => item?["name"]?.ToString() == "nyxlink-" + attempt.Token);
                if (matching?["id"]?.ToString() is { } id)
                {
                    pairingId = id;
                    if (!attempt.Cancellation.IsCancellationRequested)
                    {
                        await host.Post("/api/pin", new { pairing_id = id, pin = attempt.Code[..4], name = "NyxLink phone" }).ConfigureAwait(false);
                        success = true;
                    }
                    break;
                }
                await Task.Delay(250, attempt.Cancellation.Token).ConfigureAwait(false);
            }
        }
        catch (Exception e) when (e is IOException or HttpRequestException or InvalidOperationException or OperationCanceledException) { }
        finally
        {
            if (!success && pairingId != null && host.Ready)
            {
                try { await host.CancelPairing(pairingId).ConfigureAwait(false); }
                catch (Exception e) when (e is IOException or HttpRequestException or InvalidOperationException or OperationCanceledException) { }
            }
            lock (sync) { pending = null; }
            PairingFinished?.Invoke(attempt, success);
        }
    }

    public void Dispose()
    {
        lock (sync)
        {
            if (disposed) return;
            disposed = true;
            pending?.Cancellation.Cancel();
            socket.Dispose();
        }
    }
}
