package com.limelight.bedmirror;

import android.app.Activity;
import android.content.Intent;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Physical-device test of the real InputConnection -> encrypted protocol -> Windows input path. */
@RunWith(AndroidJUnit4.class)
public final class DeviceInputTest {
    private final android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private Activity active;
    private RemoteEditText editor;
    private InputConnection input;
    private void gate(String name) {
        java.io.File gate=new java.io.File(instrumentation.getTargetContext().getFilesDir(),"bedmirror-test-continue");gate.delete();
        android.os.Bundle status=new android.os.Bundle();status.putString("bedmirrorGate",name);instrumentation.sendStatus(2,status);
        long until=SystemClock.elapsedRealtime()+180000;
        while(!gate.exists()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(50);
        assertTrue("Focused Windows test target is required",gate.exists());gate.delete();
    }
    private void main(Runnable r){instrumentation.runOnMainSync(()->{if(editor!=null){try{java.lang.reflect.Field field=RemoteEditText.class.getDeclaredField("connection");field.setAccessible(true);input=(InputConnection)field.get(editor);}catch(Exception e){throw new AssertionError(e);}}r.run();});}
    private View find(View view,String text){if(view instanceof TextView&&(((TextView)view).getText().toString().equalsIgnoreCase(text)||((TextView)view).getText().toString().equalsIgnoreCase(text+" ●")))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=find(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private RemoteEditText edit(View view){if(view instanceof RemoteEditText)return(RemoteEditText)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){RemoteEditText found=edit(group.getChildAt(i));if(found!=null)return found;}}return null;}
    private void click(String label){main(()->{View view=find(active.getWindow().getDecorView(),label);assertNotNull(label+" must be accessible",view);view.performClick();});instrumentation.waitForIdleSync();SystemClock.sleep(150);}
    private void stage(String name){android.os.Bundle status=new android.os.Bundle();status.putString("bedmirrorStage",name);instrumentation.sendStatus(2,status);SystemClock.sleep(1000);}
    @Test public void keyboardRoundTrip() throws Exception {
        Intent launch=new Intent(instrumentation.getTargetContext(),HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        active=instrumentation.startActivitySync(launch);
        String pcName = InstrumentationRegistry.getArguments().getString("pcName", "Saved PC");
        click(pcName + " · Connect");
        long until=SystemClock.elapsedRealtime()+30000;
        while(SystemClock.elapsedRealtime()<until){main(()->{for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))if(a instanceof com.limelight.Game)active=a;});if(active instanceof com.limelight.Game)break;SystemClock.sleep(250);}
        assertTrue("Streaming activity opens",active instanceof com.limelight.Game);
        SystemClock.sleep(6000);click("Keyboard");SystemClock.sleep(1000);
        main(()->{editor=edit(active.getWindow().getDecorView());assertNotNull("Native Android editor",editor);assertTrue("Editor is focused",editor.hasFocus());});
        main(()->assertNotNull("Use the actual served IME connection",input));
        // Samsung IME reacts to edits made by a second input client. Detach it for synthetic
        // protocol operations; opening the real installed keyboard is checked above separately.
        main(()->{editor.setShowSoftInputOnFocus(false);editor.clearFocus();((InputMethodManager)active.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);});
        SystemClock.sleep(700);
        main(()->input=editor.onCreateInputConnection(new EditorInfo()));
        stage("ready-for-isolated-Windows-target");gate("clear");
        click("Ctrl");main(()->input.commitText("a",1));click("Ctrl");
        main(()->{input.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DEL));input.sendKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_DEL));});stage("initial-context-cleared");
        gate("compose");main(()->input.setComposingText("teh",1));stage("composition-deferred");
        gate("commit");main(()->input.commitText("the ",1));stage("the ");
        gate("correct");main(()->{input.setComposingRegion(0,3);input.setComposingText("The",1);input.finishComposingText();input.setSelection(editor.length(),editor.length());});stage("The ");
        gate("unicode");main(()->input.commitText("世界 😀",1));stage("The 世界 😀");
        gate("deleteEmoji");main(()->input.deleteSurroundingTextInCodePoints(1,0));stage("The 世界 ");
        gate("deleteSpace");main(()->input.deleteSurroundingText(1,0));stage("The 世界");
        gate("shortcut");click("Ctrl");main(()->input.commitText("a",1));click("Ctrl");main(()->input.commitText("Shortcuts OK",1));stage("Shortcuts OK");
        assertTrue("Connection remains in streaming activity",active instanceof com.limelight.Game);
    }
}
