using System.Runtime.InteropServices;

namespace BedMirror;

public static class CursorInfo
{
    [StructLayout(LayoutKind.Sequential)] struct POINT { public int x,y; }
    [DllImport("user32.dll")] static extern bool GetPhysicalCursorPos(out POINT point);
    public static object Read(SunshineHost host)
    {
        var monitor=host.Monitors.FirstOrDefault(m=>m.Id==host.Selected);
        if(monitor==null||!GetPhysicalCursorPos(out var point))throw new InvalidOperationException("Cursor location unavailable.");
        double x=(double)(point.x-monitor.X)/monitor.Width,y=(double)(point.y-monitor.Y)/monitor.Height;
        return new { x,y,onMonitor=x>=0&&y>=0&&x<1&&y<1 };
    }
}
