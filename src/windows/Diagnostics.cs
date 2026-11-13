using System.Text.Json;

namespace BedMirror;

static class Diagnostics
{
    public static void Run()
    {
        Directory.CreateDirectory("artifacts");
        File.WriteAllText("artifacts/windows-diagnostics.json", JsonSerializer.Serialize(new {
            addresses = HostConfig.Addresses().Select(x => x.ToString()),
            screens = Screen.AllScreens.Select(s => new { s.DeviceName, s.Bounds, s.Primary }),
            runtime = HostConfig.Runtime,
            audio = AudioControl.Status()
        }, HostConfig.Json));
    }
}
