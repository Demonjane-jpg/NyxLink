namespace BedMirror;

internal static class Program
{
    [STAThread]
    static void Main(string[] args)
    {
        ApplicationConfiguration.Initialize();
        if (args.Contains("--diagnostics")) { Diagnostics.Run(); return; }
        using var single = new Mutex(true, "Local\\BedMirror-Companion", out var created);
        if (!created) { MessageBox.Show("NyxLink is already running in the system tray."); return; }
        Application.Run(new MainForm());
    }
}
