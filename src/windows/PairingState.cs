using System.Security.Cryptography.X509Certificates;
using System.Text.Json.Nodes;

namespace BedMirror;

public static class PairingState
{
    // Re-pairing can append the same certificate. Sunshine requires a single identity.
    // Conflicting enabled/disabled records remain untouched and fail closed.
    public static string? Normalize(string json)
    {
        var document = JsonNode.Parse(json)!;
        if (document["root"]?["named_devices"] is not JsonArray devices) return null;
        var groups = new Dictionary<string, List<JsonNode>>();
        foreach (var node in devices)
        {
            if (node == null) continue;
            using var certificate = X509Certificate2.CreateFromPem(node["cert"]!.GetValue<string>());
            var identity = Convert.ToBase64String(certificate.RawData);
            if (!groups.TryGetValue(identity, out var group)) groups[identity] = group = new();
            group.Add(node);
        }
        var changed = false;
        foreach (var group in groups.Values.Where(g => g.Count > 1))
        {
            bool Enabled(JsonNode node) => !string.Equals(node["enabled"]?.ToString(), "false", StringComparison.OrdinalIgnoreCase);
            if (group.Select(Enabled).Distinct().Count() != 1) continue;
            foreach (var duplicate in group.Take(group.Count - 1)) { devices.Remove(duplicate); changed = true; }
        }
        return changed ? document.ToJsonString(HostConfig.Json) : null;
    }

    public static void RepairFile(string path)
    {
        if (!File.Exists(path)) return;
        var original = File.ReadAllText(path);
        var normalized = Normalize(original);
        if (normalized == null) return;
        var temporary = path + ".normalize.tmp";
        File.WriteAllText(temporary, normalized);
        File.Replace(temporary, path, path + ".before-normalize.bak");
    }
}
