using System.Runtime.InteropServices;
using System.Text.Json;

static class InputTarget
{
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    static Form? form;
    static TextBox? text;
    public static void Open(string root)
    {
        if(form!=null){Focus();return;}
        var thread=new Thread(()=>{
            form=new Form { Text="BedMirror input integration test",Width=650,Height=260,StartPosition=FormStartPosition.CenterScreen };
            text=new TextBox { Multiline=true,Dock=DockStyle.Fill,Font=new Font("Segoe UI",16),AcceptsReturn=true,AcceptsTab=true };
            var mouseDowns=0;var mouseUps=0;var wheels=0;
            text.MouseDown+=(_,_)=>mouseDowns++;
            text.MouseUp+=(_,_)=>mouseUps++;
            text.MouseWheel+=(_,e)=>wheels+=e.Delta;
            form.Controls.Add(text);
            form.Controls.Add(new Label {Text="BedMirror test text goes here. This temporary window verifies remote typing.",Dock=DockStyle.Top,Height=40});
            void Save(){File.WriteAllText(Path.Combine(root,"artifacts","input-target-state.json"),JsonSerializer.Serialize(new { text=text.Text,foreground=GetForegroundWindow()==form.Handle,mouseDowns,mouseUps,wheels,selection=text.SelectionLength,buttons=Control.MouseButtons.ToString(),modifiers=Control.ModifierKeys.ToString(),cursor=new{x=Cursor.Position.X,y=Cursor.Position.Y} },new JsonSerializerOptions{WriteIndented=true}));}
            text.TextChanged+=(_,_)=>{File.AppendAllText(Path.Combine(root,"artifacts","input-target-events.jsonl"),JsonSerializer.Serialize(new {text=text.Text})+Environment.NewLine);Save();};
            var timer=new System.Windows.Forms.Timer { Interval=200 };timer.Tick+=(_,_)=>Save();
            form.Shown+=(_,_)=>{form.Activate();text.Focus();timer.Start();Save();};
            form.FormClosed+=(_,_)=>{timer.Dispose();form=null;text=null;};
            Application.Run(form);
        });
        thread.SetApartmentState(ApartmentState.STA);thread.IsBackground=true;thread.Start();
    }
    public static void Focus(){form?.BeginInvoke(()=>{form.Activate();text?.Focus();});}
    public static void Close(){form?.BeginInvoke(()=>form.Close());}
}
