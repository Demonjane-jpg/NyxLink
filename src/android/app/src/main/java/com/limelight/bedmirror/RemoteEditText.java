package com.limelight.bedmirror;

import android.content.Context;
import android.text.*;
import android.view.KeyEvent;
import android.view.inputmethod.*;
import android.widget.EditText;

/** Real Android IME editor. Composition stays local until committed, including swipe and dictation. */
public final class RemoteEditText extends EditText {
    public interface Sink extends TextSynchronizer.Sink {boolean allowed(); boolean modifiedText(CharSequence text);}
    private final Sink sink;
    private final TextSynchronizer sync;
    private boolean suppress;
    private int nesting;
    private boolean composing;
    private int composingStart=-1, composingEnd=-1;
    private InputConnection connection;
    public RemoteEditText(Context context,Sink sink){
        super(context);this.sink=sink;sync=new TextSynchronizer(sink);
        setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN|EditorInfo.IME_ACTION_NONE);
        setSingleLine(false);setMaxLines(2);
        addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){}public void afterTextChanged(Editable text){if(nesting==0)post(RemoteEditText.this::flush);}});
    }
    private void flush(){if(suppress||nesting>0||!sink.allowed())return;Editable value=getText();sync.update(value.toString(),getSelectionStart(),getSelectionEnd(),composing||BaseInputConnection.getComposingSpanStart(value)>=0);}
    private void selectComposition(){if(composing&&composingStart>=0){Editable value=getText();int start=Math.min(value.length(),composingStart),end=Math.min(value.length(),Math.max(start,composingEnd));BaseInputConnection.removeComposingSpans(value);Selection.setSelection(value,start,end);}}
    private void clearComposition(){composing=false;composingStart=composingEnd=-1;}
    @Override protected void onSelectionChanged(int start,int end){super.onSelectionChanged(start,end);if(sync!=null&&nesting==0&&!suppress)post(this::flush);}
    @Override public InputConnection onCreateInputConnection(EditorInfo info){
        InputConnection base=super.onCreateInputConnection(info);
        connection=new InputConnectionWrapper(base,true){
            private boolean begin(){nesting++;return true;}
            private void done(){nesting--;flush();}
            public boolean beginBatchEdit(){nesting++;return super.beginBatchEdit();}
            public boolean endBatchEdit(){boolean result=super.endBatchEdit();nesting=Math.max(0,nesting-1);flush();return result;}
            public boolean setComposingText(CharSequence text,int cursor){if(sink.allowed()&&sink.modifiedText(text)){invalidateContext();return true;}begin();try{selectComposition();int start=Math.max(0,Math.min(getSelectionStart(),getSelectionEnd()));composing=true;boolean result=super.setComposingText(text,cursor);composingStart=start;composingEnd=Math.min(getText().length(),start+text.length());if(text.length()==0)clearComposition();return result;}finally{done();}}
            public boolean setComposingText(CharSequence text,int cursor,TextAttribute attributes){return setComposingText(text,cursor);}
            public boolean setComposingRegion(int start,int end){begin();try{boolean result=super.setComposingRegion(start,end);composing=start!=end;composingStart=Math.min(start,end);composingEnd=Math.max(start,end);return result;}finally{done();}}
            public boolean setComposingRegion(int start,int end,TextAttribute attributes){return setComposingRegion(start,end);}
            public boolean finishComposingText(){begin();try{boolean result=super.finishComposingText();clearComposition();return result;}finally{done();}}
            public boolean commitText(CharSequence text,int cursor){if(sink.allowed()&&sink.modifiedText(text)){invalidateContext();return true;}if(text.toString().equals("\n")){finishComposition();sink.key(13);invalidateContext();return true;}begin();try{selectComposition();boolean result=super.commitText(text,cursor);clearComposition();return result;}finally{done();}}
            public boolean commitText(CharSequence text,int cursor,TextAttribute attributes){return commitText(text,cursor);}
            public boolean replaceText(int start,int end,CharSequence text,int cursor,TextAttribute attributes){if(sink.allowed()&&sink.modifiedText(text)){invalidateContext();return true;}begin();try{clearComposition();BaseInputConnection.removeComposingSpans(getText());super.setSelection(start,end);return super.commitText(text,cursor);}finally{done();}}
            public boolean commitCorrection(CorrectionInfo correction){begin();try{int offset=correction.getOffset();CharSequence old=correction.getOldText(),replacement=correction.getNewText();if(old!=null&&replacement!=null&&offset>=0&&offset+old.length()<=getText().length()&&getText().subSequence(offset,offset+old.length()).toString().contentEquals(old)){clearComposition();BaseInputConnection.removeComposingSpans(getText());super.setSelection(offset,offset+old.length());return super.commitText(replacement,1);}return super.commitCorrection(correction);}finally{done();}}
            public boolean deleteSurroundingText(int before,int after){
                if(getText().length()==0){if(sink.allowed()){for(int i=0;i<Math.min(before,100);i++)sink.key(8);for(int i=0;i<Math.min(after,100);i++)sink.key(46);}return true;}
                begin();try{return super.deleteSurroundingText(before,after);}finally{done();}
            }
            public boolean deleteSurroundingTextInCodePoints(int before,int after){begin();try{return super.deleteSurroundingTextInCodePoints(before,after);}finally{done();}
            }
            public boolean sendKeyEvent(KeyEvent event){
                if(handleKeyEvent(event))return true;
                begin();try{return super.sendKeyEvent(event);}finally{done();}
            }
        };
        return connection;
    }
    private boolean handleKeyEvent(KeyEvent event){
        if(event==null)return false;
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_VOLUME_UP||code==KeyEvent.KEYCODE_VOLUME_DOWN||code==KeyEvent.KEYCODE_VOLUME_MUTE||code==KeyEvent.KEYCODE_BACK)return false;
        int vk=0;
        switch(code){
            case KeyEvent.KEYCODE_ENTER:case KeyEvent.KEYCODE_NUMPAD_ENTER:vk=13;break;
            case KeyEvent.KEYCODE_TAB:vk=9;break;
            case KeyEvent.KEYCODE_ESCAPE:vk=27;break;
            case KeyEvent.KEYCODE_DEL:if(getText().length()==0)vk=8;break;
            case KeyEvent.KEYCODE_FORWARD_DEL:vk=46;break;
            case KeyEvent.KEYCODE_INSERT:vk=45;break;
            case KeyEvent.KEYCODE_MOVE_HOME:vk=36;break;
            case KeyEvent.KEYCODE_MOVE_END:vk=35;break;
            case KeyEvent.KEYCODE_PAGE_UP:vk=33;break;
            case KeyEvent.KEYCODE_PAGE_DOWN:vk=34;break;
            case KeyEvent.KEYCODE_DPAD_LEFT:if(getText().length()==0)vk=37;break;
            case KeyEvent.KEYCODE_DPAD_UP:if(getText().length()==0)vk=38;break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:if(getText().length()==0)vk=39;break;
            case KeyEvent.KEYCODE_DPAD_DOWN:if(getText().length()==0)vk=40;break;
            case KeyEvent.KEYCODE_CAPS_LOCK:vk=20;break;
            case KeyEvent.KEYCODE_SYSRQ:vk=44;break;
            case KeyEvent.KEYCODE_SCROLL_LOCK:vk=145;break;
            case KeyEvent.KEYCODE_BREAK:vk=19;break;
            default:if(code>=KeyEvent.KEYCODE_F1&&code<=KeyEvent.KEYCODE_F12)vk=112+(code-KeyEvent.KEYCODE_F1);break;
        }
        if(vk!=0){if(event.getAction()==KeyEvent.ACTION_DOWN&&sink.allowed()){finishComposition();sink.key(vk);invalidateContext();}return true;}
        char ch=0;
        if(code>=KeyEvent.KEYCODE_A&&code<=KeyEvent.KEYCODE_Z)ch=(char)('A'+(code-KeyEvent.KEYCODE_A));
        else if(code>=KeyEvent.KEYCODE_0&&code<=KeyEvent.KEYCODE_9)ch=(char)('0'+(code-KeyEvent.KEYCODE_0));
        else if(code==KeyEvent.KEYCODE_SPACE)ch=' ';
        if(ch!=0&&event.getAction()==KeyEvent.ACTION_DOWN&&sink.allowed()&&sink.modifiedText(String.valueOf(ch))){invalidateContext();return true;}
        return false;
    }
    @Override public boolean onKeyPreIme(int keyCode,KeyEvent event){if(keyCode==KeyEvent.KEYCODE_ESCAPE&&handleKeyEvent(event))return true;return super.onKeyPreIme(keyCode,event);}
    @Override public boolean onKeyDown(int keyCode,KeyEvent event){if(handleKeyEvent(event))return true;return super.onKeyDown(keyCode,event);}
    @Override public boolean onKeyShortcut(int keyCode,KeyEvent event){if(handleKeyEvent(event))return true;return super.onKeyShortcut(keyCode,event);}
    public void finishComposition(){clearComposition();BaseInputConnection.removeComposingSpans(getText());flush();}
    public void invalidateContext(){if(suppress)return;suppress=true;clearComposition();sync.reset();getText().clear();suppress=false;}
    public void shortcutMode(boolean active){finishComposition();invalidateContext();setInputType(android.text.InputType.TYPE_CLASS_TEXT|(active?android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD:android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES));setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN|EditorInfo.IME_ACTION_NONE);}
}
