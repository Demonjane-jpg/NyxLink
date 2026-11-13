using BedMirror;
using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;

var root = Path.GetFullPath(Path.Combine(AppContext.BaseDirectory, "../../../../../"));
if(args.Contains("--snapshot")) { Console.WriteLine(JsonSerializer.Serialize(new { audio=AudioControl.DeviceSnapshot(),status=AudioControl.Status(),screens=Screen.AllScreens.Select(s=>new{s.DeviceName,s.Bounds,s.Primary}) },HostConfig.Json));return; }
if(args.Contains("--input-target-only")) { InputTarget.Open(root);await Task.Delay(TimeSpan.FromHours(2));return; }
Environment.SetEnvironmentVariable("BEDMIRROR_DATA", Path.Combine(root, "artifacts", args.Contains("--serve-for-device") ? "device-host-data" : args.Contains("--integration") ? "integration-data" : "core-data"));
Directory.CreateDirectory(HostConfig.Data);
if(args.Contains("--serve-for-device")) { await DeviceHost.Run(root); return; }
var results = new List<object>();
void Check(string name, bool value) { results.Add(new { name, passed = value }); Console.WriteLine($"{(value ? "PASS" : "FAIL")} {name}"); if (!value) throw new Exception(name); }
var config = HostConfig.Generate("192.168.1.10", "monitor-id", true);
foreach (var setting in new[] { "dd_configuration_option", "dd_resolution_option", "dd_refresh_rate_option", "dd_hdr_option" }) Check(setting + " disabled", config.Contains(setting + " = disabled"));
Check("UPnP disabled", config.Contains("upnp = disabled"));
Check("LAN encryption required", config.Contains("lan_encryption_mode = 2"));
Check("host administration local only", config.Contains("origin_web_ui_allowed = pc"));
Check("no automatic audio driver installation", config.Contains("install_steam_audio_drivers = disabled"));
Check("virtual audio routing disabled", config.Contains("virtual_sink = BedMirror-No-Audio-Redirect"));
Check("audio fallback disabled when no endpoint", HostConfig.Generate("192.168.1.10", "id", false).Contains("stream_audio = disabled"));
Check("public address rejected", !HostConfig.IsLocal(IPAddress.Parse("8.8.8.8")));
Check("private address accepted", HostConfig.IsLocal(IPAddress.Parse("192.168.1.4")));
Check("Tailscale CGNAT private address accepted", HostConfig.IsLocal(IPAddress.Parse("100.100.42.15")) && HostConfig.IsRemotePrivate(IPAddress.Parse("100.100.42.15")));
Check("non-CGNAT 100.x public address rejected", !HostConfig.IsLocal(IPAddress.Parse("100.63.255.254")) && !HostConfig.IsLocal(IPAddress.Parse("100.128.0.1")));
Check("LAN address not classified as remote CGNAT", !HostConfig.IsRemotePrivate(IPAddress.Parse("192.168.1.4")));
Check("pairing code keeps leading zero and adds a check digit", LanDiscovery.DisplayCode("0199") == "01999");
var subnet = IPAddress.Parse("255.255.255.0");
Check("discovery allows peers on the selected subnet", LanDiscovery.SameSubnet(IPAddress.Parse("192.168.1.4"), IPAddress.Parse("192.168.1.200"), subnet));
Check("discovery rejects peers on another private subnet", !LanDiscovery.SameSubnet(IPAddress.Parse("192.168.1.4"), IPAddress.Parse("192.168.2.4"), subnet));
using var host = new SunshineHost();
Check("missing client certificate rejected", !host.Trusted(null));
using var key = RSA.Create(2048);
var request = new CertificateRequest("CN=Test client", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
using var rawClient = request.CreateSelfSigned(DateTimeOffset.UtcNow.AddMinutes(-1), DateTimeOffset.UtcNow.AddDays(1));
using var client = X509CertificateLoader.LoadPkcs12(rawClient.Export(X509ContentType.Pfx), null);
File.WriteAllText(HostConfig.FilePath("state.json"),JsonSerializer.Serialize(new { root = new { uniqueid = "test", named_devices = new[] { new { name = "test", uuid = "test-id", cert = client.ExportCertificatePem(), enabled = true } } } }));
Check("paired certificate accepted", host.Trusted(client));
Check("different certificate rejected", !host.Trusted(host.ServerCertificate));
var duplicateState=JsonSerializer.Serialize(new {root=new {uniqueid="test",named_devices=new[]{new{name="old",uuid="old",cert=client.ExportCertificatePem(),enabled=true},new{name="new",uuid="new",cert=client.ExportCertificatePem(),enabled=true}}}});
var normalized=PairingState.Normalize(duplicateState)!;
using(var normalizedDoc=JsonDocument.Parse(normalized)) {var entries=normalizedDoc.RootElement.GetProperty("root").GetProperty("named_devices");Check("re-pairing duplicates retain one enabled identity",entries.GetArrayLength()==1&&entries[0].GetProperty("enabled").GetBoolean()&&entries[0].GetProperty("uuid").GetString()=="new");}
Check("pairing normalization is idempotent",PairingState.Normalize(normalized)==null);
var conflictingState=duplicateState.Replace("\"enabled\":true","\"enabled\":false");
using(var disabledDoc=JsonDocument.Parse(PairingState.Normalize(conflictingState)!)) Check("disabled duplicate identities stay disabled",!disabledDoc.RootElement.GetProperty("root").GetProperty("named_devices")[0].GetProperty("enabled").GetBoolean());
var mixedNode=System.Text.Json.Nodes.JsonNode.Parse(duplicateState)!;mixedNode["root"]!["named_devices"]![0]!["enabled"]=false;
Check("conflicting revocation states are not auto-enabled",PairingState.Normalize(mixedNode.ToJsonString())==null);
File.WriteAllText(HostConfig.FilePath("state.json"),JsonSerializer.Serialize(new { root = new { uniqueid = "test", named_devices = new[] { new { name = "test", uuid = "test-id", cert = client.ExportCertificatePem(), enabled = false } } } }));
Check("disabled certificate rejected", !host.Trusted(client));
File.WriteAllText(HostConfig.FilePath("state.json"),"""{"root":{"uniqueid":"test","named_devices":""}}""");
Check("empty upstream pairing state rejects access",!host.Trusted(client));
ReportStore.ClearAll();
Check("report store starts empty", ReportStore.GetAll().Count == 0);
var testCrash = new ReportItem {
    Type = "crash",
    Title = "Test Crash bearer secret-token-1234",
    Description = "Null pointer password: super_secret_pass",
    AppVersion = "1.0.0",
    StackTrace = "at foo.bar() in token=abc12345"
};
var saved = ReportStore.Save(testCrash);
Check("report store sanitizes tokens and passwords", !saved.Title.Contains("secret-token") && !saved.Description.Contains("super_secret_pass") && !saved.StackTrace!.Contains("abc12345"));
Check("report store persists report", ReportStore.GetAll().Count == 1);
var summary = ReportStore.GetSummary();
Check("report summary counts crashes", summary.TotalCrashes == 1 && summary.CrashFrequencyByVersion.GetValueOrDefault("1.0.0", 0) == 1);
ReportStore.UpdateStatus(saved.Id, "resolved");
Check("report status update succeeds", ReportStore.GetAll()[0].Status == "resolved");
ReportStore.Delete(saved.Id);
Check("report deletion succeeds", ReportStore.GetAll().Count == 0);
if (args.Contains("--integration"))
{
    File.WriteAllText(HostConfig.FilePath("state.json"),JsonSerializer.Serialize(new { root = new { uniqueid = "test", named_devices = new[] { new { name = "test", uuid = "test-id", cert = client.ExportCertificatePem(), enabled = true } } } }));
    var before = Screen.AllScreens.Select(s => new { s.DeviceName, x=s.Bounds.X,y=s.Bounds.Y,w=s.Bounds.Width,h=s.Bounds.Height,s.Primary }).ToArray();
    var audioBefore=JsonSerializer.Serialize(AudioControl.DeviceSnapshot());
    host.Message += message => { if(message.Contains("Error") || message.Contains("Fatal")) Console.WriteLine(message); };
    var address = HostConfig.Addresses().FirstOrDefault() ?? throw new Exception("No private LAN interface");
    Console.WriteLine("Starting host on " + address);
    await host.Start(address.ToString());
    Check("host starts under current user", host.Running);
    Check("local authenticated admin works", (await host.Get("/api/config"))["status"]!.ToString() == "true");
    Check("active monitors enumerated", host.Monitors.Count > 0);
    Console.WriteLine(JsonSerializer.Serialize(host.Monitors,HostConfig.Json));
    await using var api = new CompanionApi();
    await api.Start(host);
    using var secureHandler = new HttpClientHandler { UseProxy = false, ServerCertificateCustomValidationCallback = (_, certificate, _, _) => certificate != null && certificate.RawData.SequenceEqual(host.ServerCertificate.RawData) };
    secureHandler.ClientCertificates.Add(client);
    using var secure = new HttpClient(secureHandler) { BaseAddress = new Uri($"https://{host.Address}:47995"), Timeout = TimeSpan.FromSeconds(5) };
    Check("companion accepts paired mutual TLS", (await secure.GetAsync("/v1/status")).IsSuccessStatusCode);
    using var anonymous = new HttpClient(new HttpClientHandler { UseProxy = false, ServerCertificateCustomValidationCallback = (_, c, _, _) => c != null && c.RawData.SequenceEqual(host.ServerCertificate.RawData) }) { Timeout = TimeSpan.FromSeconds(3) };
    var rejected = false;
    try { var response = await anonymous.GetAsync($"https://{host.Address}:47995/v1/status"); rejected = !response.IsSuccessStatusCode; } catch(HttpRequestException) { rejected = true; }
    Check("companion rejects missing client certificate", rejected);
    using(var socket = new System.Net.Sockets.TcpClient()) { try { await socket.ConnectAsync(address,47990).WaitAsync(TimeSpan.FromSeconds(2)); } catch { } Check("Sunshine admin unavailable on LAN", !socket.Connected); }
    Check("authenticated cursor query works",(await secure.GetAsync("/v1/cursor")).IsSuccessStatusCode);
    host.Power.Update(true,true);Check("temporary sleep request created",host.Power.Active);host.Power.Update(false,false);Check("temporary sleep request released",!host.Power.Active);
    var after = Screen.AllScreens.Select(s => new { s.DeviceName,x=s.Bounds.X,y=s.Bounds.Y,w=s.Bounds.Width,h=s.Bounds.Height,s.Primary }).ToArray();
    Check("display topology unchanged", JsonSerializer.Serialize(before) == JsonSerializer.Serialize(after));
    foreach (var monitor in host.Monitors.ToArray()) { await host.Switch(monitor.Id); Check("monitor selection " + monitor.Name, host.Selected == monitor.Id && host.Ready); }
    await host.Pause();Check("privacy stops host media", !host.Running && host.Privacy);
    Check("privacy retains authenticated companion connection", (await secure.GetAsync("/v1/status")).IsSuccessStatusCode);
    await host.Resume();Check("privacy resumes without deleting pairing", host.Running && !host.Privacy);
    Check("audio endpoint assignments unchanged",audioBefore==JsonSerializer.Serialize(AudioControl.DeviceSnapshot()));
    using var forgetRequest = new HttpRequestMessage(HttpMethod.Post, "/v1/forget");
    using var forgetTimeout = new CancellationTokenSource(TimeSpan.FromSeconds(45));
    using var forgetClient = new HttpClient(secureHandler, disposeHandler: false) { BaseAddress = secure.BaseAddress, Timeout = Timeout.InfiniteTimeSpan };
    Check("authenticated phone can forget itself", (await forgetClient.SendAsync(forgetRequest, forgetTimeout.Token)).IsSuccessStatusCode);
    Check("revocation terminates existing API authorization",(await secure.GetAsync("/v1/status")).StatusCode==HttpStatusCode.Forbidden);
    await host.Stop();Check("disconnect blocks new companion access", !host.Trusted(client) && !host.Running);
}
File.WriteAllText(Path.Combine(root,"artifacts",args.Contains("--integration")?"windows-integration-tests.json":"windows-core-tests.json"),JsonSerializer.Serialize(results,HostConfig.Json));
