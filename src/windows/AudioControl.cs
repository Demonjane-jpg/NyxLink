using System.Runtime.InteropServices;

namespace BedMirror;

public static class AudioControl
{
    [DllImport("user32.dll", SetLastError = true)] static extern uint SendInput(uint count, INPUT[] inputs, int size);
    [DllImport("user32.dll")] static extern bool LockWorkStation();
    [StructLayout(LayoutKind.Sequential)] struct INPUT { public uint type; public INPUTUNION data; }
    [StructLayout(LayoutKind.Explicit)] struct INPUTUNION { [FieldOffset(0)] public KEYBDINPUT key; [FieldOffset(0)] public MOUSEINPUT mouse; }
    [StructLayout(LayoutKind.Sequential)] struct KEYBDINPUT { public ushort vk, scan; public uint flags, time; public UIntPtr extra; }
    [StructLayout(LayoutKind.Sequential)] struct MOUSEINPUT { public int x, y; public uint data, flags, time; public UIntPtr extra; }
    public static void Key(ushort vk)
    {
        INPUT Make(uint flags) => new() { type = 1, data = new() { key = new() { vk = vk, flags = flags } } };
        var events = new[] { Make(0), Make(2) };
        if (SendInput(2, events, Marshal.SizeOf<INPUT>()) != 2) throw new InvalidOperationException("Windows blocked the command (possibly an elevated or secure desktop).");
    }
    public static void Command(string action)
    {
        switch (action)
        {
            case "playPause": Key(0xB3); break;
            case "next": Key(0xB0); break;
            case "previous": Key(0xB1); break;
            case "volumeUp": Key(0xAF); break;
            case "volumeDown": Key(0xAE); break;
            case "speakerMute": ToggleMute(false); break;
            case "microphoneMute": ToggleMute(true); break;
            case "lock": if (!LockWorkStation()) throw new InvalidOperationException("Windows could not lock."); break;
            default: throw new ArgumentException("Unknown media command.");
        }
    }
    static IAudioEndpointVolume Endpoint(bool capture)
    {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumerator();
        try
        {
            Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(capture ? 1 : 0, capture ? 2 : 0, out var device));
            try
            {
                var iid = typeof(IAudioEndpointVolume).GUID;
                Marshal.ThrowExceptionForHR(device.Activate(ref iid, 23, IntPtr.Zero, out var result));
                return (IAudioEndpointVolume)result;
            }
            finally { Marshal.ReleaseComObject(device); }
        }
        finally { Marshal.ReleaseComObject(enumerator); }
    }
    public static bool HasPlaybackDevice() { try { var endpoint = Endpoint(false); Marshal.ReleaseComObject(endpoint); return true; } catch { return false; } }
    public static void ToggleMute(bool capture)
    {
        var endpoint = Endpoint(capture);
        try { Marshal.ThrowExceptionForHR(endpoint.GetMute(out var mute)); var context = Guid.Empty; Marshal.ThrowExceptionForHR(endpoint.SetMute(!mute, ref context)); }
        finally { Marshal.ReleaseComObject(endpoint); }
    }
    public static object Status()
    {
        bool? Mute(bool input) { try { var v = Endpoint(input); try { Marshal.ThrowExceptionForHR(v.GetMute(out var mute)); return mute; } finally { Marshal.ReleaseComObject(v); } } catch { return null; } }
        return new { speakerMuted = Mute(false), microphoneMuted = Mute(true), microphoneTarget = "Windows default communications capture endpoint" };
    }
    public static Dictionary<string,string?> DeviceSnapshot()
    {
        var result = new Dictionary<string,string?>();
        var enumerator=(IMMDeviceEnumerator)new MMDeviceEnumerator();
        try { for(var flow=0;flow<2;flow++)for(var role=0;role<3;role++) { IMMDevice? device=null; try { Marshal.ThrowExceptionForHR(enumerator.GetDefaultAudioEndpoint(flow,role,out device)); Marshal.ThrowExceptionForHR(device.GetId(out var id)); result[$"{flow}/{role}"]=id; } catch { result[$"{flow}/{role}"]=null; } finally { if(device!=null)Marshal.ReleaseComObject(device); } } }
        finally { Marshal.ReleaseComObject(enumerator); }
        return result;
    }
    public static bool DefaultPlaybackIsSteam()
    {
        var enumerator=(IMMDeviceEnumerator)new MMDeviceEnumerator();
        try
        {
            if(enumerator.GetDefaultAudioEndpoint(0,0,out var device)!=0)return false;
            try
            {
                Marshal.ThrowExceptionForHR(device.OpenPropertyStore(0,out var properties));
                try
                {
                    var key=new PROPERTYKEY { id=new Guid("b3f8fa53-0004-438e-9003-51a46e139bfc"), pid=2 };
                    Marshal.ThrowExceptionForHR(properties.GetValue(ref key,out var value));
                    try { return value.type==31 && (Marshal.PtrToStringUni(value.pointer)?.Contains("Steam Streaming Speakers",StringComparison.OrdinalIgnoreCase)??false); }
                    finally { PropVariantClear(ref value); }
                }
                finally { Marshal.ReleaseComObject(properties); }
            }
            finally { Marshal.ReleaseComObject(device); }
        }
        finally { Marshal.ReleaseComObject(enumerator); }
    }
    [StructLayout(LayoutKind.Sequential)] struct PROPERTYKEY { public Guid id; public uint pid; }
    [StructLayout(LayoutKind.Explicit, Size=24)] struct PROPVARIANT { [FieldOffset(0)] public ushort type; [FieldOffset(8)] public IntPtr pointer; }
    [DllImport("ole32.dll")] static extern int PropVariantClear(ref PROPVARIANT value);
    [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IPropertyStore
    {
        [PreserveSig] int GetCount(out uint count);
        [PreserveSig] int GetAt(uint index,out PROPERTYKEY key);
        [PreserveSig] int GetValue(ref PROPERTYKEY key,out PROPVARIANT value);
    }
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")] class MMDeviceEnumerator { }
    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IMMDeviceEnumerator
    {
        [PreserveSig] int EnumAudioEndpoints(int flow, uint mask, out IntPtr devices);
        [PreserveSig] int GetDefaultAudioEndpoint(int flow, int role, out IMMDevice device);
    }
    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IMMDevice
    {
        [PreserveSig] int Activate(ref Guid id, uint context, IntPtr parameters, [MarshalAs(UnmanagedType.IUnknown)] out object result);
        [PreserveSig] int OpenPropertyStore(uint mode,out IPropertyStore properties);
        [PreserveSig] int GetId([MarshalAs(UnmanagedType.LPWStr)] out string id);
    }
    [ComImport, Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IAudioEndpointVolume
    {
        [PreserveSig] int RegisterControlChangeNotify(IntPtr callback);
        [PreserveSig] int UnregisterControlChangeNotify(IntPtr callback);
        [PreserveSig] int GetChannelCount(out uint count);
        [PreserveSig] int SetMasterVolumeLevel(float level, ref Guid context);
        [PreserveSig] int SetMasterVolumeLevelScalar(float level, ref Guid context);
        [PreserveSig] int GetMasterVolumeLevel(out float level);
        [PreserveSig] int GetMasterVolumeLevelScalar(out float level);
        [PreserveSig] int SetChannelVolumeLevel(uint channel, float level, ref Guid context);
        [PreserveSig] int SetChannelVolumeLevelScalar(uint channel, float level, ref Guid context);
        [PreserveSig] int GetChannelVolumeLevel(uint channel, out float level);
        [PreserveSig] int GetChannelVolumeLevelScalar(uint channel, out float level);
        [PreserveSig] int SetMute([MarshalAs(UnmanagedType.Bool)] bool mute, ref Guid context);
        [PreserveSig] int GetMute([MarshalAs(UnmanagedType.Bool)] out bool mute);
    }
}
