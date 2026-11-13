using System.Text.Json;
using System.Text.Json.Serialization;

namespace BedMirror;

public sealed record ReportItem
{
    [JsonPropertyName("id")] public string Id { get; init; } = Guid.NewGuid().ToString("N");
    [JsonPropertyName("type")] public string Type { get; init; } = "feedback"; // "crash", "bug", "feedback", "feature"
    [JsonPropertyName("timestamp")] public DateTimeOffset Timestamp { get; init; } = DateTimeOffset.UtcNow;
    [JsonPropertyName("title")] public string Title { get; init; } = "";
    [JsonPropertyName("description")] public string Description { get; init; } = "";
    [JsonPropertyName("stepsToReproduce")] public string? StepsToReproduce { get; init; }
    [JsonPropertyName("utilityReason")] public string? UtilityReason { get; init; }
    [JsonPropertyName("rating")] public int? Rating { get; init; }
    [JsonPropertyName("contactEmail")] public string? ContactEmail { get; init; }
    [JsonPropertyName("appVersion")] public string AppVersion { get; init; } = "unknown";
    [JsonPropertyName("androidVersion")] public string AndroidVersion { get; init; } = "unknown";
    [JsonPropertyName("deviceModel")] public string DeviceModel { get; init; } = "unknown";
    [JsonPropertyName("diagnostics")] public string? Diagnostics { get; init; }
    [JsonPropertyName("stackTrace")] public string? StackTrace { get; init; }
    [JsonPropertyName("screenshotBase64")] public string? ScreenshotBase64 { get; init; }
    [JsonPropertyName("status")] public string Status { get; init; } = "new"; // "new", "reviewed", "in_progress", "resolved"
}

public sealed record ReportSummary
{
    [JsonPropertyName("totalReports")] public int TotalReports { get; init; }
    [JsonPropertyName("totalCrashes")] public int TotalCrashes { get; init; }
    [JsonPropertyName("totalBugs")] public int TotalBugs { get; init; }
    [JsonPropertyName("totalFeedback")] public int TotalFeedback { get; init; }
    [JsonPropertyName("totalFeatures")] public int TotalFeatures { get; init; }
    [JsonPropertyName("crashFrequencyByVersion")] public Dictionary<string, int> CrashFrequencyByVersion { get; init; } = new();
    [JsonPropertyName("latestTimestamp")] public DateTimeOffset? LatestTimestamp { get; init; }
}

public static class ReportStore
{
    static readonly object gate = new();
    static readonly string FilePath = HostConfig.FilePath("reports.json");

    static string Sanitize(string? text)
    {
        if (string.IsNullOrEmpty(text)) return "";
        // Strip out any potential token/password/private key patterns
        var sanitized = text;
        sanitized = System.Text.RegularExpressions.Regex.Replace(sanitized, @"(?i)(bearer\s+|token[=:]\s*|password[=:]\s*|secret[=:]\s*)[^\s,;&""]+", "$1[REDACTED]");
        sanitized = System.Text.RegularExpressions.Regex.Replace(sanitized, @"-----BEGIN [A-Z ]+PRIVATE KEY-----[^-]+-----END [A-Z ]+PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]");
        return sanitized;
    }

    public static ReportItem SanitizeReport(ReportItem item)
    {
        return item with
        {
            Title = Sanitize(item.Title),
            Description = Sanitize(item.Description),
            StepsToReproduce = Sanitize(item.StepsToReproduce),
            UtilityReason = Sanitize(item.UtilityReason),
            Diagnostics = Sanitize(item.Diagnostics),
            StackTrace = Sanitize(item.StackTrace),
            ContactEmail = string.IsNullOrWhiteSpace(item.ContactEmail) ? null : item.ContactEmail.Trim()
        };
    }

    public static List<ReportItem> GetAll()
    {
        lock (gate)
        {
            if (!File.Exists(FilePath)) return new List<ReportItem>();
            try
            {
                var json = File.ReadAllText(FilePath);
                var items = JsonSerializer.Deserialize<List<ReportItem>>(json, HostConfig.Json);
                return items?.OrderByDescending(r => r.Timestamp).ToList() ?? new List<ReportItem>();
            }
            catch
            {
                return new List<ReportItem>();
            }
        }
    }

    public static ReportItem Save(ReportItem rawItem)
    {
        var sanitized = SanitizeReport(rawItem);
        lock (gate)
        {
            var list = GetAll();
            list.RemoveAll(r => r.Id == sanitized.Id);
            list.Insert(0, sanitized);
            // Cap at 500 reports to bound disk usage
            if (list.Count > 500) list = list.Take(500).ToList();
            File.WriteAllText(FilePath, JsonSerializer.Serialize(list, HostConfig.Json));
        }
        return sanitized;
    }

    public static ReportSummary GetSummary()
    {
        var items = GetAll();
        var crashes = items.Where(i => i.Type.Equals("crash", StringComparison.OrdinalIgnoreCase)).ToList();
        var frequency = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        foreach (var c in crashes)
        {
            var ver = string.IsNullOrWhiteSpace(c.AppVersion) ? "unknown" : c.AppVersion;
            frequency[ver] = frequency.GetValueOrDefault(ver, 0) + 1;
        }

        return new ReportSummary
        {
            TotalReports = items.Count,
            TotalCrashes = crashes.Count,
            TotalBugs = items.Count(i => i.Type.Equals("bug", StringComparison.OrdinalIgnoreCase)),
            TotalFeedback = items.Count(i => i.Type.Equals("feedback", StringComparison.OrdinalIgnoreCase)),
            TotalFeatures = items.Count(i => i.Type.Equals("feature", StringComparison.OrdinalIgnoreCase)),
            CrashFrequencyByVersion = frequency,
            LatestTimestamp = items.Count > 0 ? items.Max(i => i.Timestamp) : null
        };
    }

    public static bool Delete(string id)
    {
        lock (gate)
        {
            var items = GetAll();
            var removed = items.RemoveAll(i => i.Id == id) > 0;
            if (removed)
            {
                File.WriteAllText(FilePath, JsonSerializer.Serialize(items, HostConfig.Json));
            }
            return removed;
        }
    }

    public static bool UpdateStatus(string id, string status)
    {
        lock (gate)
        {
            var items = GetAll();
            var index = items.FindIndex(i => i.Id == id);
            if (index < 0) return false;
            items[index] = items[index] with { Status = status };
            File.WriteAllText(FilePath, JsonSerializer.Serialize(items, HostConfig.Json));
            return true;
        }
    }

    public static void ClearAll()
    {
        lock (gate)
        {
            if (File.Exists(FilePath))
            {
                File.Delete(FilePath);
            }
        }
    }
}
