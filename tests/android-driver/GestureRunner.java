package dev.nyxlink.testdriver;
import android.app.*;
import android.os.*;
import android.view.*;
import org.json.*;

/** On-device touch injection for physical end-to-end tests. Does not modify NyxLink. */
public final class GestureRunner extends Instrumentation {
 private Bundle arguments;
 public void onCreate(Bundle b){super.onCreate(b);arguments=b;start();}
 public void onStart(){Bundle result=new Bundle();try{
  if(arguments.containsKey("setTextBase64")){
   android.accessibilityservice.AccessibilityServiceInfo service=getUiAutomation().getServiceInfo();service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(service);
   android.view.accessibility.AccessibilityNodeInfo focused=null;
   for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo root=window.getRoot();if(root!=null&&"dev.bedmirror.android".contentEquals(root.getPackageName())){focused=root.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT);if(focused!=null&&focused.isEditable())break;}}
   if(focused==null||!"dev.bedmirror.android".contentEquals(focused.getPackageName())||!focused.isEditable())throw new Exception("Expected a focused NyxLink test editor");
   Bundle value=new Bundle();value.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,new String(android.util.Base64.decode(arguments.getString("setTextBase64"),0),java.nio.charset.StandardCharsets.UTF_8));
   if(!focused.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,value))throw new Exception("Editor refused test text");
   result.putString("result","fixture text set");finish(Activity.RESULT_OK,result);return;
  }
  JSONArray frames=new JSONArray(arguments.getString("frames"));
  int count=frames.getJSONArray(0).length()/2;
  long down=SystemClock.uptimeMillis();
  int duration=Integer.parseInt(arguments.getString("duration","700"));
  send(frames.getJSONArray(0),1,MotionEvent.ACTION_DOWN,down);
  for(int i=1;i<count;i++)send(frames.getJSONArray(0),i+1,MotionEvent.ACTION_POINTER_DOWN|(i<<8),down);
  SystemClock.sleep(Integer.parseInt(arguments.getString("hold","0")));
  int segments=frames.length()-1;
  int steps=Math.max(segments,duration/16);
  for(int i=1;i<=steps;i++){
   float position=(float)i/steps*segments;int a=Math.min(segments-1,(int)position);float t=position-a;
   JSONArray start=frames.getJSONArray(a),end=frames.getJSONArray(a+1),p=new JSONArray();
   for(int j=0;j<count*2;j++)p.put(start.getDouble(j)+(end.getDouble(j)-start.getDouble(j))*t);
   SystemClock.sleep(Math.max(1,duration/steps));send(p,count,MotionEvent.ACTION_MOVE,down);
  }
  for(int i=count-1;i>0;i--)send(frames.getJSONArray(segments),i+1,MotionEvent.ACTION_POINTER_UP|(i<<8),down);
  send(frames.getJSONArray(segments),1,MotionEvent.ACTION_UP,down);
  result.putString("result","gesture injected");finish(Activity.RESULT_OK,result);
 }catch(Exception e){result.putString("error",e.toString());finish(Activity.RESULT_CANCELED,result);}}
 private void send(JSONArray xy,int count,int action,long down)throws Exception{
  MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[count];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
  for(int i=0;i<count;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=(float)xy.getDouble(i*2);coords[i].y=(float)xy.getDouble(i*2+1);coords[i].pressure=1;coords[i].size=1;}
  MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,count,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
  try{if(!getUiAutomation().injectInputEvent(e,true))throw new Exception("Touch injection refused");}finally{e.recycle();}
 }
}
