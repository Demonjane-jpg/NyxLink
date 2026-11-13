using BedMirror;
using System.Text.Json.Nodes;
using System.Text.Json;

static class DeviceHost
{
    public static async Task Run(string root)
    {
        using var host = new SunshineHost();
        var audioBefore=JsonSerializer.Serialize(AudioControl.DeviceSnapshot());
        var screensBefore=JsonSerializer.Serialize(Screen.AllScreens.Select(s=>new{s.DeviceName,s.Bounds,s.Primary}));
        await using var api = new CompanionApi();
        await host.Start(HostConfig.Addresses().First().ToString());
        await api.Start(host);
        using var discovery = new LanDiscovery(host);
        discovery.PairingRequested += attempt => Console.WriteLine("DEVICE PAIRING CODE " + attempt.Code);
        Console.WriteLine("DEVICE HOST READY " + host.Address);
        File.WriteAllText(Path.Combine(root,"artifacts","device-host-ready.json"),JsonSerializer.Serialize(new { address=host.Address, monitors=host.Monitors },HostConfig.Json));
        var commandPath=Path.Combine(root,"artifacts","device-command.json");
        var finish=DateTimeOffset.UtcNow.AddHours(2);
        while(DateTimeOffset.UtcNow<finish)
        {
            if(File.Exists(commandPath))
            {
                var text=await File.ReadAllTextAsync(commandPath); File.Delete(commandPath);
                try
                {
                    var command=JsonNode.Parse(text)!;
                    switch(command["action"]!.ToString())
                    {
                        case "pair":
                            var pending=(await host.Get("/api/pin"))["pairings"]!.AsArray();
                            if(pending.Count!=1)throw new Exception("Expected one pending device");
                            await host.Post("/api/pin",new {pairing_id=pending[0]!["id"]!.ToString(),pin=command["pin"]!.ToString(),name="Samsung Galaxy A17"});
                            break;
                        case "pause":await host.Pause();break;
                        case "resume":await host.Resume();break;
                        case "switch":await host.Switch(command["id"]!.ToString());break;
                        case "input-target":InputTarget.Open(root);break;
                        case "focus-target":InputTarget.Focus();break;
                        case "restart":await host.Switch(host.Selected);break;
                        case "stop":
                            InputTarget.Close();await host.Stop();
                            var checks=new[]{new{name="Physical-device test preserves audio assignments",passed=audioBefore==JsonSerializer.Serialize(AudioControl.DeviceSnapshot())},new{name="Physical-device test preserves monitor topology",passed=screensBefore==JsonSerializer.Serialize(Screen.AllScreens.Select(s=>new{s.DeviceName,s.Bounds,s.Primary}))}};
                            File.WriteAllText(Path.Combine(root,"artifacts","device-host-tests.json"),JsonSerializer.Serialize(checks,HostConfig.Json));
                            return;
                        case "status":break;
                        default:throw new Exception("Unknown test command");
                    }
                    await File.WriteAllTextAsync(Path.Combine(root,"artifacts","device-command-result.json"),JsonSerializer.Serialize(new { success=true,running=host.Running,privacy=host.Privacy },HostConfig.Json));
                    Console.WriteLine("Device command completed");
                }
                catch(Exception e) { await File.WriteAllTextAsync(Path.Combine(root,"artifacts","device-command-result.json"),JsonSerializer.Serialize(new {success=false,error=e.Message},HostConfig.Json));Console.WriteLine(e.Message); }
            }
            await Task.Delay(200);
        }
    }
}
