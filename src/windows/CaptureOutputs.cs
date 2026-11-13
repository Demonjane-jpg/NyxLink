using System.Runtime.InteropServices;

namespace BedMirror;

// Match Sunshine's Windows DXGI output order, including its duplication check.
internal static class CaptureOutputs
{
    const int NotFound = unchecked((int)0x887a0002);
    static readonly Guid Factory1 = new("770aae78-f26f-4dba-a829-253c83d1b387");
    static readonly Guid Output1 = new("00cddea8-939b-4b83-a340-a685226666cc");

    [DllImport("dxgi.dll")]
    static extern int CreateDXGIFactory1(in Guid iid, out IntPtr factory);

    [DllImport("d3d11.dll")]
    static extern int D3D11CreateDevice(IntPtr adapter, int driverType, IntPtr software, uint flags,
        IntPtr featureLevels, uint featureLevelCount, uint sdkVersion, out IntPtr device,
        IntPtr selectedFeatureLevel, IntPtr immediateContext);

    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    struct OutputDescription
    {
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)]
        public string DeviceName;
        public int Left, Top, Right, Bottom;
        [MarshalAs(UnmanagedType.Bool)]
        public bool AttachedToDesktop;
        public int Rotation;
        public IntPtr Monitor;
    }

    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int EnumerateItem(IntPtr owner, uint index, out IntPtr item);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int GetDescription(IntPtr output, out OutputDescription description);
    [UnmanagedFunctionPointer(CallingConvention.StdCall)]
    delegate int DuplicateOutput(IntPtr output, IntPtr device, out IntPtr duplication);

    static T Method<T>(IntPtr instance, int slot) where T : Delegate =>
        Marshal.GetDelegateForFunctionPointer<T>(Marshal.ReadIntPtr(Marshal.ReadIntPtr(instance), slot * IntPtr.Size));

    public static List<string> Enumerate()
    {
        Marshal.ThrowExceptionForHR(CreateDXGIFactory1(Factory1, out var factory));
        try
        {
            var result = new List<string>();
            // IDXGIFactory1::EnumAdapters1
            var enumAdapters = Method<EnumerateItem>(factory, 12);
            for (uint index = 0; ; index++)
            {
                var status = enumAdapters(factory, index, out var adapter);
                if (status == NotFound) return result;
                Marshal.ThrowExceptionForHR(status);
                try { AddOutputs(adapter, result); }
                finally { Marshal.Release(adapter); }
            }
        }
        finally { Marshal.Release(factory); }
    }

    static void AddOutputs(IntPtr adapter, List<string> result)
    {
        // IDXGIAdapter::EnumOutputs
        var enumOutputs = Method<EnumerateItem>(adapter, 7);
        for (uint index = 0; ; index++)
        {
            var status = enumOutputs(adapter, index, out var output);
            if (status == NotFound) return;
            Marshal.ThrowExceptionForHR(status);
            try
            {
                // IDXGIOutput::GetDesc
                Marshal.ThrowExceptionForHR(Method<GetDescription>(output, 7)(output, out var description));
                if (description.AttachedToDesktop && CanDuplicate(adapter, output)) result.Add(description.DeviceName);
            }
            finally { Marshal.Release(output); }
        }
    }

    static bool CanDuplicate(IntPtr adapter, IntPtr output)
    {
        const uint d3d11SdkVersion = 7;
        if (D3D11CreateDevice(adapter, 0, IntPtr.Zero, 0, IntPtr.Zero, 0, d3d11SdkVersion,
                out var device, IntPtr.Zero, IntPtr.Zero) < 0) return false;
        try
        {
            var iid = Output1;
            if (Marshal.QueryInterface(output, ref iid, out var output1) < 0) return false;
            try
            {
                // IDXGIOutput1::DuplicateOutput
                var status = Method<DuplicateOutput>(output1, 22)(output1, device, out var duplication);
                if (status < 0) return false;
                Marshal.Release(duplication);
                return true;
            }
            finally { Marshal.Release(output1); }
        }
        finally { Marshal.Release(device); }
    }
}
