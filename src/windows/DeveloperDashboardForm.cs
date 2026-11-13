using System.Text.Json;

namespace BedMirror;

public sealed class DeveloperDashboardForm : Form
{
    readonly ListBox reportList = new() { Width = 320, Dock = DockStyle.Left, IntegralHeight = false };
    readonly TextBox detailsText = new() { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Both, Dock = DockStyle.Fill, Font = new Font("Consolas", 9.5f) };
    readonly PictureBox screenshotBox = new() { SizeMode = PictureBoxSizeMode.Zoom, Height = 180, Dock = DockStyle.Bottom, Visible = false, BorderStyle = BorderStyle.FixedSingle };
    readonly Label summaryLabel = new() { AutoSize = false, Height = 45, Dock = DockStyle.Top, Font = new Font(FontFamily.GenericSansSerif, 9.5f, FontStyle.Bold), BackColor = Color.FromArgb(240, 244, 250), Padding = new Padding(8, 6, 8, 4) };
    readonly ComboBox filterBox = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 160 };
    readonly FlowLayoutPanel statusPanel = new() { Dock = DockStyle.Top, Height = 34, Padding = new Padding(6, 4, 6, 4), Visible = false };
    readonly ComboBox statusBox = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 140 };
    bool updatingStatus;
    List<ReportItem> allReports = new();

    public DeveloperDashboardForm()
    {
        Text = "NyxLink — Developer Dashboard (Crash Reports, Bug Reports & Feedback)";
        Size = new Size(920, 680);
        MinimumSize = new Size(800, 520);
        StartPosition = FormStartPosition.CenterScreen;

        var topPanel = new FlowLayoutPanel { Dock = DockStyle.Top, Height = 40, Padding = new Padding(6), FlowDirection = FlowDirection.LeftToRight };
        topPanel.Controls.Add(new Label { Text = "Filter:", AutoSize = true, Margin = new Padding(0, 6, 4, 0) });
        filterBox.Items.AddRange(new object[] { "All Reports", "Crashes", "Bug Reports", "Feedback", "Feature Requests" });
        filterBox.SelectedIndex = 0;
        filterBox.SelectedIndexChanged += (_, _) => ApplyFilter();
        topPanel.Controls.Add(filterBox);

        var refreshBtn = new Button { Text = "⟳ Refresh", AutoSize = true, Height = 28 };
        refreshBtn.Click += (_, _) => LoadReports();
        topPanel.Controls.Add(refreshBtn);

        var exportBtn = new Button { Text = "Export JSON…", AutoSize = true, Height = 28 };
        exportBtn.Click += (_, _) => ExportJson();
        topPanel.Controls.Add(exportBtn);

        var deleteBtn = new Button { Text = "Delete Selected", AutoSize = true, Height = 28 };
        deleteBtn.Click += (_, _) => DeleteSelected();
        topPanel.Controls.Add(deleteBtn);

        var clearBtn = new Button { Text = "Clear All", AutoSize = true, Height = 28 };
        clearBtn.Click += (_, _) => ClearAllReports();
        topPanel.Controls.Add(clearBtn);

        Controls.Add(topPanel);
        Controls.Add(summaryLabel);

        var contentSplit = new SplitContainer { Dock = DockStyle.Fill, Orientation = Orientation.Vertical, SplitterDistance = 340 };
        contentSplit.Panel1.Controls.Add(reportList);

        statusPanel.Controls.Add(new Label { Text = "Status:", AutoSize = true, Margin = new Padding(0, 4, 4, 0) });
        statusBox.Items.AddRange(new object[] { "new", "reviewed", "in_progress", "resolved" });
        statusBox.SelectedIndexChanged += (_, _) => {
            if (updatingStatus) return;
            if (reportList.SelectedItem is ReportListItem selected && statusBox.SelectedItem is string newStatus)
            {
                if (selected.Item.Status != newStatus)
                {
                    ReportStore.UpdateStatus(selected.Item.Id, newStatus);
                    LoadReports();
                }
            }
        };
        statusPanel.Controls.Add(statusBox);

        var rightPanel = new Panel { Dock = DockStyle.Fill };
        rightPanel.Controls.Add(detailsText);
        rightPanel.Controls.Add(statusPanel);
        rightPanel.Controls.Add(screenshotBox);
        contentSplit.Panel2.Controls.Add(rightPanel);

        Controls.Add(contentSplit);

        screenshotBox.Cursor = Cursors.Hand;
        screenshotBox.Click += (_, _) =>
        {
            if (screenshotBox.Image is Image img)
            {
                var viewForm = new Form { Text = "Screenshot — Full Resolution", Size = new Size(Math.Min(1200, img.Width + 40), Math.Min(900, img.Height + 60)), StartPosition = FormStartPosition.CenterParent };
                var pbox = new PictureBox { Image = img, Dock = DockStyle.Fill, SizeMode = PictureBoxSizeMode.Zoom };
                viewForm.Controls.Add(pbox);
                viewForm.ShowDialog(this);
            }
        };

        reportList.SelectedIndexChanged += (_, _) => ShowSelectedReport();

        LoadReports();
    }

    void LoadReports()
    {
        allReports = ReportStore.GetAll();
        var summary = ReportStore.GetSummary();

        var crashFreqStr = summary.CrashFrequencyByVersion.Count > 0
            ? string.Join(", ", summary.CrashFrequencyByVersion.Select(kv => $"{kv.Key}: {kv.Value}"))
            : "none";

        summaryLabel.Text = $"Total Reports: {summary.TotalReports}  |  Crashes: {summary.TotalCrashes} ({crashFreqStr})\nBugs: {summary.TotalBugs}  |  Feedback: {summary.TotalFeedback}  |  Features: {summary.TotalFeatures}  |  Last: {(summary.LatestTimestamp.HasValue ? summary.LatestTimestamp.Value.ToLocalTime().ToString("yyyy-MM-dd HH:mm:ss") : "none")}";

        ApplyFilter();
    }

    void ApplyFilter()
    {
        var filter = filterBox.SelectedItem?.ToString() ?? "All Reports";
        var filtered = filter switch
        {
            "Crashes" => allReports.Where(r => r.Type.Equals("crash", StringComparison.OrdinalIgnoreCase)).ToList(),
            "Bug Reports" => allReports.Where(r => r.Type.Equals("bug", StringComparison.OrdinalIgnoreCase)).ToList(),
            "Feedback" => allReports.Where(r => r.Type.Equals("feedback", StringComparison.OrdinalIgnoreCase)).ToList(),
            "Feature Requests" => allReports.Where(r => r.Type.Equals("feature", StringComparison.OrdinalIgnoreCase)).ToList(),
            _ => allReports
        };

        var prevSelectedId = (reportList.SelectedItem as ReportListItem)?.Item.Id;

        reportList.BeginUpdate();
        reportList.Items.Clear();
        int newIdx = -1;
        for (int i = 0; i < filtered.Count; i++)
        {
            var item = filtered[i];
            reportList.Items.Add(new ReportListItem(item));
            if (item.Id == prevSelectedId) newIdx = i;
        }
        reportList.EndUpdate();

        if (newIdx >= 0) reportList.SelectedIndex = newIdx;
        else if (reportList.Items.Count > 0) reportList.SelectedIndex = 0;
        else
        {
            detailsText.Text = "No reports found for this filter.\n\nReports submitted by opted-in Android users will appear here.";
            screenshotBox.Visible = false;
            statusPanel.Visible = false;
        }
    }

    void ShowSelectedReport()
    {
        if (reportList.SelectedItem is not ReportListItem selected)
        {
            detailsText.Text = "";
            screenshotBox.Visible = false;
            statusPanel.Visible = false;
            return;
        }

        var r = selected.Item;
        updatingStatus = true;
        try
        {
            statusPanel.Visible = true;
            statusBox.SelectedItem = r.Status;
        }
        finally
        {
            updatingStatus = false;
        }

        var sb = new System.Text.StringBuilder();
        sb.AppendLine($"================================================================================");
        sb.AppendLine($"REPORT ID:    {r.Id}");
        sb.AppendLine($"TYPE:         {r.Type.ToUpperInvariant()}");
        sb.AppendLine($"STATUS:       {r.Status.ToUpperInvariant()}");
        sb.AppendLine($"TIMESTAMP:    {r.Timestamp.ToLocalTime():yyyy-MM-dd HH:mm:ss} ({r.Timestamp:o})");
        sb.AppendLine($"APP VERSION:  {r.AppVersion}");
        sb.AppendLine($"DEVICE:       {r.DeviceModel} (Android {r.AndroidVersion})");
        if (!string.IsNullOrWhiteSpace(r.ContactEmail))
            sb.AppendLine($"CONTACT:      {r.ContactEmail}");
        if (r.Rating.HasValue)
            sb.AppendLine($"RATING:       {new string('★', Math.Clamp(r.Rating.Value, 1, 5))} ({r.Rating.Value}/5)");
        sb.AppendLine($"TITLE:        {r.Title}");
        sb.AppendLine($"================================================================================");
        sb.AppendLine();

        sb.AppendLine("--- DESCRIPTION / COMMENTS ---");
        sb.AppendLine(string.IsNullOrWhiteSpace(r.Description) ? "(None provided)" : r.Description);
        sb.AppendLine();

        if (!string.IsNullOrWhiteSpace(r.StepsToReproduce))
        {
            sb.AppendLine("--- STEPS TO REPRODUCE ---");
            sb.AppendLine(r.StepsToReproduce);
            sb.AppendLine();
        }

        if (!string.IsNullOrWhiteSpace(r.UtilityReason))
        {
            sb.AppendLine("--- UTILITY / WHY USEFUL ---");
            sb.AppendLine(r.UtilityReason);
            sb.AppendLine();
        }

        if (!string.IsNullOrWhiteSpace(r.Diagnostics))
        {
            sb.AppendLine("--- TECHNICAL DIAGNOSTICS ---");
            sb.AppendLine(r.Diagnostics);
            sb.AppendLine();
        }

        if (!string.IsNullOrWhiteSpace(r.StackTrace))
        {
            sb.AppendLine("--- EXCEPTION STACK TRACE ---");
            sb.AppendLine(r.StackTrace);
            sb.AppendLine();
        }

        detailsText.Text = sb.ToString();

        if (!string.IsNullOrWhiteSpace(r.ScreenshotBase64))
        {
            try
            {
                var bytes = Convert.FromBase64String(r.ScreenshotBase64);
                using var ms = new MemoryStream(bytes);
                var bmp = new Bitmap(ms);
                screenshotBox.Image?.Dispose();
                screenshotBox.Image = bmp;
                screenshotBox.Visible = true;
            }
            catch
            {
                screenshotBox.Visible = false;
            }
        }
        else
        {
            screenshotBox.Visible = false;
        }
    }

    void ExportJson()
    {
        using var sfd = new SaveFileDialog { Filter = "JSON Files (*.json)|*.json", FileName = $"nyxlink-reports-{DateTime.Now:yyyyMMdd-HHmmss}.json" };
        if (sfd.ShowDialog() == DialogResult.OK)
        {
            File.WriteAllText(sfd.FileName, JsonSerializer.Serialize(allReports, HostConfig.Json));
            MessageBox.Show(this, $"Exported {allReports.Count} reports to:\n{sfd.FileName}", "Export Complete", MessageBoxButtons.OK, MessageBoxIcon.Information);
        }
    }

    void DeleteSelected()
    {
        if (reportList.SelectedItem is not ReportListItem selected) return;
        if (MessageBox.Show(this, $"Delete report '{selected.Item.Title}'?", "Confirm Delete", MessageBoxButtons.YesNo, MessageBoxIcon.Question) == DialogResult.Yes)
        {
            ReportStore.Delete(selected.Item.Id);
            LoadReports();
        }
    }

    void ClearAllReports()
    {
        if (allReports.Count == 0) return;
        if (MessageBox.Show(this, "Delete ALL submitted reports permanently?", "Confirm Clear All", MessageBoxButtons.YesNo, MessageBoxIcon.Warning) == DialogResult.Yes)
        {
            ReportStore.ClearAll();
            LoadReports();
        }
    }

    sealed record ReportListItem(ReportItem Item)
    {
        public override string ToString()
        {
            var badge = Item.Type switch
            {
                "crash" => "[CRASH]",
                "bug" => "[BUG]",
                "feature" => "[FEATURE]",
                _ => "[FEEDBACK]"
            };
            var statusBadge = Item.Status switch
            {
                "reviewed" => " [REVIEWED]",
                "in_progress" => " [IN PROGRESS]",
                "resolved" => " [RESOLVED]",
                _ => ""
            };
            var time = Item.Timestamp.ToLocalTime().ToString("MM-dd HH:mm");
            var title = string.IsNullOrWhiteSpace(Item.Title) ? Item.Description : Item.Title;
            if (title.Length > 24) title = title[..21] + "…";
            return $"{badge}{statusBadge} {time} {title} (v{Item.AppVersion})";
        }
    }
}
