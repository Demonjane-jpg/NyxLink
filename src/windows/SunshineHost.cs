using System.Diagnostics;
using System.Net.Http.Headers;
using System.Net.Http.Json;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace BedMirror;

public sealed record MonitorInfo(string Id, string Name, int Width, int Height, string Display, bool Primary, int X, int Y);

public sealed class SunshineHost : IDisposable
{
    readonly SemaphoreSlim gate = new(1, 1);
    Process? process;
    LanRelay? relay;
    HttpClient? admin;
    string password = "";
    LiveSwitch? liveSwitch;
    sealed record LiveSwitch(Guid Ticket, string Monitor, long LogOffset, int ProcessId, long Expires);
    static readonly Regex captureBounds = new(@"Capture size\s*:\s*(\d+)x(\d+)\r?\n\s*Offset\s*:\s*(-?\d+)x(-?\d+)", RegexOptions.CultureInvariant);
    public string Address { get; private set; } = "";
    public string Selected { get; private set; } = "";
    public bool Running => process is { HasExited: false };
    public bool Ready => Running && relay != null;
    public bool Privacy { get; private set; }
    public bool AllowReconnect { get; private set; } = true;
    public event Action<string>? Message;
    public X509Certificate2 ServerCertificate { get; } = HostConfig.Certificate();
    public List<MonitorInfo> Monitors { get; private set; } = [];
    public SessionPower Power { get; } = new();
    string connectedDevice = "";
    DateTimeOffset lastSessionHeartbeat;
    public string ConnectedDevice => Ready && DateTimeOffset.UtcNow-lastSessionHeartbeat<TimeSpan.FromSeconds(20)?connectedDevice:"";
    public void Session(X509Certificate2 certificate,bool active,bool preventSleep)
    {
        if(!Trusted(certificate))throw new UnauthorizedAccessException("Device is no longer paired.");
        lastSessionHeartbeat=DateTimeOffset.UtcNow;
        connectedDevice=active?"Paired phone":"";
        Power.Update(active&&Ready&&!Privacy,preventSleep);
    }
    public SunshineHost()
    {
        if (File.Exists(HostConfig.FilePath("monitor.txt"))) Selected = File.ReadAllText(HostConfig.FilePath("monitor.txt")).Trim();
    }

    public async Task Start(string address)
    {
        await gate.WaitAsync();
        try { AllowReconnect = true; Privacy = false; await StartCore(address); }
        catch { await StopCore(); throw; }
        finally { gate.Release(); }
    }
    async Task StartCore(string address)
    {
        if (Running) return;
        if (!HostConfig.Addresses().Any(a => a.ToString() == address)) throw new InvalidOperationException("Choose an active private LAN address.");
        Address = address;
        if(AudioControl.DefaultPlaybackIsSteam()) throw new InvalidOperationException("Sunshine would automatically redirect the existing Steam Streaming Speakers default device. NyxLink stopped to preserve your devices. Select a physical playback device yourself before starting.");
        PairingState.RepairFile(HostConfig.FilePath("state.json"));
        password = HostConfig.PrepareCredentials();
        var configuredMonitor = Selected;
        File.WriteAllText(HostConfig.FilePath("sunshine.conf"), HostConfig.Generate(address, Selected, AudioControl.HasPlaybackDevice()));
        var start = new ProcessStartInfo(Path.Combine(HostConfig.Runtime, "sunshine.exe")) { WorkingDirectory = HostConfig.Runtime, UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        start.ArgumentList.Add(HostConfig.FilePath("sunshine.conf"));
        process = Process.Start(start) ?? throw new IOException("Could not start bundled Sunshine.");
        process.OutputDataReceived += (_, e) => { if (e.Data != null) Message?.Invoke(e.Data); };
        process.ErrorDataReceived += (_, e) => { if (e.Data != null) Message?.Invoke(e.Data); };
        process.BeginOutputReadLine(); process.BeginErrorReadLine();
        admin?.Dispose();
        // The LAN relay excludes 47990. Sunshine administration never listens on a LAN interface.
        var handler = new SocketsHttpHandler { UseProxy = false, ConnectTimeout = TimeSpan.FromSeconds(1), SslOptions = new() { RemoteCertificateValidationCallback = (_, cert, _, _) => cert != null && cert.GetRawCertData().SequenceEqual(ServerCertificate.RawData) } };
        admin = new HttpClient(handler) { BaseAddress = new Uri("https://127.0.0.1:47990"), Timeout = TimeSpan.FromSeconds(35) };
        admin.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes("bedmirror:" + password)));
        Exception? last = null;
        for (var i = 0; i < 200; i++) { if (process.HasExited) throw new IOException("Sunshine exited. Read sunshine.log for details."); try { await Get("/api/config"); await RefreshMonitors();if(configuredMonitor.Length>0&&configuredMonitor!=Selected&&Selected.Length>0){await StopCore();await StartCore(address);return;}relay = new LanRelay(System.Net.IPAddress.Parse(address)); return; } catch (Exception e) { last = e; await Task.Delay(200); } }
        throw new IOException("Sunshine did not become ready: " + last?.Message, last);
    }

    public async Task<JsonObject> Get(string route)
    {
        if (admin == null || !Running) throw new IOException("Streaming host is stopped.");
        using var response = await admin.GetAsync(route); response.EnsureSuccessStatusCode();
        return JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
    }
    public Task<JsonObject> Post(string route, object body) => Send(HttpMethod.Post, route, body);

    public Task<JsonObject> CancelPairing(string id) => Send(HttpMethod.Delete, "/api/pin", new { pairing_id = id });

    async Task<JsonObject> Send(HttpMethod method, string route, object body)
    {
        var token = await Get("/api/csrf-token");
        using var req = new HttpRequestMessage(method, route) { Content = JsonContent.Create(body) };
        req.Headers.Add("X-CSRF-Token", token["csrf_token"]?.ToString() ?? token["token"]?.ToString() ?? "");
        using var response = await admin!.SendAsync(req); response.EnsureSuccessStatusCode();
        var result = JsonNode.Parse(await response.Content.ReadAsStringAsync())!.AsObject();
        if (result["status"]?.ToString() == "false") throw new IOException("The host rejected the request.");
        return result;
    }
    public async Task RefreshMonitors()
    {
        using var logStream = new FileStream(HostConfig.FilePath("sunshine.log"), FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
        using var logReader = new StreamReader(logStream);
        var text = await logReader.ReadToEndAsync();
        var marker = text.LastIndexOf("Currently available display devices:", StringComparison.Ordinal);
        if (marker < 0) return;
        var begin = text.IndexOf('[', marker);
        if (begin < 0) return;
        var tail = text[begin..];
        // Parse one JSON value, leaving the rest of the log untouched.
        var reader = new Utf8JsonReader(System.Text.Encoding.UTF8.GetBytes(tail));
        using var doc = JsonDocument.ParseValue(ref reader);
        var found = new List<MonitorInfo>();
        foreach (var item in doc.RootElement.EnumerateArray())
        {
            if (!item.TryGetProperty("info", out var info) || info.ValueKind != JsonValueKind.Object) continue;
            var name = item.GetProperty("friendly_name").GetString() ?? "Monitor";
            // Sunshine enumerates connected desktop outputs. Exclude known indirect/virtual outputs.
            if (Regex.IsMatch(name, "virtual|idd|sunshinehdr", RegexOptions.IgnoreCase)) continue;
            var res = info.GetProperty("resolution");
            var origin=info.GetProperty("origin_point");
            found.Add(new(item.GetProperty("device_id").GetString()!, name, res.GetProperty("width").GetInt32(), res.GetProperty("height").GetInt32(), item.GetProperty("display_name").GetString() ?? "", info.GetProperty("primary").GetBoolean(),origin.GetProperty("x").GetInt32(),origin.GetProperty("y").GetInt32()));
        }
        Monitors = found;
        if (!Monitors.Any(m => m.Id == Selected)) Selected = Monitors.FirstOrDefault(m => m.Primary)?.Id ?? Monitors.FirstOrDefault()?.Id ?? "";
    }
    public async Task Switch(string id)
    {
        await gate.WaitAsync();
        try
        {
            if (!Ready || Privacy) throw new InvalidOperationException("Start or resume NyxLink before switching monitors.");
            if (!Monitors.Any(m => m.Id == id)) throw new ArgumentException("Monitor is no longer available.");
            if (Selected == id) return;

            var previous = Selected;
            await StopCore();
            Selected = id;
            try
            {
                await StartCore(Address);
                if (Selected != id) throw new IOException("The selected monitor is no longer available.");
                File.WriteAllText(HostConfig.FilePath("monitor.txt"), id);
            }
            catch
            {
                await StopCore();
                Selected = previous;
                try { await StartCore(Address); }
                catch { await StopCore(); }
                throw;
            }
        }
        finally { gate.Release(); }
    }
    public async Task<(int Index, Guid Ticket)> PrepareLiveSwitch(string id)
    {
        await gate.WaitAsync();
        try
        {
            if (!Ready || Privacy) throw new InvalidOperationException("Start or resume NyxLink before switching monitors.");
            var monitor = Monitors.Find(m => m.Id == id) ?? throw new ArgumentException("Monitor is no longer available.");
            if (ActiveCapture() == id)
            {
                SaveSelected(id);
                return (-1, Guid.Empty);
            }
            if (liveSwitch is { } pending && pending.Expires > Environment.TickCount64)
                throw new InvalidOperationException("A monitor switch is already in progress.");
            var index = CaptureOutputs.Enumerate().FindIndex(name => string.Equals(name, monitor.Display, StringComparison.OrdinalIgnoreCase));
            if (index is < 0 or > 12) throw new InvalidOperationException("Sunshine can't switch to this capture output.");
            var ticket = Guid.NewGuid();
            liveSwitch = new(ticket, id, new FileInfo(HostConfig.FilePath("sunshine.log")).Length,
                process!.Id, Environment.TickCount64 + 8000);
            return (index, ticket);
        }
        finally { gate.Release(); }
    }

    public async Task ConfirmLiveSwitch(Guid ticket)
    {
        await gate.WaitAsync();
        try
        {
            if (!Ready || liveSwitch is not { } pending || pending.Ticket != ticket || pending.ProcessId != process!.Id)
                throw new InvalidOperationException("The monitor switch expired.");
            try
            {
                while (Environment.TickCount64 < pending.Expires)
                {
                    if (ActiveCapture(pending.LogOffset) == pending.Monitor)
                    {
                        SaveSelected(pending.Monitor);
                        return;
                    }
                    await Task.Delay(100);
                }
                throw new IOException("Sunshine didn't confirm the new capture output.");
            }
            finally { liveSwitch = null; }
        }
        finally { gate.Release(); }
    }

    void SaveSelected(string id)
    {
        File.WriteAllText(HostConfig.FilePath("monitor.txt"), id);
        Selected = id;
    }

    public string? ActiveCapture(long since = 0)
    {
        if (!Ready || Monitors.Count == 0) return null;
        using var log = new FileStream(HostConfig.FilePath("sunshine.log"), FileMode.Open, FileAccess.Read,
            FileShare.ReadWrite | FileShare.Delete);
        if (since > log.Length) return null;
        log.Position = Math.Max(since, log.Length - 65536);
        using var reader = new StreamReader(log);
        var tail = reader.ReadToEnd();
        if (since == 0)
        {
            var connected = tail.LastIndexOf("CLIENT CONNECTED", StringComparison.Ordinal);
            if (connected < 0 || tail.LastIndexOf("CLIENT DISCONNECTED", StringComparison.Ordinal) > connected) return null;
            tail = tail[connected..];
        }
        var match = captureBounds.Matches(tail).LastOrDefault();
        if (match == null) return null;
        int Field(int index) => int.Parse(match.Groups[index].Value, System.Globalization.CultureInfo.InvariantCulture);
        var width = Field(1);
        var height = Field(2);
        var x = Field(3) + Monitors.Min(m => m.X);
        var y = Field(4) + Monitors.Min(m => m.Y);
        return Monitors.Find(m => m.Width == width && m.Height == height && m.X == x && m.Y == y)?.Id;
    }
    public async Task Pause()
    {
        await gate.WaitAsync(); try { Privacy = true; await StopCore(); } finally { gate.Release(); }
    }
    public async Task Resume()
    {
        await gate.WaitAsync(); try { if (!AllowReconnect) throw new IOException("Disconnected on PC. Press Start on PC."); await StartCore(Address); Privacy = false; } finally { gate.Release(); }
    }
    public async Task Stop()
    {
        await gate.WaitAsync(); try { AllowReconnect = false; Privacy = false; await StopCore(); } finally { gate.Release(); }
    }

    public async Task Forget(X509Certificate2 certificate)
    {
        await gate.WaitAsync();
        try
        {
            if (!Ready || !Trusted(certificate)) throw new InvalidOperationException("The paired PC is unavailable.");
            using var stream = new FileStream(HostConfig.FilePath("state.json"), FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            using var document = JsonDocument.Parse(stream);
            var ids = new List<string>();
            foreach (var entry in document.RootElement.GetProperty("root").GetProperty("named_devices").EnumerateArray())
            {
                using var stored = X509Certificate2.CreateFromPem(entry.GetProperty("cert").GetString()!);
                if (stored.RawData.SequenceEqual(certificate.RawData)) ids.Add(entry.GetProperty("uuid").GetString()!);
            }
            stream.Dispose();
            foreach (var id in ids) await Post("/api/clients/unpair", new { uuid = id });
            await StopCore();
            await StartCore(Address);
        }
        finally { gate.Release(); }
    }
    async Task StopCore()
    {
        liveSwitch = null;
        // Tear down the input session before terminating the host process.
        if (Running && admin != null) { try { await Post("/api/apps/close", new { }).WaitAsync(TimeSpan.FromSeconds(3)); await Task.Delay(150); } catch { } }
        relay?.Dispose(); relay = null;
        if (process != null) { try { if (!process.HasExited) { process.Kill(true); process.WaitForExit(4000); } } finally { process.Dispose(); process = null; } }
        Power.Update(false, false);
    }
    public bool Trusted(X509Certificate2? cert)
    {
        if (cert == null || !AllowReconnect) return false;
        try
        {
            using var stateStream=new FileStream(HostConfig.FilePath("state.json"),FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete);
            using var doc = JsonDocument.Parse(stateStream);
            if(!doc.RootElement.TryGetProperty("root",out var root)||!root.TryGetProperty("named_devices",out var devices)||devices.ValueKind!=JsonValueKind.Array)return false;
            foreach (var entry in devices.EnumerateArray())
            {
                if (entry.TryGetProperty("enabled", out var enabled) && string.Equals(enabled.ToString(), "false", StringComparison.OrdinalIgnoreCase)) continue;
                using var stored = X509Certificate2.CreateFromPem(entry.GetProperty("cert").GetString()!);
                if (stored.RawData.SequenceEqual(cert.RawData)) return true;
            }
        } catch (Exception e) when (e is IOException or JsonException or KeyNotFoundException or InvalidOperationException or ArgumentException or System.Security.Cryptography.CryptographicException) { }
        return false;
    }
    public void Dispose() { Task.Run(StopCore).GetAwaiter().GetResult(); Power.Dispose(); admin?.Dispose(); ServerCertificate.Dispose(); gate.Dispose(); }
}
