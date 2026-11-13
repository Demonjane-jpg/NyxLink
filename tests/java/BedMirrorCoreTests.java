import com.limelight.bedmirror.KeyChord;
import com.limelight.bedmirror.TextSynchronizer;
import com.limelight.bedmirror.ViewportMapper;

public final class BedMirrorCoreTests {
    static int count;
    static void check(String name,boolean ok){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
    static final class Remote implements TextSynchronizer.Sink {
        String value="";int cursor,anchor,events;
        void deleteSelection(){if(cursor!=anchor){int lo=Math.min(cursor,anchor),hi=Math.max(cursor,anchor);value=value.substring(0,lo)+value.substring(hi);cursor=anchor=lo;}}
        public void text(String text){deleteSelection();value=value.substring(0,cursor)+text+value.substring(cursor);cursor+=text.length();anchor=cursor;events++;}
        public void key(int vk){events++;if(vk==8){if(cursor!=anchor){deleteSelection();return;}if(cursor>0){int p=value.offsetByCodePoints(cursor,-1);value=value.substring(0,p)+value.substring(cursor);cursor=anchor=p;}}else if(vk==39){cursor=anchor=Math.max(cursor,anchor);}}
        public void move(int delta,boolean select){cursor=value.offsetByCodePoints(cursor,delta);if(!select)anchor=cursor;events++;}
    }
    public static void main(String[] args){
        Remote remote=new Remote();TextSynchronizer sync=new TextSynchronizer(remote);
        sync.update("hel",3,3,true);check("composition is deferred",remote.value.isEmpty());
        sync.update("hello",5,5,false);check("committed text reaches remote",remote.value.equals("hello"));
        int before=remote.events;sync.update("hello",5,5,false);check("duplicate IME notification does not duplicate text",remote.events==before);
        sync.update("Hello",5,5,false);check("autocorrect replaces existing text",remote.value.equals("Hello"));
        sync.update("Hello 世界 😀",11,11,false);check("Unicode and emoji preserved",remote.value.equals("Hello 世界 😀"));
        sync.update("Hello 世界 ",9,9,false);check("emoji deletion preserves preceding Unicode",remote.value.equals("Hello 世界 "));
        sync.update("Hello 世界",8,8,false);check("ordinary deletion",remote.value.equals("Hello 世界"));
        sync.update("Hello 世界",6,8,false);sync.update("Hello friend",12,12,false);check("selection replacement",remote.value.equals("Hello friend"));
        sync.reset();sync.update(" next",5,5,false);check("focus reset does not erase unknown remote content",remote.value.equals("Hello friend next"));
        KeyChord ctrl=KeyChord.parse("Ctrl+Shift+M");check("Discord shortcut modifiers",ctrl.key==77&&ctrl.modifiers==3);
        check("F11 mapping",KeyChord.parse("F11").key==122);
        check("Windows shortcut",KeyChord.parse("Win+D").modifiers==8);
        KeyChord snip=KeyChord.parse("Win+Shift+S");check("Win+Shift+S mapping",snip.key=='S'&&snip.modifiers==9);
        KeyChord altTab=KeyChord.parse("Alt+Tab");check("Alt+Tab mapping",altTab.key==9&&altTab.modifiers==4);
        check("standalone Win mapping",KeyChord.parse("Win").key==0x5B&&KeyChord.parse("Win").modifiers==0);
        check("PC navigation and system keys mapping",KeyChord.parse("Esc").key==27&&KeyChord.parse("CapsLock").key==20&&KeyChord.parse("PrtSc").key==44&&KeyChord.parse("ScrLk").key==145&&KeyChord.parse("Pause").key==19&&KeyChord.parse("Ins").key==45&&KeyChord.parse("Del").key==46&&KeyChord.parse("Home").key==36&&KeyChord.parse("End").key==35&&KeyChord.parse("PgUp").key==33&&KeyChord.parse("PgDn").key==34&&KeyChord.parse("Up").key==38&&KeyChord.parse("Down").key==40&&KeyChord.parse("Left").key==37&&KeyChord.parse("Right").key==39);
        boolean rejected=false;try{KeyChord.parse("Ctrl+Bogus");}catch(IllegalArgumentException e){rejected=true;}check("invalid shortcut rejected",rejected);
        check("letterbox tap excluded",ViewportMapper.map(20,20,0,100,1000,500,1)==null);
        float[] center=ViewportMapper.map(500,350,0,100,1000,500,1);check("landscape center maps correctly",center[0]==.5f&&center[1]==.5f);
        float[] portrait=ViewportMapper.map(250,600,0,475,500,250,1);check("portrait letterbox center maps correctly",portrait[0]==.5f&&portrait[1]==.5f);
        float[] zoom=ViewportMapper.map(500,350,100,100,1000,500,2);check("zoom and translated view map correctly",Math.abs(zoom[0]-.45f)<.0001f&&zoom[1]==.5f);
        check("live switch accepts touches inside a pillarboxed capture",ViewportMapper.insideCapture(.4f,.5f,1280,720,1080,1920));
        check("live switch rejects touches in the encoded black bars",!ViewportMapper.insideCapture(.1f,.5f,1280,720,1080,1920));
        String rawLeak = "Error in auth: bearer xyz123456 token=secret_token password: my_password pin: 12345 -----BEGIN RSA PRIVATE KEY-----MIIEpAIBAAKCAQEA0-----END RSA PRIVATE KEY-----";
        String clean = rawLeak.replaceAll("(?i)(bearer\\s+|token[=:]\\s*|password[=:]\\s*|secret[=:]\\s*|pin[=:]\\s*)[^\\s,;&\"]+", "$1[REDACTED]")
                              .replaceAll("-----BEGIN [A-Z ]+PRIVATE KEY-----[^-]+-----END [A-Z ]+PRIVATE KEY-----", "[REDACTED_PRIVATE_KEY]");
        check("bearer token redacted", !clean.contains("xyz123456") && clean.contains("bearer [REDACTED]"));
        check("token assignment redacted", !clean.contains("secret_token") && clean.contains("token=[REDACTED]"));
        check("password redacted", !clean.contains("my_password") && clean.contains("password: [REDACTED]"));
        check("pin redacted", !clean.contains("12345") && clean.contains("pin: [REDACTED]"));
        check("private key redacted", !clean.contains("MIIEp") && clean.contains("[REDACTED_PRIVATE_KEY]"));
        System.out.println(count+" core tests passed. These are protocol/model tests, not physical IME tests.");
    }
}
