package dev.nyxlink.testdriver;
import android.app.*;import android.content.*;import android.os.*;import android.widget.*;
public final class ClipboardActivity extends Activity {
 private static ClipData previous;private static boolean saved;
 public void onCreate(Bundle b){super.onCreate(b);TextView label=new TextView(this);label.setText("NyxLink clipboard test fixture");setContentView(label);}
 public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused)return;
  ClipboardManager manager=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
  if(getIntent().getBooleanExtra("restore",false)){if(saved){if(previous==null)manager.clearPrimaryClip();else manager.setPrimaryClip(previous);saved=false;}finish();return;}
  if(!saved){previous=manager.getPrimaryClip();saved=true;}
  String value=getIntent().getStringExtra("text");String encoded=getIntent().getStringExtra("base64");if(encoded!=null)value=new String(android.util.Base64.decode(encoded,0),java.nio.charset.StandardCharsets.UTF_8);
  manager.setPrimaryClip(ClipData.newPlainText("NyxLink fixture",value));finish();
 }
}
