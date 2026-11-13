using System.Text.Json.Nodes;
using Microsoft.Win32;

namespace BedMirror;

public sealed class MainForm : Form
{
    readonly SunshineHost host = new();
    readonly CompanionApi api = new();
    readonly ComboBox addresses = new() { DropDownStyle = ComboBoxStyle.DropDownList, Width = 230 };
    readonly ListBox monitors = new() { Width = 610, Height = 90 };
    readonly ListBox clients = new() { Width = 610, Height = 75 };
    readonly Label status = new() { AutoSize = true, Text = "Server stopped" };
    LanDiscovery? discovery;
    Form? pairingWindow;
    readonly TextBox log = new() { Multiline = true, ReadOnly = true, ScrollBars = ScrollBars.Vertical, Width = 610, Height = 120 };
    readonly NotifyIcon tray;
    readonly System.Windows.Forms.Timer refresh = new() { Interval = 3000 };
    bool busy, exiting, closing, shutdownComplete;
    public MainForm()
    {
        using(var iconStream=typeof(MainForm).Assembly.GetManifestResourceStream("NyxLink.ico")) Icon=new Icon(iconStream!);
        Text = "NyxLink"; Size = new(670, 650); MinimumSize = Size;
        var root = new FlowLayoutPanel { Dock = DockStyle.Fill, FlowDirection = FlowDirection.TopDown, WrapContents = false, Padding = new(16), AutoScroll = true };
        Controls.Add(root);
        root.Controls.Add(new Label { Text = "NyxLink — existing monitors, local network", AutoSize = true, Font = new(Font.FontFamily, 13, FontStyle.Bold) });
        foreach (var address in HostConfig.Addresses()) addresses.Items.Add(address.ToString());
        if (addresses.Items.Count > 0) addresses.SelectedIndex = 0;
        if(File.Exists(HostConfig.FilePath("address.txt"))) { var previous=File.ReadAllText(HostConfig.FilePath("address.txt")).Trim();if(addresses.Items.Contains(previous))addresses.SelectedItem=previous; }
        root.Controls.Add(addresses);
        var buttons = Row();
        buttons.Controls.Add(Button("Start", StartServer));
        buttons.Controls.Add(Button("Stop / Disconnect", async () => { StopDiscovery(); await host.Stop(); await api.DisposeAsync(); status.Text = "Stopped. Press Start to allow connections."; }));
        buttons.Controls.Add(Button("Minimize to tray", () => { Hide(); return Task.CompletedTask; }));
        buttons.Controls.Add(Button("Developer Dashboard", () => { new DeveloperDashboardForm().Show(); return Task.CompletedTask; }));
        root.Controls.Add(buttons); root.Controls.Add(status);
        root.Controls.Add(new Label { Text = "Monitors (display settings are never changed)", AutoSize = true }); root.Controls.Add(monitors);
        root.Controls.Add(Button("Mirror selected monitor", async () => { if (monitors.SelectedItem is MonitorItem item) await host.Switch(item.Monitor.Id); }));
        root.Controls.Add(clients);
        root.Controls.Add(Button("Revoke selected device", async () => {
            if (clients.SelectedItem is ClientItem client) { await host.Post("/api/clients/unpair", new { uuid = client.Id }); await host.Pause(); await host.Resume(); await UpdateLists(); }
        }));
        var startup = new CheckBox { Text = "Start with Windows (current user)", AutoSize = true };
        using (var key = Registry.CurrentUser.OpenSubKey("Software\\Microsoft\\Windows\\CurrentVersion\\Run")) startup.Checked = key?.GetValue("NyxLink") != null || key?.GetValue("BedMirror") != null;
        startup.CheckedChanged += (_, _) => { using var key = Registry.CurrentUser.CreateSubKey("Software\\Microsoft\\Windows\\CurrentVersion\\Run"); key.DeleteValue("BedMirror", false); if (startup.Checked) key.SetValue("NyxLink", "\"" + Environment.ProcessPath + "\" --tray"); else key.DeleteValue("NyxLink", false); };
        root.Controls.Add(startup);
        root.Controls.Add(new Label { Text = "Open NyxLink on your phone and select this PC. Allow NyxLink on private networks if Windows asks.", AutoSize = true, MaximumSize = new Size(600, 0) });
        root.Controls.Add(log);
        tray = new NotifyIcon { Icon = Icon, Text = "NyxLink", Visible = true, ContextMenuStrip = new() };
        tray.ContextMenuStrip.Items.Add("Open NyxLink", null, (_, _) => { Show(); WindowState = FormWindowState.Normal; Activate(); });
        tray.ContextMenuStrip.Items.Add("Developer Dashboard", null, (_, _) => { new DeveloperDashboardForm().Show(); });
        tray.ContextMenuStrip.Items.Add("Exit", null, (_, _) => { exiting = true; Close(); });
        tray.DoubleClick += (_, _) => { Show(); WindowState = FormWindowState.Normal; Activate(); };
        host.Message += line => { if (!IsDisposed && IsHandleCreated) BeginInvoke(() => { log.AppendText(line + Environment.NewLine); if (log.TextLength > 16000) log.Text = log.Text[^10000..]; }); };
        refresh.Tick += async (_, _) =>
        {
            if (busy) return;
            if (!host.Running && !host.Privacy && host.AllowReconnect && discovery != null)
            {
                busy = true;
                try { await StartServer(); }
                catch (Exception e) { status.Text = e.Message; }
                finally { busy = false; }
            }
            else if (host.Running)
            {
                try { await UpdateLists(); } catch { }
            }
        };
        refresh.Start();
        Shown += async (_, _) =>
        {
            if (Environment.GetCommandLineArgs().Contains("--tray")) Hide();
            busy = true;
            try { await StartServer(); }
            catch (Exception e) { status.Text = e.Message; }
            finally { busy = false; }
        };
        FormClosing += async (_, e) => {
            if (!exiting && e.CloseReason == CloseReason.UserClosing) { e.Cancel = true; Hide(); return; }
            if(shutdownComplete)return;
            e.Cancel=true;if(closing)return;closing=true;refresh.Stop();Enabled=false;status.Text="Stopping NyxLink…";
            try { StopDiscovery(); await host.Stop();await api.DisposeAsync(); }
            finally { host.Dispose();tray.Dispose();shutdownComplete=true;Close(); }
        };
        FormClosed += (_, _) => refresh.Dispose();
    }
    static FlowLayoutPanel Row() => new() { AutoSize = true, WrapContents = false };
    async Task StartServer()
    {
        if (discovery != null && host.Running) return;
        if (discovery != null)
        {
            StopDiscovery();
            await api.DisposeAsync();
            await host.Stop();
        }
        status.Text = "Starting NyxLink…";
        await host.Start(addresses.Text);
        try
        {
            await api.Start(host);
            var listener = new LanDiscovery(host);
            discovery = listener;
            listener.PairingRequested += attempt => BeginInvoke(() =>
            {
                if (discovery == listener && !attempt.Cancellation.IsCancellationRequested) ShowPairingCode(attempt);
            });
            listener.PairingFinished += (_, success) =>
            {
                if (!IsDisposed && IsHandleCreated) BeginInvoke(() =>
                {
                    if (discovery != listener) return;
                    pairingWindow?.Close();
                    status.Text = success ? "Phone paired" : "Pairing ended. Select this PC on your phone to try again.";
                });
            };
            File.WriteAllText(HostConfig.FilePath("address.txt"), host.Address);
            await UpdateLists();
        }
        catch { StopDiscovery(); await host.Stop(); await api.DisposeAsync(); throw; }
    }

    void ShowPairingCode(LanDiscovery.PairingAttempt attempt)
    {
        pairingWindow?.Close();
        var window = new Form
        {
            Text = "Pair a phone with NyxLink",
            AutoScaleMode = AutoScaleMode.Dpi,
            AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink,
            StartPosition = FormStartPosition.CenterScreen, FormBorderStyle = FormBorderStyle.FixedDialog,
            MaximizeBox = false, MinimizeBox = false, TopMost = true
        };
        var content = new TableLayoutPanel { AutoSize = true, AutoSizeMode = AutoSizeMode.GrowAndShrink, ColumnCount = 1, Padding = new Padding(20), Dock = DockStyle.Fill };
        var instructions = new Label { Text = "Enter this code in NyxLink on your phone.", AutoSize = true, Anchor = AnchorStyles.None };
        var codeFont = new Font(Font.FontFamily, 36, FontStyle.Bold);
        var code = new Label { Text = attempt.Code, AutoSize = true, Anchor = AnchorStyles.None, Margin = new Padding(0, 12, 0, 16), Font = codeFont };
        var cancel = new Button { Text = "Stop pairing", AutoSize = true, Anchor = AnchorStyles.None };
        cancel.Click += (_, _) => window.Close();
        content.Controls.Add(instructions, 0, 0);
        content.Controls.Add(code, 0, 1);
        content.Controls.Add(cancel, 0, 2);
        window.Controls.Add(content);
        window.FormClosed += (_, _) =>
        {
            attempt.Cancellation.Cancel();
            if (pairingWindow == window) pairingWindow = null;
        };
        window.Disposed += (_, _) => codeFont.Dispose();
        pairingWindow = window;
        window.Show();
        window.Activate();
    }

    void StopDiscovery()
    {
        var listener = discovery;
        discovery = null;
        listener?.Dispose();
        pairingWindow?.Close();
    }
    Button Button(string text, Func<Task> action)
    {
        var button = new Button { Text = text, AutoSize = true, Height = 34 };
        button.Click += async (_, _) => { if (busy) return; busy = true; button.Enabled = false; try { await action(); } catch (Exception e) { status.Text = e.Message; } finally { busy = false; button.Enabled = true; } };
        return button;
    }
    async Task UpdateLists()
    {
        var previous = (monitors.SelectedItem as MonitorItem)?.Monitor.Id ?? host.Selected;
        monitors.Items.Clear();
        foreach (var monitor in host.Monitors) { var idx = monitors.Items.Add(new MonitorItem(monitor)); if (monitor.Id == previous) monitors.SelectedIndex = idx; }
        var list = (await host.Get("/api/clients/list"))["named_certs"]!.AsArray();
        var selected = (clients.SelectedItem as ClientItem)?.Id;
        clients.Items.Clear();
        foreach (var entry in list) { var item = new ClientItem(entry!["uuid"]!.ToString(), entry["name"]!.ToString()); var idx = clients.Items.Add(item); if (item.Id == selected) clients.SelectedIndex = idx; }
        status.Text = (host.ConnectedDevice.Length > 0 ? "Streaming · " + host.ConnectedDevice : "Ready")
            + " · " + Environment.MachineName + " · paired devices: " + clients.Items.Count;
    }
    sealed record MonitorItem(MonitorInfo Monitor) { public override string ToString() => $"{Monitor.Display} · {Monitor.Name} · {Monitor.Width} × {Monitor.Height}"; }
    sealed record ClientItem(string Id, string Name) { public override string ToString() => Name; }
}
