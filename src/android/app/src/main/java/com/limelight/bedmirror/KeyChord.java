package com.limelight.bedmirror;

import java.util.Locale;

public final class KeyChord {
    public final int key;
    public final byte modifiers;
    private KeyChord(int key,byte modifiers){this.key=key;this.modifiers=modifiers;}
    public static KeyChord parse(String input){
        byte mods=0;int key=0;
        for(String raw:input.toUpperCase(Locale.ROOT).split("\\+")){
            String part=raw.trim();
            switch(part){case "CTRL":case "CONTROL":mods|=2;continue;case "ALT":mods|=4;continue;case "SHIFT":mods|=1;continue;case "WIN":case "WINDOWS":mods|=8;continue;}
            if(key!=0)throw new IllegalArgumentException("Only one non-modifier key is supported");
            if(part.length()==1&&Character.isLetterOrDigit(part.charAt(0)))key=part.charAt(0);
            else if(part.matches("F([1-9]|1[0-9]|2[0-4])"))key=111+Integer.parseInt(part.substring(1));
            else switch(part){case "ESC":case "ESCAPE":key=27;break;case "TAB":key=9;break;case "CAPSLOCK":case "CAPS":key=20;break;case "PRTSC":case "PRTSCN":case "PRINTSCREEN":key=44;break;case "SCRLK":case "SCROLLLOCK":key=145;break;case "PAUSE":case "BREAK":key=19;break;case "INS":case "INSERT":key=45;break;case "DEL":case "DELETE":key=46;break;case "HOME":key=36;break;case "END":key=35;break;case "PGUP":case "PAGEUP":key=33;break;case "PGDN":case "PAGEDOWN":key=34;break;case "ENTER":case "RETURN":key=13;break;case "SPACE":key=32;break;case "BACKSPACE":case "BKSP":key=8;break;case "LEFT":key=37;break;case "UP":key=38;break;case "RIGHT":key=39;break;case "DOWN":key=40;break;default:throw new IllegalArgumentException("Unknown key");}
        }
        if(key==0&&mods==8)return new KeyChord(0x5B,(byte)0);
        if(key==0)throw new IllegalArgumentException("Missing key");return new KeyChord(key,mods);
    }
}
