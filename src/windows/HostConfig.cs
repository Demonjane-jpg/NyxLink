using System.Net;
using System.Net.NetworkInformation;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;

namespace BedMirror;

public static class HostConfig
{
    public const int StreamPort = 47989, ApiPort = 47995;
    public static readonly string Data = Environment.GetEnvironmentVariable("BEDMIRROR_DATA") ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "BedMirror");
    public static string FilePath(string name) => Path.Combine(Data, name);
    public static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web) { WriteIndented = true };
    public static string Runtime => FindRuntime();
    static string FindRuntime()
    {
        foreach (var candidate in new[] { Path.Combine(AppContext.BaseDirectory, "sunshine"), Path.Combine(Environment.CurrentDirectory, "vendor", "sunshine", "Sunshine") })
            if (File.Exists(Path.Combine(candidate, "sunshine.exe"))) return candidate;
        return Path.Combine(AppContext.BaseDirectory, "sunshine");
    }

    public static IPAddress[] Addresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType != NetworkInterfaceType.Loopback)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses).Select(a => a.Address)
        .Where(a => a.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork && IsLocal(a))
        .Distinct()
        .OrderBy(IsRemotePrivate)
        .ToArray();
    public static IPAddress[] RemoteAddresses() => Addresses().Where(IsRemotePrivate).ToArray();
    public static bool IsRemotePrivate(IPAddress address)
    {
        if (address.IsIPv4MappedToIPv6) address = address.MapToIPv4();
        var a = address.GetAddressBytes();
        return a.Length == 4 && a[0] == 100 && a[1] >= 64 && a[1] <= 127;
    }
    public static bool IsLocal(IPAddress address)
    {
        if (address.IsIPv4MappedToIPv6) address = address.MapToIPv4();
        if (IPAddress.IsLoopback(address)) return true;
        var a = address.GetAddressBytes();
        return a.Length == 4 && (a[0] == 10 || a[0] == 192 && a[1] == 168 || a[0] == 172 && a[1] >= 16 && a[1] <= 31 || a[0] == 169 && a[1] == 254 || IsRemotePrivate(address));
    }
    static string ConfigPath(string name) => FilePath(name).Replace('\\', '/');
    public static string Generate(string address, string monitor, bool audio) => $"""
        sunshine_name = NyxLink
        port = 47989
        bind_address = 127.0.0.1
        address_family = ipv4
        upnp = disabled
        origin_web_ui_allowed = pc
        lan_encryption_mode = 2
        wan_encryption_mode = 2
        dd_configuration_option = disabled
        dd_resolution_option = disabled
        dd_refresh_rate_option = disabled
        dd_hdr_option = disabled
        output_name = {monitor}
        global_prep_cmd = []
        controller = disabled
        install_steam_audio_drivers = disabled
        audio_sink =
        virtual_sink = BedMirror-No-Audio-Redirect
        stream_audio = {(audio ? "enabled" : "disabled")}
        system_tray = disabled
        notify_pre_releases = disabled
        pkey = {ConfigPath("server.key")}
        cert = {ConfigPath("server.crt")}
        file_state = {ConfigPath("state.json")}
        credentials_file = {ConfigPath("credentials.json")}
        file_apps = {ConfigPath("apps.json")}
        log_path = {ConfigPath("sunshine.log")}
        """;

    public static X509Certificate2 Certificate()
    {
        Directory.CreateDirectory(Data);
        var security = new System.Security.AccessControl.DirectorySecurity();
        security.SetAccessRuleProtection(true, false);
        security.AddAccessRule(new System.Security.AccessControl.FileSystemAccessRule(System.Security.Principal.WindowsIdentity.GetCurrent().User!, System.Security.AccessControl.FileSystemRights.FullControl, System.Security.AccessControl.InheritanceFlags.ContainerInherit | System.Security.AccessControl.InheritanceFlags.ObjectInherit, System.Security.AccessControl.PropagationFlags.None, System.Security.AccessControl.AccessControlType.Allow));
        security.AddAccessRule(new System.Security.AccessControl.FileSystemAccessRule(new System.Security.Principal.SecurityIdentifier(System.Security.Principal.WellKnownSidType.LocalSystemSid, null), System.Security.AccessControl.FileSystemRights.FullControl, System.Security.AccessControl.InheritanceFlags.ContainerInherit | System.Security.AccessControl.InheritanceFlags.ObjectInherit, System.Security.AccessControl.PropagationFlags.None, System.Security.AccessControl.AccessControlType.Allow));
        System.IO.FileSystemAclExtensions.SetAccessControl(new DirectoryInfo(Data), security);
        var keyPath = FilePath("server.key"); var certPath = FilePath("server.crt");
        if (!File.Exists(keyPath) || !File.Exists(certPath))
        {
            using var rsa = RSA.Create(2048);
            var req = new CertificateRequest("CN=BedMirror", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            req.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
            using var cert = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(10));
            File.WriteAllText(keyPath, rsa.ExportPkcs8PrivateKeyPem());
            File.WriteAllText(certPath, cert.ExportCertificatePem());
        }
        using var pem = X509Certificate2.CreateFromPemFile(certPath, keyPath);
        return X509CertificateLoader.LoadPkcs12(pem.Export(X509ContentType.Pfx), null);
    }

    public static string PrepareCredentials()
    {
        // A fresh admin password is held only in memory; Sunshine stores its salted hash.
        var password = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        var salt = Convert.ToHexString(RandomNumberGenerator.GetBytes(8));
        var digest = SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(password + salt));
        Array.Reverse(digest); // Sunshine util::hex serializes this array in reverse order.
        var hash = Convert.ToHexString(digest);
        File.WriteAllText(FilePath("credentials.json"), JsonSerializer.Serialize(new { username = "bedmirror", password = hash, salt }, Json));
        File.WriteAllText(FilePath("apps.json"), """{"env":{},"apps":[{"name":"NyxLink Desktop","cmd":"","prep-cmd":[],"detached":[],"exclude-global-prep-cmd":true,"auto-detach":true}]}""");
        return password;
    }
}
