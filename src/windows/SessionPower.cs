using System.Runtime.InteropServices;
using Microsoft.Win32.SafeHandles;

namespace BedMirror;

public sealed class SessionPower : IDisposable
{
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode, Size=32)] struct REASON_CONTEXT { public uint version, flags; [MarshalAs(UnmanagedType.LPWStr)] public string reason; }
    [DllImport("kernel32.dll", SetLastError = true)] static extern SafeFileHandle PowerCreateRequest(ref REASON_CONTEXT context);
    [DllImport("kernel32.dll", SetLastError = true)] [return: MarshalAs(UnmanagedType.Bool)] static extern bool PowerSetRequest(SafeFileHandle handle, int type);
    [DllImport("kernel32.dll", SetLastError = true)] [return: MarshalAs(UnmanagedType.Bool)] static extern bool PowerClearRequest(SafeFileHandle handle, int type);
    readonly object gate = new();
    readonly System.Threading.Timer expiry;
    SafeFileHandle? handle;
    DateTimeOffset lastHeartbeat;
    public bool Active { get { lock(gate) return handle is { IsInvalid: false, IsClosed: false }; } }
    public SessionPower() { expiry = new(_ => { lock(gate) { if (DateTimeOffset.UtcNow - lastHeartbeat > TimeSpan.FromSeconds(20)) Clear(); } }, null, 5000, 5000); }
    public void Update(bool active, bool preventSleep)
    {
        lock(gate)
        {
            lastHeartbeat = DateTimeOffset.UtcNow;
            if (!active || !preventSleep) { Clear(); return; }
            if (handle != null) return;
            var reason = new REASON_CONTEXT { flags = 1, reason = "NyxLink viewing session" };
            handle = PowerCreateRequest(ref reason);
            if (handle.IsInvalid || !PowerSetRequest(handle, 1)) { handle.Dispose(); handle = null; throw new InvalidOperationException("Windows could not create a temporary sleep-prevention request."); }
        }
    }
    void Clear() { if (handle == null) return; PowerClearRequest(handle, 1); handle.Dispose(); handle = null; }
    public void Dispose() { expiry.Dispose(); lock(gate) Clear(); }
}
