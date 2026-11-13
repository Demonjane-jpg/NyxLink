package com.limelight.bedmirror;

import java.util.Arrays;

/** Tracks only text entered in this input session; never assumes knowledge of a Windows field. */
public final class TextSynchronizer {
    public interface Sink {void text(String text);void key(int vk);void move(int count,boolean select);}
    private String sent="";
    private int caret,anchor;
    private final Sink sink;
    public TextSynchronizer(Sink sink){this.sink=sink;}
    public void reset(){sent="";caret=anchor=0;}
    public void update(String text,int start,int end,boolean composing){
        if(composing)return;
        int[] old=sent.codePoints().toArray(),next=text.codePoints().toArray();
        int prefix=0;while(prefix<old.length&&prefix<next.length&&old[prefix]==next[prefix])prefix++;
        int suffix=0;while(suffix<old.length-prefix&&suffix<next.length-prefix&&old[old.length-1-suffix]==next[next.length-1-suffix])suffix++;
        if(!sent.equals(text)){
            collapseSelection();
            int editEnd=old.length-suffix;move(editEnd);
            for(int i=prefix;i<editEnd;i++){sink.key(8);caret--;}
            if(next.length-prefix-suffix>0){sink.text(new String(next,prefix,next.length-prefix-suffix));caret+=next.length-prefix-suffix;}
            anchor=caret;
            sent=text;
        }
        int targetStart=text.codePointCount(0,Math.max(0,Math.min(text.length(),start)));
        int targetEnd=text.codePointCount(0,Math.max(0,Math.min(text.length(),end)));
        if(anchor!=targetStart||caret!=targetEnd){collapseSelection();move(targetStart);anchor=caret;if(targetEnd!=caret){sink.move(targetEnd-caret,true);caret=targetEnd;}}
    }
    private void collapseSelection(){if(caret!=anchor){sink.key(39);caret=Math.max(caret,anchor);anchor=caret;}}
    private void move(int target){if(target!=caret)sink.move(target-caret,false);caret=anchor=target;}
}
