package com.limelight.bedmirror;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.limelight.Game;
import com.limelight.nvstream.NvConnection;
import com.limelight.nvstream.input.KeyboardPacket;
import com.limelight.ui.StreamView;
import org.json.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.*;

/** BedMirror owns touch arbitration; streaming/decoding remains upstream Moonlight. */
public final class BedMirrorControls {
    private final Game activity;
    private final NvConnection conn;
    private final StreamView video;
    private final SharedPreferences prefs;
    private final FrameLayout layer;
    private final LinearLayout controls, keyboardBar;
    private RemoteEditText editor;
    private final TextView state;
    private TextView unlock;
    private final View privacyScreen;
    private final GestureSurface gestures;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private CompanionClient api;
    private final X509Certificate serverCert;
    private String identity = "", pcFingerprint = "", activeHost = "", localHost = "", remoteHost = "";
    private boolean connectedRemotely, reconnecting, connectionUnavailable;
    private Button cancelReconnectButton;
    private android.net.ConnectivityManager connectivityManager;
    private android.net.ConnectivityManager.NetworkCallback networkCallback;
    private android.net.Network activeNetwork;
    private String lastRecoveryWhy = "Connection interrupted";
    private final Runnable recoveryStep = this::executeRecoveryStep;
    private final Runnable networkChangeRunnable = this::handleDebouncedNetworkChange;
    private boolean visible=true, locked, viewOnly, fullscreen=true, ready, privacy, destroyed, intentional;
    private boolean controlsPinned, trackpad, switchingMonitor;
    private int captureWidth, captureHeight;
    private int retry;
    private byte modifiers;
    private boolean clearingModifiers, modifierComboUsed;
    private final java.util.ArrayList<ToggleButton> modifierButtons=new java.util.ArrayList<>();
    private LinearLayout keyboardHeader;
    private Button pcKeysButton, androidKeyboardButton, hideKeyboardButton;
    private Button keyEsc, keyTab, keyCaps, keyPrtSc, keyScrLk, keyPause, keyIns, keyDel, keyHome, keyEnd, keyPgUp, keyPgDn, keyUp, keyDown, keyLeft, keyRight;
    private final Button[] keyFn=new Button[12];
    private ToggleButton modCtrl, modAlt, modShift, modWin;
    private boolean androidImeActive, lastLandscape;
    private float zoom=1, sensitivity=1;
    private String measuredStatistics="No stream measurements yet.";
    private long poorSince, lastQualityChange;
    private long poorGeneration;
    private final Runnable heartbeat=new Runnable(){public void run(){if(destroyed)return;if(ready)request(()->{api.post("session",new JSONObject().put("active",true).put("preventSleep",prefs.getBoolean("preventPcSleep",false)));});ui.postDelayed(this,5000);}};
    private boolean volumeHeld;
    private int volumeDirection;
    private final Runnable volumeLongPress=()->{volumeHeld=true;media(volumeDirection>0?"volumeUp":"volumeDown");};
    private final Runnable fade = () -> { if (!controlsPinned && !switchingMonitor && !privacy && editor != null && !editor.isShown()) setVisible(false); };

    public BedMirrorControls(Game activity, NvConnection conn, StreamView video, X509Certificate server) {
        this.activity=activity; this.conn=conn; this.video=video; this.serverCert=server;
        prefs=activity.getSharedPreferences("bedmirror",Context.MODE_PRIVATE);
        captureWidth=prefs.getInt("monitorWidth",1920);
        captureHeight=prefs.getInt("monitorHeight",1080);
        trackpad=prefs.getBoolean("trackpad",false); sensitivity=prefs.getFloat("sensitivity",1);
        controlsPinned=prefs.getBoolean("controlsPinned",false);
        layer=new FrameLayout(activity);
        activity.addContentView(layer,new FrameLayout.LayoutParams(-1,-1));
        if(Build.VERSION.SDK_INT>=30){
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            layer.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int bottom=Math.max(bars.bottom,insets.getInsets(WindowInsets.Type.ime()).bottom);View content=(View)layer.getParent();if(content.getPaddingLeft()!=bars.left||content.getPaddingTop()!=bars.top||content.getPaddingRight()!=bars.right||content.getPaddingBottom()!=bottom)content.setPadding(bars.left,bars.top,bars.right,bottom);return insets;});
        }
        gestures=new GestureSurface(activity); layer.addView(gestures,new FrameLayout.LayoutParams(-1,-1));
        privacyScreen=new View(activity); privacyScreen.setBackgroundColor(Color.BLACK); privacyScreen.setVisibility(View.GONE); layer.addView(privacyScreen,new FrameLayout.LayoutParams(-1,-1));
        controls=new LinearLayout(activity); controls.setOrientation(1); controls.setBackgroundColor(0xDD151B26); controls.setPadding(8,4,8,4);
        FrameLayout.LayoutParams top=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP); layer.addView(controls,top);
        LinearLayout stateRow=new LinearLayout(activity);
        stateRow.setOrientation(LinearLayout.HORIZONTAL);
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        state=new TextView(activity); state.setTextColor(Color.WHITE); state.setText("Connecting… · double-tap to hide / restore controls");
        stateRow.addView(state,new LinearLayout.LayoutParams(0,-2,1f));
        cancelReconnectButton=new Button(activity);
        cancelReconnectButton.setText("Cancel Reconnect");
        cancelReconnectButton.setAllCaps(false);
        cancelReconnectButton.setTextColor(Color.WHITE);
        cancelReconnectButton.setTextSize(11.5f);
        cancelReconnectButton.setBackgroundResource(com.limelight.R.drawable.nyx_button);
        cancelReconnectButton.setMinHeight(dp(34));
        cancelReconnectButton.setMinimumHeight(dp(34));
        cancelReconnectButton.setPadding(dp(10),dp(2),dp(10),dp(2));
        cancelReconnectButton.setVisibility(View.GONE);
        cancelReconnectButton.setOnClickListener(v->cancelReconnection());
        stateRow.addView(cancelReconnectButton,new LinearLayout.LayoutParams(-2,-2));
        controls.addView(stateRow);
        LinearLayout toolbar=new LinearLayout(activity);
        add(toolbar,"Monitor",this::monitors);
        LinearLayout row=new LinearLayout(activity);
        HorizontalScrollView scroller=new HorizontalScrollView(activity);
        scroller.addView(row);
        toolbar.addView(scroller,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(toolbar);
        add(row,"Keyboard",this::keyboard);
        add(row,"Audio",this::audioMenu);
        add(row,"Touch mode",()-> { releaseInput(); trackpad=!trackpad; prefs.edit().putBoolean("trackpad",trackpad).apply(); toast(trackpad?"Trackpad mode":"Direct touch mode"); });
        add(row,"Touch Lock",()-> { hideKeyboard();releaseInput(); locked=true; unlock.setVisibility(View.VISIBLE); toast("Touch locked. Hold Unlock to unlock; double-tap still restores controls."); });
        add(row,"Fullscreen",()->fullscreen(!fullscreen));
        add(row,"Rotate",this::orientation);
        add(row,"Actions",this::quickActions);
        add(row,"Privacy",this::privacy);
        add(row,"Options",this::options);
        add(row,"Reconnect",this::reconnect);
        add(row,"Disconnect",()-> { intentional=true; releaseInput(); activity.finish(); });
        unlock=new TextView(activity); unlock.setText("Hold to unlock"); unlock.setTextColor(Color.WHITE); unlock.setTextSize(16); unlock.setPadding(18,16,18,16); unlock.setBackgroundColor(0xEE34445B); unlock.setContentDescription("Hold to unlock remote touch"); unlock.setVisibility(View.GONE);
        FrameLayout.LayoutParams corner=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END); corner.setMargins(8,8,12,12); layer.addView(unlock,corner);
        unlock.setOnLongClickListener(v->{ locked=false; unlock.setVisibility(View.GONE); setVisible(true); toast("Touch unlocked"); return true; });
        LinearLayout ime=new LinearLayout(activity) {
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rect=new RectF();
            @Override protected void onConfigurationChanged(Configuration newConfig) {
                super.onConfigurationChanged(newConfig);
                boolean land=newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE;
                if(land!=lastLandscape)layoutKeyboard(land);
            }
            @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
                super.onSizeChanged(w,h,oldw,oldh);
                boolean land=isLandscape();
                if(land!=lastLandscape)post(()->layoutKeyboard(isLandscape()));
            }
            @Override protected void dispatchDraw(Canvas canvas) {
                float pad=dp(2.5f),r=dp(18);
                rect.set(pad,pad,getWidth()-pad,getHeight()-pad);
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(0,rect.top,0,rect.bottom,0xF8090D22,0xFC050814,Shader.TileMode.CLAMP));
                canvas.drawRoundRect(rect,r,r,paint);
                super.dispatchDraw(canvas);
                LinearGradient borderGrad=new LinearGradient(rect.left,rect.bottom,rect.right,rect.top,
                        new int[]{0xFFC845FF,0xFF7B4DFF,0xFF00E5FF},new float[]{0f,0.5f,1f},Shader.TileMode.CLAMP);
                paint.setStyle(Paint.Style.STROKE);
                paint.setShader(borderGrad);
                paint.setStrokeWidth(dp(4.2f));
                paint.setAlpha(70);
                canvas.drawRoundRect(rect,r,r,paint);
                paint.setStrokeWidth(dp(1.6f));
                paint.setAlpha(255);
                canvas.drawRoundRect(rect,r,r,paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF6B739E);
                float pillW=dp(36),pillH=dp(4),pillTop=pad+dp(5);
                canvas.drawRoundRect((getWidth()-pillW)/2f,pillTop,(getWidth()+pillW)/2f,pillTop+pillH,pillH/2f,pillH/2f,paint);
            }
        };
        ime.setOrientation(LinearLayout.VERTICAL);
        ime.setPadding(dp(10),dp(13),dp(10),dp(9));
        ime.setClickable(true);
        FrameLayout.LayoutParams bottom=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottom.setMargins(dp(3),0,dp(3),dp(3));
        layer.addView(ime,bottom);
        editor=new RemoteEditText(activity, new RemoteEditText.Sink() {
            public void text(String text) { if (!canControl()) return; if (modifiers != 0 && modifiedText(text)) return; conn.sendUtf8Text(text); }
            public void key(int vk) { sendKey(vk,modifiers); modifierComboUsed=true; if(modifiers!=0&&editor.hasFocus())clearModifiers(); }
            public void move(int count, boolean select) { for(int i=0;i<Math.abs(count);i++) sendKey(count<0?0x25:0x27, select?(byte)1:0); }
            public boolean allowed() { return canControl(); }
            public boolean modifiedText(CharSequence text) {
                if(modifiers==0)return false;
                boolean sent=false;
                for(int i=0;i<text.length();i++){
                    char c=Character.toUpperCase(text.charAt(i));
                    if(c>='A'&&c<='Z'||c>='0'&&c<='9'||c==' '){sendKey(c,modifiers);sent=true;}
                }
                if(sent){modifierComboUsed=true;if(editor.hasFocus())clearModifiers();}
                return sent;
            }
        });
        editor.setBackground(null); editor.setTextColor(Color.TRANSPARENT); editor.setHintTextColor(Color.TRANSPARENT);
        editor.setCursorVisible(false); editor.setPadding(0,0,0,0); editor.setTextSize(1);
        ime.addView(editor,new LinearLayout.LayoutParams(dp(1),dp(1)));
        keyboardHeader=new LinearLayout(activity);
        keyboardHeader.setOrientation(LinearLayout.HORIZONTAL);
        keyboardHeader.setGravity(Gravity.CENTER_VERTICAL);
        ime.addView(keyboardHeader,new LinearLayout.LayoutParams(-1,-2));
        View headerDivider=createDivider(true);
        LinearLayout.LayoutParams hdLp=new LinearLayout.LayoutParams(-1,dp(1));
        hdLp.setMargins(dp(2),dp(6),dp(2),dp(6));
        ime.addView(headerDivider,hdLp);
        keyboardBar=new LinearLayout(activity);
        keyboardBar.setOrientation(LinearLayout.VERTICAL);
        ime.addView(keyboardBar,new LinearLayout.LayoutParams(-1,-2));
        pcKeysButton=createHeaderButton("PC Keys",0,this::keyboard);
        androidKeyboardButton=createHeaderButton("Android Keyboard",1,this::androidKeyboard);
        hideKeyboardButton=createHeaderButton("Hide Keyboard",2,this::hideKeyboard);
        keyEsc=createKey("Esc",27);
        for(int i=0;i<12;i++)keyFn[i]=createKey("F"+(i+1),112+i);
        modCtrl=createModifier("Ctrl",(byte)2); modAlt=createModifier("Alt",(byte)4);
        modShift=createModifier("Shift",(byte)1); modWin=createModifier("Win",(byte)8);
        keyTab=createKey("Tab",9);
        keyCaps=createKey("Caps\nLock",20); keyCaps.setLineSpacing(0f,0.88f);
        keyPrtSc=createKey("PrtSc",44); keyScrLk=createKey("ScrLk",145); keyPause=createKey("Pause",19);
        keyIns=createKey("Ins",45); keyDel=createKey("Del",46);
        keyHome=createKey("Home",36); keyEnd=createKey("End",35);
        keyPgUp=createKey("PgUp",33); keyPgDn=createKey("PgDn",34);
        keyUp=createKey("↑",38); keyDown=createKey("↓",40); keyLeft=createKey("←",37); keyRight=createKey("→",39);
        layoutKeyboard(isLandscape());
        updateHeaderState();
        ime.setVisibility(View.GONE);
        editor.setOnFocusChangeListener((v,focus)-> { if(focus) { ime.setVisibility(View.VISIBLE); ui.removeCallbacks(fade); } });
        try {
            String rawIdentity = activity.getIntent().getStringExtra(Game.EXTRA_NYXLINK_IDENTITY);
            identity = rawIdentity == null ? "" : rawIdentity;
            String rawFp = activity.getIntent().getStringExtra("com.limelight.NyxLinkFingerprint");
            pcFingerprint = rawFp != null ? rawFp : (server != null ? NearbyPc.fingerprint(server) : "");
            String rawHost = activity.getIntent().getStringExtra(Game.EXTRA_HOST);
            activeHost = rawHost == null ? "" : rawHost;
            String rawLocal = activity.getIntent().getStringExtra("com.limelight.NyxLinkLocalHost");
            localHost = rawLocal != null ? rawLocal : activeHost;
            String rawRemote = activity.getIntent().getStringExtra("com.limelight.NyxLinkRemoteHost");
            remoteHost = rawRemote != null ? rawRemote : "";
            connectedRemotely = activity.getIntent().getBooleanExtra("com.limelight.NyxLinkRemoteSession", CompanionClient.isRemotePrivateAddress(activeHost));
            refreshSavedEndpoints();
            api = new CompanionClient(activity, activeHost, server, identity);
        } catch(Exception e) { toast(e.getMessage()); }
        registerNetworkMonitor();
        float brightness=prefs.getFloat("brightness",-1); setBrightness(brightness);
        fullscreen(prefs.getBoolean("fullscreen",true));
        ui.postDelayed(fade,8000);
        ui.postDelayed(heartbeat,5000);
    }
    private int dp(float n) { return (int)(n*activity.getResources().getDisplayMetrics().density+0.5f); }
    private void add(LinearLayout row,String label,Runnable action) { Button b=new Button(activity); int left=b.getPaddingLeft(),top=b.getPaddingTop(),right=b.getPaddingRight(),bottom=b.getPaddingBottom(); b.setBackgroundTintList(null); b.setBackgroundResource(com.limelight.R.drawable.nyx_button); b.setPadding(left,top,right,bottom); b.setTextColor(Color.WHITE); b.setText(label); b.setMinHeight(dp(48)); b.setTextSize(13); row.addView(b); b.setOnClickListener(v->{ui.removeCallbacks(fade);action.run();if(!controlsPinned)ui.postDelayed(fade,8000);}); }
    private void toast(String text) { Toast.makeText(activity,text,Toast.LENGTH_LONG).show(); }
    private boolean isLandscape() { return activity.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE; }
    private View createDivider(boolean horizontal) {
        View v=new View(activity);
        v.setBackgroundColor(0xFF222D52);
        return v;
    }
    private Drawable headerIcon(int type) {
        int w=dp(18),h=dp(16);
        Drawable d=new Drawable() {
            private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas c) {
                Rect b=getBounds();
                float sx=b.left,sy=b.top,bw=b.width(),bh=b.height();
                p.setColor(Color.WHITE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                if(type==0) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f));
                    RectF r=new RectF(sx+dp(1),sy+dp(2.2f),sx+bw-dp(1),sy+bh-dp(2.2f));
                    c.drawRoundRect(r,dp(2.2f),dp(2.2f),p);
                    p.setStyle(Paint.Style.FILL);
                    for(int row=0;row<2;row++)for(int col=0;col<4;col++){
                        float kx=r.left+dp(2.5f)+col*dp(3.1f),ky=r.top+dp(2.4f)+row*dp(2.7f);
                        c.drawRect(kx,ky,kx+dp(1.5f),ky+dp(1.4f),p);
                    }
                    c.drawRect(r.centerX()-dp(3.2f),r.bottom-dp(2.6f),r.centerX()+dp(3.2f),r.bottom-dp(1.4f),p);
                } else if(type==1) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f));
                    RectF r=new RectF(sx+dp(4.2f),sy+dp(1f),sx+bw-dp(4.2f),sy+bh-dp(1f));
                    c.drawRoundRect(r,dp(2f),dp(2f),p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawRect(r.centerX()-dp(1.6f),r.top+dp(1.6f),r.centerX()+dp(1.6f),r.top+dp(2.5f),p);
                    c.drawCircle(r.centerX(),r.bottom-dp(2.1f),dp(1f),p);
                } else {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f));
                    Path eye=new Path();
                    float cy=sy+bh/2f,lx=sx+dp(1.2f),rx=sx+bw-dp(1.2f),mx=(lx+rx)/2f;
                    eye.moveTo(lx,cy);
                    eye.quadTo(mx,sy+dp(1.2f),rx,cy);
                    eye.quadTo(mx,sy+bh-dp(1.2f),lx,cy);
                    c.drawPath(eye,p);
                    c.drawCircle(mx,cy,dp(2.1f),p);
                    c.drawLine(sx+dp(2.2f),sy+bh-dp(1.5f),sx+bw-dp(2.2f),sy+dp(1.5f),p);
                }
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(ColorFilter cf) {}
            @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
            @Override public int getIntrinsicWidth() { return w; }
            @Override public int getIntrinsicHeight() { return h; }
        };
        d.setBounds(0,0,w,h);
        return d;
    }
    private Button createHeaderButton(String label,int iconType,Runnable action) {
        Button b=new Button(activity) {
            private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF r=new RectF();
            @Override protected void onDraw(Canvas canvas) {
                boolean active=(iconType==0&&!androidImeActive)||(iconType==1&&androidImeActive);
                boolean lit=active||isPressed();
                float inset=dp(1.5f),rad=dp(9.5f);
                r.set(inset,inset,getWidth()-inset,getHeight()-inset);
                p.setStyle(Paint.Style.FILL);
                p.setShader(new LinearGradient(0,r.top,0,r.bottom,lit?0xFF27174E:0xFF101730,lit?0xFF151030:0xFF0A0F22,Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r,rad,rad,p);
                if(lit) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setShader(null);
                    p.setColor(0x55C44DFF);
                    p.setStrokeWidth(dp(3.2f));
                    canvas.drawRoundRect(r,rad,rad,p);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(lit?1.5f:1f));
                p.setShader(new LinearGradient(r.left,r.top,r.right,r.bottom,lit?0xFFD55CFF:0xFF334170,lit?0xFF8C42FF:0xFF242E54,Shader.TileMode.CLAMP));
                canvas.drawRoundRect(r,rad,rad,p);
                super.onDraw(canvas);
            }
        };
        b.setBackground(null); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setText(label);
        b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        b.setMinimumWidth(0); b.setMinWidth(0); b.setMinimumHeight(0); b.setMinHeight(0);
        b.setIncludeFontPadding(false);
        b.setFocusable(false); b.setFocusableInTouchMode(false);
        b.setCompoundDrawables(headerIcon(iconType),null,null,null);
        b.setCompoundDrawablePadding(dp(6));
        b.setOnClickListener(v->{ui.removeCallbacks(fade);action.run();if(!controlsPinned)ui.postDelayed(fade,8000);});
        return b;
    }
    private void updateHeaderState() {
        if(pcKeysButton!=null)pcKeysButton.invalidate();
        if(androidKeyboardButton!=null)androidKeyboardButton.invalidate();
        if(hideKeyboardButton!=null)hideKeyboardButton.invalidate();
    }
    private void drawKeycap(Canvas canvas,int width,int height,boolean active) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float inset=dp(1f),rad=dp(7.5f);
        RectF r=new RectF(inset,inset,width-inset,height-inset);
        p.setStyle(Paint.Style.FILL);
        p.setShader(new LinearGradient(0,r.top,0,r.bottom,active?0xFF2A1956:0xFF151E3D,active?0xFF161E46:0xFF0B1024,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r,rad,rad,p);
        if(active) {
            p.setStyle(Paint.Style.STROKE);
            p.setShader(null);
            p.setColor(0x50C44DFF);
            p.setStrokeWidth(dp(2.6f));
            canvas.drawRoundRect(r,rad,rad,p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(active?1.5f:1f));
        p.setShader(new LinearGradient(0,r.top,0,r.bottom,active?0xFFD258FF:0xFF3C4B7E,active?0xFF00E5FF:0xFF242D52,Shader.TileMode.CLAMP));
        canvas.drawRoundRect(r,rad,rad,p);
    }
    private void drawWinIcon(Canvas canvas,int width,int height) {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setStyle(Paint.Style.FILL);
        float cx=width/2f,cy=height/2f,w=dp(12f),h=dp(10.5f),gap=dp(1.3f);
        float l=cx-w/2f,r=cx+w/2f,t=cy-h/2f,b=cy+h/2f,tl=t+dp(1.3f),bl=b-dp(1.3f);
        float midX=cx-dp(0.3f),leftW=(midX-gap/2f)-l,rightX=midX+gap/2f;
        float midTopLeft=tl+(t-tl)*((leftW)/w),midTopRight=tl+(t-tl)*((rightX-l)/w);
        float midBotLeft=bl+(b-bl)*((leftW)/w),midBotRight=bl+(b-bl)*((rightX-l)/w);
        Path q1=new Path(); q1.moveTo(l,tl); q1.lineTo(midX-gap/2f,midTopLeft); q1.lineTo(midX-gap/2f,cy-gap/2f); q1.lineTo(l,cy-gap/2f); q1.close(); canvas.drawPath(q1,p);
        Path q2=new Path(); q2.moveTo(rightX,midTopRight); q2.lineTo(r,t); q2.lineTo(r,cy-gap/2f); q2.lineTo(rightX,cy-gap/2f); q2.close(); canvas.drawPath(q2,p);
        Path q3=new Path(); q3.moveTo(l,cy+gap/2f); q3.lineTo(midX-gap/2f,cy+gap/2f); q3.lineTo(midX-gap/2f,midBotLeft); q3.lineTo(l,bl); q3.close(); canvas.drawPath(q3,p);
        Path q4=new Path(); q4.moveTo(rightX,cy+gap/2f); q4.lineTo(r,cy+gap/2f); q4.lineTo(r,b); q4.lineTo(rightX,midBotRight); q4.close(); canvas.drawPath(q4,p);
    }
    private Button createKey(String label,int vk) {
        Button b=new Button(activity) {
            @Override protected void onDraw(Canvas canvas) {
                drawKeycap(canvas,getWidth(),getHeight(),isPressed());
                super.onDraw(canvas);
            }
        };
        b.setBackground(null); b.setAllCaps(false); b.setTextColor(Color.WHITE); b.setText(label);
        b.setGravity(Gravity.CENTER); b.setPadding(0,0,0,0);
        b.setMinimumWidth(0); b.setMinWidth(0); b.setMinimumHeight(0); b.setMinHeight(0);
        b.setIncludeFontPadding(false);
        b.setFocusable(false); b.setFocusableInTouchMode(false);
        b.setOnClickListener(v->{ui.removeCallbacks(fade);tapKey(vk);if(!controlsPinned)ui.postDelayed(fade,8000);});
        return b;
    }
    private ToggleButton createModifier(String label,byte mask) {
        ToggleButton button=new ToggleButton(activity) {
            @Override protected void onDraw(Canvas canvas) {
                drawKeycap(canvas,getWidth(),getHeight(),isChecked()||isPressed());
                if("Win".equals(label))drawWinIcon(canvas,getWidth(),getHeight());
                else super.onDraw(canvas);
            }
        };
        button.setBackground(null); button.setAllCaps(false); button.setTextColor(Color.WHITE);
        button.setTextOff(label); button.setTextOn(label); button.setText(label);
        button.setContentDescription(label);
        button.setGravity(Gravity.CENTER); button.setPadding(0,0,0,0);
        button.setMinimumWidth(0); button.setMinWidth(0); button.setMinimumHeight(0); button.setMinHeight(0);
        button.setIncludeFontPadding(false);
        button.setFocusable(false); button.setFocusableInTouchMode(false);
        button.setChecked(false);
        modifierButtons.add(button);
        button.setOnCheckedChangeListener((b,checked)->{
            if(clearingModifiers){b.invalidate();return;}
            ui.removeCallbacks(fade);
            editor.finishComposition();
            boolean wasOnlyModifier=(modifiers&~mask)==0;
            boolean sendStandalone=!checked&&wasOnlyModifier&&!modifierComboUsed&&(mask==8||editor.hasFocus());
            modifiers=checked?(byte)(modifiers|mask):(byte)(modifiers&~mask);
            if(checked)modifierComboUsed=!wasOnlyModifier;
            editor.shortcutMode(modifiers!=0);
            if(androidImeActive){InputMethodManager imm=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);if(imm!=null)imm.restartInput(editor);}
            b.invalidate();
            if(sendStandalone)sendKey(mask==2?0xA2:mask==4?0xA4:mask==8?0x5B:0xA0,(byte)0);
            if(!controlsPinned)ui.postDelayed(fade,8000);
        });
        return button;
    }
    private void detach(View v) { if(v!=null&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v); }
    private void addKeyToRow(LinearLayout row,TextView key,float weight,int heightDp,float textSp,int leftDp,int rightDp) {
        detach(key);
        key.setTextSize(textSp);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(heightDp),weight);
        lp.setMargins(dp(leftDp),0,dp(rightDp),0);
        row.addView(key,lp);
    }
    private void addSpacerToRow(LinearLayout row,float weight,int heightDp,int leftDp,int rightDp) {
        Space s=new Space(activity);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(heightDp),weight);
        lp.setMargins(dp(leftDp),0,dp(rightDp),0);
        row.addView(s,lp);
    }
    private void layoutKeyboard(boolean landscape) {
        lastLandscape=landscape;
        keyboardHeader.removeAllViews();
        keyboardBar.removeAllViews();
        detach(pcKeysButton); detach(androidKeyboardButton); detach(hideKeyboardButton);
        if(landscape) {
            pcKeysButton.setTextSize(12f); androidKeyboardButton.setTextSize(12f); hideKeyboardButton.setTextSize(12f);
            pcKeysButton.setPadding(dp(12),0,dp(14),0); androidKeyboardButton.setPadding(dp(12),0,dp(14),0); hideKeyboardButton.setPadding(dp(12),0,dp(14),0);
            LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(-2,dp(34)); p1.setMargins(0,0,dp(8),0); keyboardHeader.addView(pcKeysButton,p1);
            LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(-2,dp(34)); keyboardHeader.addView(androidKeyboardButton,p2);
            keyboardHeader.addView(new Space(activity),new LinearLayout.LayoutParams(0,dp(1),1f));
            LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(-2,dp(34)); keyboardHeader.addView(hideKeyboardButton,p3);

            LinearLayout body=new LinearLayout(activity); body.setOrientation(LinearLayout.HORIZONTAL);
            keyboardBar.addView(body,new LinearLayout.LayoutParams(-1,-2));
            LinearLayout leftCol=new LinearLayout(activity); leftCol.setOrientation(LinearLayout.VERTICAL);
            body.addView(leftCol,new LinearLayout.LayoutParams(0,-2,67f));

            LinearLayout fnRow=new LinearLayout(activity); fnRow.setOrientation(LinearLayout.HORIZONTAL);
            leftCol.addView(fnRow,new LinearLayout.LayoutParams(-1,-2));
            addKeyToRow(fnRow,keyEsc,1.25f,34,11f,0,7);
            for(int i=0;i<12;i++) addKeyToRow(fnRow,keyFn[i],1f,34,10.5f,i==0||i==4||i==8?0:2,(i==3||i==7)?6:1);

            LinearLayout modRow=new LinearLayout(activity); modRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams modRowLp=new LinearLayout.LayoutParams(-1,-2); modRowLp.setMargins(0,dp(6),0,0);
            leftCol.addView(modRow,modRowLp);
            addKeyToRow(modRow,modCtrl,1.1f,38,11.5f,0,2);
            addKeyToRow(modRow,modAlt,1.1f,38,11.5f,2,2);
            addKeyToRow(modRow,modWin,1.1f,38,11.5f,2,2);
            addKeyToRow(modRow,modShift,1.15f,38,11.5f,2,8);
            addKeyToRow(modRow,keyTab,1.05f,38,11.5f,0,2);
            addKeyToRow(modRow,keyCaps,1.05f,38,9.5f,2,8);
            addKeyToRow(modRow,keyPrtSc,1.05f,38,10.5f,0,2);
            addKeyToRow(modRow,keyScrLk,1.05f,38,10.5f,2,2);
            addKeyToRow(modRow,keyPause,1.1f,38,10.5f,2,0);

            View vDiv=createDivider(false);
            LinearLayout.LayoutParams vdLp=new LinearLayout.LayoutParams(dp(1),-1);
            vdLp.setMargins(dp(8),dp(2),dp(8),dp(2));
            body.addView(vDiv,vdLp);

            LinearLayout rightCol=new LinearLayout(activity); rightCol.setOrientation(LinearLayout.HORIZONTAL);
            body.addView(rightCol,new LinearLayout.LayoutParams(0,-2,33f));

            LinearLayout navCol=new LinearLayout(activity); navCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams navColLp=new LinearLayout.LayoutParams(0,-2,1f); navColLp.setMargins(0,0,dp(6),0);
            rightCol.addView(navCol,navColLp);
            LinearLayout navTop=new LinearLayout(activity); navTop.setOrientation(LinearLayout.HORIZONTAL);
            navCol.addView(navTop,new LinearLayout.LayoutParams(-1,-2));
            addKeyToRow(navTop,keyIns,1f,34,10.5f,0,2);
            addKeyToRow(navTop,keyHome,1f,34,10.5f,1,2);
            addKeyToRow(navTop,keyPgUp,1f,34,10.5f,1,0);
            LinearLayout navBot=new LinearLayout(activity); navBot.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams navBotLp=new LinearLayout.LayoutParams(-1,-2); navBotLp.setMargins(0,dp(6),0,0);
            navCol.addView(navBot,navBotLp);
            addKeyToRow(navBot,keyDel,1f,38,10.5f,0,2);
            addKeyToRow(navBot,keyEnd,1f,38,10.5f,1,2);
            addKeyToRow(navBot,keyPgDn,1f,38,10.5f,1,0);

            LinearLayout arrCol=new LinearLayout(activity); arrCol.setOrientation(LinearLayout.VERTICAL);
            rightCol.addView(arrCol,new LinearLayout.LayoutParams(0,-2,1f));
            LinearLayout arrTop=new LinearLayout(activity); arrTop.setOrientation(LinearLayout.HORIZONTAL);
            arrCol.addView(arrTop,new LinearLayout.LayoutParams(-1,-2));
            addSpacerToRow(arrTop,1f,34,0,2);
            addKeyToRow(arrTop,keyUp,1f,34,14f,1,2);
            addSpacerToRow(arrTop,1f,34,1,0);
            LinearLayout arrBot=new LinearLayout(activity); arrBot.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams arrBotLp=new LinearLayout.LayoutParams(-1,-2); arrBotLp.setMargins(0,dp(6),0,0);
            arrCol.addView(arrBot,arrBotLp);
            addKeyToRow(arrBot,keyLeft,1f,38,14f,0,2);
            addKeyToRow(arrBot,keyDown,1f,38,14f,1,2);
            addKeyToRow(arrBot,keyRight,1f,38,14f,1,0);
        } else {
            pcKeysButton.setTextSize(11.5f); androidKeyboardButton.setTextSize(11f); hideKeyboardButton.setTextSize(11f);
            pcKeysButton.setPadding(dp(9),0,dp(6),0); androidKeyboardButton.setPadding(dp(9),0,dp(6),0); hideKeyboardButton.setPadding(dp(9),0,dp(6),0);
            LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(40),1f); p1.setMargins(0,0,dp(5),0); keyboardHeader.addView(pcKeysButton,p1);
            LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(40),1f); p2.setMargins(0,0,dp(5),0); keyboardHeader.addView(androidKeyboardButton,p2);
            LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(0,dp(40),1f); keyboardHeader.addView(hideKeyboardButton,p3);

            LinearLayout fnRow1=new LinearLayout(activity); fnRow1.setOrientation(LinearLayout.HORIZONTAL);
            keyboardBar.addView(fnRow1,new LinearLayout.LayoutParams(-1,-2));
            addKeyToRow(fnRow1,keyEsc,1.25f,34,11.5f,0,6);
            for(int i=0;i<6;i++) addKeyToRow(fnRow1,keyFn[i],1f,34,11.5f,i==0?0:2,i==5?0:2);

            LinearLayout fnRow2=new LinearLayout(activity); fnRow2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams fnRow2Lp=new LinearLayout.LayoutParams(-1,-2); fnRow2Lp.setMargins(dp(6),dp(5),dp(6),0);
            keyboardBar.addView(fnRow2,fnRow2Lp);
            for(int i=6;i<12;i++) addKeyToRow(fnRow2,keyFn[i],1f,34,11.5f,i==6?0:3,i==11?0:3);

            View div1=createDivider(true);
            LinearLayout.LayoutParams d1Lp=new LinearLayout.LayoutParams(-1,dp(1)); d1Lp.setMargins(dp(2),dp(7),dp(2),dp(7));
            keyboardBar.addView(div1,d1Lp);

            LinearLayout modRow1=new LinearLayout(activity); modRow1.setOrientation(LinearLayout.HORIZONTAL);
            keyboardBar.addView(modRow1,new LinearLayout.LayoutParams(-1,-2));
            addKeyToRow(modRow1,modCtrl,1f,35,12f,0,3);
            addKeyToRow(modRow1,modWin,1f,35,12f,3,3);
            addKeyToRow(modRow1,modAlt,1f,35,12f,3,3);
            addKeyToRow(modRow1,modShift,1f,35,12f,3,3);
            addKeyToRow(modRow1,keyTab,1f,35,12f,3,0);

            LinearLayout modRow2=new LinearLayout(activity); modRow2.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams modRow2Lp=new LinearLayout.LayoutParams(-1,-2); modRow2Lp.setMargins(dp(8),dp(5),dp(8),0);
            keyboardBar.addView(modRow2,modRow2Lp);
            addKeyToRow(modRow2,keyCaps,1f,37,10f,0,4);
            addKeyToRow(modRow2,keyPrtSc,1f,37,11.5f,4,4);
            addKeyToRow(modRow2,keyScrLk,1f,37,11.5f,4,4);
            addKeyToRow(modRow2,keyPause,1f,37,11.5f,4,0);

            View div2=createDivider(true);
            LinearLayout.LayoutParams d2Lp=new LinearLayout.LayoutParams(-1,dp(1)); d2Lp.setMargins(dp(2),dp(7),dp(2),dp(7));
            keyboardBar.addView(div2,d2Lp);

            LinearLayout bottomSplit=new LinearLayout(activity); bottomSplit.setOrientation(LinearLayout.HORIZONTAL);
            keyboardBar.addView(bottomSplit,new LinearLayout.LayoutParams(-1,-2));

            LinearLayout navCol=new LinearLayout(activity); navCol.setOrientation(LinearLayout.VERTICAL);
            bottomSplit.addView(navCol,new LinearLayout.LayoutParams(0,-2,1f));
            LinearLayout navTop=new LinearLayout(activity); navTop.setOrientation(LinearLayout.HORIZONTAL);
            navCol.addView(navTop,new LinearLayout.LayoutParams(-1,-2));
            addKeyToRow(navTop,keyIns,1f,35,11.5f,0,2);
            addKeyToRow(navTop,keyHome,1f,35,11.5f,2,2);
            addKeyToRow(navTop,keyPgUp,1f,35,11.5f,2,0);
            LinearLayout navBot=new LinearLayout(activity); navBot.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams navBotLp=new LinearLayout.LayoutParams(-1,-2); navBotLp.setMargins(0,dp(5),0,0);
            navCol.addView(navBot,navBotLp);
            addKeyToRow(navBot,keyDel,1f,35,11.5f,0,2);
            addKeyToRow(navBot,keyEnd,1f,35,11.5f,2,2);
            addKeyToRow(navBot,keyPgDn,1f,35,11.5f,2,0);

            View vDiv=createDivider(false);
            LinearLayout.LayoutParams vdLp=new LinearLayout.LayoutParams(dp(1),-1);
            vdLp.setMargins(dp(8),dp(2),dp(8),dp(2));
            bottomSplit.addView(vDiv,vdLp);

            LinearLayout arrCol=new LinearLayout(activity); arrCol.setOrientation(LinearLayout.VERTICAL);
            bottomSplit.addView(arrCol,new LinearLayout.LayoutParams(0,-2,1f));
            LinearLayout arrTop=new LinearLayout(activity); arrTop.setOrientation(LinearLayout.HORIZONTAL);
            arrCol.addView(arrTop,new LinearLayout.LayoutParams(-1,-2));
            addSpacerToRow(arrTop,1f,35,0,2);
            addKeyToRow(arrTop,keyUp,1f,35,14f,2,2);
            addSpacerToRow(arrTop,1f,35,2,0);
            LinearLayout arrBot=new LinearLayout(activity); arrBot.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams arrBotLp=new LinearLayout.LayoutParams(-1,-2); arrBotLp.setMargins(0,dp(5),0,0);
            arrCol.addView(arrBot,arrBotLp);
            addKeyToRow(arrBot,keyLeft,1f,35,14f,0,2);
            addKeyToRow(arrBot,keyDown,1f,35,14f,2,2);
            addKeyToRow(arrBot,keyRight,1f,35,14f,2,0);
        }
    }
    public boolean isFullscreen() { return fullscreen; }
    private boolean canControl() { return ready&&!switchingMonitor&&!privacy&&!locked&&!viewOnly&&!destroyed; }
    public boolean blocksRemoteInput() { return !canControl(); }
    private void setVisible(boolean show) { visible=show;controls.setVisibility(show?View.VISIBLE:View.GONE);if(show&&!controlsPinned) {ui.removeCallbacks(fade);ui.postDelayed(fade,8000);} }
    private void fullscreen(boolean enabled) {
        fullscreen=enabled;prefs.edit().putBoolean("fullscreen",enabled).apply();
        if(enabled) { activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); activity.getWindow().getDecorView().setSystemUiVisibility(5894); }
        else { activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }
    }
    private void orientation() {
        String[] names={"Follow phone rotation","Lock portrait","Lock landscape"};
        int[] values={ActivityInfo.SCREEN_ORIENTATION_FULL_USER,ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE};
        int currentVal = prefs.getInt("orientation", ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        int currentIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentVal) currentIndex = i;
        }
        NyxMenuDialog.showSingleChoice(activity, "Orientation", names, currentIndex, index -> {
            prefs.edit().putInt("orientation", values[index]).apply();
            activity.setRequestedOrientation(values[index]);
        }, null);
    }
    private void keyboard() {
        if(locked||viewOnly) {toast("Unlock touch / turn off View Only to type.");return;}
        androidImeActive=false;
        setVisible(true);
        if(isLandscape()!=lastLandscape)layoutKeyboard(isLandscape());
        View imeRoot=(View)editor.getParent();
        imeRoot.setVisibility(View.VISIBLE);
        imeRoot.bringToFront();
        keyboardBar.setVisibility(View.VISIBLE);
        updateHeaderState();
        editor.setShowSoftInputOnFocus(false);
        editor.setVisibility(View.VISIBLE);
        editor.requestFocus();
        editor.onCreateInputConnection(new EditorInfo());
        InputMethodManager imm=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.restartInput(editor);
        imm.hideSoftInputFromWindow(editor.getWindowToken(),0);
        if(Build.VERSION.SDK_INT>=30&&editor.getWindowInsetsController()!=null)editor.getWindowInsetsController().hide(WindowInsets.Type.ime());
    }
    private void androidKeyboard() {
        if(locked||viewOnly) {toast("Unlock touch / turn off View Only to type.");return;}
        androidImeActive=true;
        setVisible(true);
        View imeRoot=(View)editor.getParent();
        imeRoot.setVisibility(View.VISIBLE);
        imeRoot.bringToFront();
        keyboardBar.setVisibility(View.GONE);
        updateHeaderState();
        editor.shortcutMode(modifiers!=0);
        editor.setShowSoftInputOnFocus(true);
        editor.setVisibility(View.VISIBLE);
        editor.requestFocus();
        InputMethodManager imm=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if(imm!=null)imm.restartInput(editor);
        editor.post(()->{((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);if(Build.VERSION.SDK_INT>=30&&editor.getWindowInsetsController()!=null)editor.getWindowInsetsController().show(WindowInsets.Type.ime());});
    }
    private void hideKeyboard() {
        androidImeActive=false;
        updateHeaderState();
        editor.finishComposition();
        releaseInput();
        editor.invalidateContext();
        editor.setShowSoftInputOnFocus(false);
        editor.clearFocus();
        ((InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(),0);
        if(Build.VERSION.SDK_INT>=30&&editor.getWindowInsetsController()!=null)editor.getWindowInsetsController().hide(WindowInsets.Type.ime());
        ((View)editor.getParent()).setVisibility(View.GONE);
    }
    private void clearModifiers() {
        clearingModifiers=true;
        modifiers=0;
        modifierComboUsed=false;
        for(ToggleButton button:modifierButtons)if(button.isChecked())button.setChecked(false);
        clearingModifiers=false;
        if(editor!=null){
            editor.shortcutMode(false);
            if(androidImeActive){
                InputMethodManager imm=(InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null)imm.restartInput(editor);
            }
        }
    }
    private void tapKey(int vk) { if(!canControl())return;editor.finishComposition();editor.invalidateContext();byte mods=modifiers;sendKey(vk,mods);modifierComboUsed=true;if(mods!=0)clearModifiers(); }
    private void sendKey(int vk,byte mods) {
        if(!canControl())return;
        byte active=0;
        int[][] modMap={{2,0xA2},{4,0xA4},{8,0x5B},{1,0xA0}};
        for(int[] m:modMap){
            if((mods&m[0])!=0&&vk!=m[1]){
                active|=m[0];
                conn.sendKeyboardInput((short)(0x8000|m[1]),KeyboardPacket.KEY_DOWN,active,(byte)0);
            }
        }
        conn.sendKeyboardInput((short)(0x8000|vk),KeyboardPacket.KEY_DOWN,mods,(byte)0);
        conn.sendKeyboardInput((short)(0x8000|vk),KeyboardPacket.KEY_UP,mods,(byte)0);
        for(int i=modMap.length-1;i>=0;i--){
            int[] m=modMap[i];
            if((mods&m[0])!=0&&vk!=m[1]){
                active&=~m[0];
                conn.sendKeyboardInput((short)(0x8000|m[1]),KeyboardPacket.KEY_UP,active,(byte)0);
            }
        }
    }
    private void chord(String combination) {
        if(!canControl())return;editor.finishComposition();editor.invalidateContext();
        try { KeyChord chord=KeyChord.parse(combination);sendKey(chord.key,chord.modifiers); }catch(Exception e){toast("Use a shortcut like Ctrl+Shift+M or Alt+Tab.");}
    }
    private void quickActions() {
        String[] choices={"Discord mute (configured shortcut)","F11 — browser fullscreen","Alt+Tab","Escape","Show desktop","Play / Pause","Lock Windows","Remote double-click","Copy","Paste","Select all","Paste phone clipboard","Configure custom buttons","Configure Discord shortcut","Custom 1: "+prefs.getString("custom1","Ctrl+Shift+M"),"Custom 2: "+prefs.getString("custom2","Ctrl+L"),"Custom 3: "+prefs.getString("custom3","Alt+Enter")};
        NyxMenuDialog.showActionList(activity, "Quick actions", choices, w -> {
            switch(w){case 0:chord(prefs.getString("discord","Ctrl+Shift+M"));break;case 1:chord("F11");break;case 2:chord("Alt+Tab");break;case 3:chord("Escape");break;case 4:chord("Win+D");break;case 5:media("playPause");break;case 6:media("lock");break;case 7:click((byte)1);ui.postDelayed(()->click((byte)1),90);break;case 8:chord("Ctrl+C");break;case 9:chord("Ctrl+V");break;case 10:chord("Ctrl+A");break;
                case 11:if(canControl()){android.content.ClipboardManager clip=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);if(clip.hasPrimaryClip()){editor.invalidateContext();conn.sendUtf8Text(clip.getPrimaryClip().getItemAt(0).coerceToText(activity).toString());}}break;
                case 12:NyxMenuDialog.showActionList(activity, "Choose a custom button", new String[]{"Custom 1","Custom 2","Custom 3"}, index -> customShortcut(index+1));break;case 13:shortcut(true);break;
                case 14:chord(prefs.getString("custom1","Ctrl+Shift+M"));break;case 15:chord(prefs.getString("custom2","Ctrl+L"));break;case 16:chord(prefs.getString("custom3","Alt+Enter"));break;
            }
        });
    }
    private void shortcut(boolean discord) {
        String initial = prefs.getString(discord?"discord":"custom","Ctrl+Shift+M");
        String title = discord ? "Discord shortcut — match your Discord keybind" : "Custom shortcut";
        NyxMenuDialog.showTextInput(activity, title, "Enter key combination (e.g. Ctrl+Shift+M or Alt+Tab):", initial, "Shortcut", "Save", "Cancel", text -> {
            try {
                KeyChord.parse(text);
                prefs.edit().putString(discord?"discord":"custom", text).apply();
                if (!discord) chord(text);
            } catch (Exception e) {
                toast("Invalid key combination");
            }
        });
    }
    private void customShortcut(int slot) {
        String initial = prefs.getString("custom"+slot,"Ctrl+Shift+M");
        NyxMenuDialog.showTextInput(activity, "Custom button "+slot, "Enter key combination (e.g. Ctrl+Shift+M or Alt+Tab):", initial, "Shortcut", "Save", "Cancel", text -> {
            try {
                KeyChord.parse(text);
                prefs.edit().putString("custom"+slot, text).apply();
            } catch (Exception e) {
                toast("Invalid key combination");
            }
        });
    }
    private interface Request { void run() throws Exception; }
    private void request(Request task) { network.execute(()->{try{if(api==null)throw new Exception("Companion unavailable. Return home and pair again.");task.run();}catch(Exception e){ui.post(()->{if(!destroyed){setVisible(true);state.setText(e.getMessage());}});}}); }
    private void media(String action) { if(!canControl()) {toast("Unlock controls first.");return;}request(()->{JSONObject result=api.post("media",new JSONObject().put("action",action));ui.post(()->state.setText("Windows mic muted: "+result.opt("microphoneMuted")+" · speaker muted: "+result.opt("speakerMuted")));}); }
    private void monitors() {
        if (switchingMonitor || privacy) return;
        request(() -> {
            JSONObject result = api.get("monitors");
            JSONArray list = result.getJSONArray("monitors");
            String[] names = new String[list.length()];
            int selected = -1;
            String active = result.optString("active", result.getString("selected"));
            for (int i = 0; i < list.length(); i++) {
                JSONObject monitor = list.getJSONObject(i);
                names[i] = monitor.getString("name") + " · " + monitor.getInt("width") + " × " + monitor.getInt("height");
                if (monitor.getString("id").equals(active)) selected = i;
            }
            int current = selected;
            ui.post(() -> {
                if (destroyed || switchingMonitor) return;
                NyxMenuDialog.showSingleChoice(activity, "Mirror existing monitor", names, current, index -> {
                    if (index != current) switchMonitor(list.optJSONObject(index));
                }, null);
            });
        });
    }
    private void switchMonitor(JSONObject target) {
        if (target == null || switchingMonitor || !ready) return;
        switchingMonitor = true;
        hideKeyboard();
        setVisible(true);
        state.setText("Switching to " + target.optString("name") + "…");
        network.execute(() -> {
            try {
                JSONObject command = api.post("monitor/live", new JSONObject().put("id", target.getString("id")));
                int index = command.getInt("index");
                if (index >= 0) ui.post(() -> sendLiveMonitorShortcut(index, command.optString("ticket"), target));
                else ui.post(() -> finishMonitorSwitch(target, null));
            } catch (Exception e) {
                ui.post(() -> finishMonitorSwitch(null, e.getMessage()));
            }
        });
    }
    private void sendLiveMonitorShortcut(int index, String ticket, JSONObject target) {
        if (destroyed || !ready || !switchingMonitor) return;
        // Sunshine intercepts Ctrl+Alt+Shift+Fn on the streaming input channel.
        int[] keys = {0x11, 0x12, 0x10, 0x70 + index};
        for (int key : keys) conn.sendKeyboardInput((short)(0x8000 | key), KeyboardPacket.KEY_DOWN, (byte)0, (byte)0);
        for (int i = keys.length - 1; i >= 0; i--)
            conn.sendKeyboardInput((short)(0x8000 | keys[i]), KeyboardPacket.KEY_UP, (byte)0, (byte)0);
        network.execute(() -> {
            try {
                api.post("monitor/live/confirm", new JSONObject().put("ticket", ticket));
                ui.post(() -> finishMonitorSwitch(target, null));
            } catch (Exception e) {
                ui.post(() -> finishMonitorSwitch(null, e.getMessage()));
            }
        });
    }
    private void finishMonitorSwitch(JSONObject target, String error) {
        if (destroyed || !switchingMonitor) return;
        switchingMonitor = false;
        if (target == null) {
            toast(error == null ? "Couldn't switch monitors." : error);
            state.setText("Monitor switch failed. The stream is still open.");
            return;
        }
        captureWidth = target.optInt("width", captureWidth);
        captureHeight = target.optInt("height", captureHeight);
        prefs.edit().putString("monitor", target.optString("id")).putInt("monitorWidth", captureWidth)
                .putInt("monitorHeight", captureHeight).apply();
        resetZoom();
        setVisible(true);
        state.setText("Mirroring " + target.optString("name"));
    }
    private void restoreLiveMonitor(int attempts) {
        if (destroyed || !ready || switchingMonitor) return;
        request(() -> {
            JSONObject listing = api.get("monitors");
            if (!listing.has("active")) return;
            if (listing.isNull("active")) {
                if (attempts > 1) ui.postDelayed(() -> restoreLiveMonitor(attempts - 1), 500);
                return;
            }
            String selected = listing.getString("selected");
            String active = listing.optString("active", selected);
            JSONArray monitors = listing.getJSONArray("monitors");
            for (int i = 0; i < monitors.length(); i++) {
                JSONObject monitor = monitors.getJSONObject(i);
                if (selected.equals(monitor.getString("id"))) {
                    if (!selected.equals(active)) ui.post(() -> { if (!destroyed) switchMonitor(monitor); });
                    return;
                }
            }
        });
    }
    private void rememberSelectedMonitor() throws Exception {
        JSONObject result = api.get("monitors");
        String id = result.getString("selected");
        JSONArray monitors = result.getJSONArray("monitors");
        for (int i = 0; i < monitors.length(); i++) {
            JSONObject monitor = monitors.getJSONObject(i);
            if (!id.equals(monitor.getString("id"))) continue;
            prefs.edit().putString("monitor", id).putInt("monitorWidth", monitor.getInt("width"))
                    .putInt("monitorHeight", monitor.getInt("height")).apply();
            return;
        }
        throw new java.io.IOException("The PC has no selected monitor.");
    }
    private void privacy() {
        if (switchingMonitor) return;
        privacy = !privacy;
        if (privacy) {
            hideKeyboard();
            releaseInput();
            privacyScreen.setVisibility(View.VISIBLE);
            privacyScreen.bringToFront();
            controls.bringToFront();
            if (unlock != null) unlock.bringToFront();
            com.limelight.binding.audio.AndroidAudioRenderer.setPrivacyMuted(true);
            state.setText("Privacy Pause — video and audio stopped. Tap Privacy to resume.");
            setVisible(true);
        } else {
            privacyScreen.setVisibility(View.GONE);
            com.limelight.binding.audio.AndroidAudioRenderer.setPrivacyMuted(false);
            if (measuredStatistics != null && !measuredStatistics.isEmpty()) {
                statistics(measuredStatistics);
            } else {
                state.setText("Connected · " + (trackpad ? "Trackpad" : "Direct touch") + " · double-tap controls");
            }
            setVisible(true);
        }
    }
    private void reconnect(){
        if(switchingMonitor)return;
        intentional=true;
        reconnecting=true;
        connectionUnavailable=false;
        releaseInput();
        setVisible(true);
        state.setText(CompanionClient.STATUS_RECONNECTING + "…");
        network.execute(()->{
            try{
                CompanionClient resolved = resolveReachableClient();
                JSONObject s = resolved.get("status");
                if (s.optBoolean("privacy")) resolved.post("resume", new JSONObject());
                rememberSelectedMonitor();
                ui.post(activity::bedReconnect);
            }catch(Exception e){
                ui.post(()->{
                    intentional=false;
                    reconnecting=false;
                    if(!destroyed) {
                        setVisible(true);
                        state.setText(e.getMessage());
                    }
                });
            }
        });
    }
    private void audioMenu() {
        boolean earpieceSupported = com.limelight.binding.audio.AndroidAudioRenderer.hasEarpiece(activity);
        if (!earpieceSupported && prefs.getBoolean("earpieceMode", false)) {
            prefs.edit().putBoolean("earpieceMode", false).apply();
        }
        boolean currentEarpiece = earpieceSupported && prefs.getBoolean("earpieceMode", false);
        String[] modes = {
            "Speaker Mode — Phone loudspeaker",
            earpieceSupported ? "Earpiece Mode — Call-style earpiece" : "Earpiece Mode (Unsupported on this device)"
        };
        NyxMenuDialog.showSingleChoice(activity, "Phone audio output", modes, currentEarpiece ? 1 : 0, which -> {
            if (which == 1 && !earpieceSupported) {
                toast("Earpiece playback is not supported on this device.");
                return;
            }
            boolean useEarpiece = (which == 1);
            prefs.edit().putBoolean("earpieceMode", useEarpiece).putBoolean("phoneAudio", true).apply();
            if (!useEarpiece) com.limelight.binding.audio.AndroidAudioRenderer.restoreNormalAudioRouting(activity);
            toast(useEarpiece ? "Earpiece Mode active" : "Speaker Mode active");
        }, index -> index == 0 || earpieceSupported);
    }
    private void options() {
        String[] choices={"Windows volume +","Windows volume −","Windows speaker mute","Windows microphone mute","Previous track","Next track","Phone audio on / off","Brightness","Cursor sensitivity","Fit / reset zoom","Locate cursor","View Only on / off","Pin controls on / off","Picture in picture","Battery mode (720p / 30)","Phone volume long-press controls PC","Prevent PC sleep while viewing","Keep phone awake while viewing","Automatic stream quality","Phone playback volume","Connection details","Click indicator on / off","Remote Access"};
        NyxMenuDialog.showActionList(activity, "Viewing options", choices, w -> {
            switch(w){case 0:media("volumeUp");break;case 1:media("volumeDown");break;case 2:media("speakerMute");break;case 3:media("microphoneMute");break;case 4:media("previous");break;case 5:media("next");break;
                case 6:boolean audio=!prefs.getBoolean("phoneAudio",true);prefs.edit().putBoolean("phoneAudio",audio).apply();toast(audio?"Phone audio enabled":"Phone audio disabled");break;
                case 7:slider("Window brightness",(int)(Math.max(0,prefs.getFloat("brightness",0.5f))*100),100,p->{float value=Math.max(.02f,p/100f);setBrightness(value);prefs.edit().putFloat("brightness",value).apply();},()->{setBrightness(-1);prefs.edit().putFloat("brightness",-1).apply();});break;
                case 8:slider("Cursor sensitivity",(int)(sensitivity*25),100,p->{sensitivity=Math.max(.2f,p/25f);prefs.edit().putFloat("sensitivity",sensitivity).apply();},null);break;
                case 9:resetZoom();break;
                case 10:request(()->{JSONObject cursor=api.get("cursor");ui.post(()->{if(!cursor.optBoolean("onMonitor")){toast("The Windows cursor is on another monitor.");return;}gestures.mouseX=video.getX()+video.getWidth()/2f+(float)(cursor.optDouble("x")-.5)*video.getWidth()*zoom;gestures.mouseY=video.getY()+video.getHeight()/2f+(float)(cursor.optDouble("y")-.5)*video.getHeight()*zoom;gestures.locate=true;gestures.invalidate();ui.postDelayed(()->{gestures.locate=false;gestures.invalidate();},1800);});});break;
                case 11:hideKeyboard();releaseInput();viewOnly=!viewOnly;toast(viewOnly?"View Only enabled":"Remote control enabled");break;
                case 12:controlsPinned=!controlsPinned;prefs.edit().putBoolean("controlsPinned",controlsPinned).apply();setVisible(true);break;
                case 13:hideKeyboard();if(Build.VERSION.SDK_INT>=26){setVisible(false);activity.enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new android.util.Rational(Math.max(1,video.getWidth()),Math.max(1,video.getHeight()))).build());}break;
                case 14:prefs.edit().putInt("quality",0).apply();reconnect();break;
                case 15:boolean enabled=!prefs.getBoolean("volumeLongPress",false);prefs.edit().putBoolean("volumeLongPress",enabled).apply();toast(enabled?"Long-press volume controls PC; short presses stay local":"Ordinary phone volume buttons restored");break;
                case 16:boolean prevent=!prefs.getBoolean("preventPcSleep",false);prefs.edit().putBoolean("preventPcSleep",prevent).apply();toast(prevent?"PC sleep prevention enabled during active viewing":"PC normal sleep policy restored");break;
                case 17:boolean awake=!prefs.getBoolean("phoneAwake",true);prefs.edit().putBoolean("phoneAwake",awake).apply();if(awake)activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);break;
                case 18:boolean auto=!prefs.getBoolean("autoQuality",true);prefs.edit().putBoolean("autoQuality",auto).apply();toast(auto?"Automatic quality enabled":"Manual quality enabled");break;
                case 19:slider("Phone stream volume",(int)(prefs.getFloat("phoneVolume",1f)*100),100,p->prefs.edit().putFloat("phoneVolume",p/100f).apply(),null);break;
                case 20:
                    String details = "Connection Status: " + liveConnectionStatus()
                            + "\nConnection Mode: " + CompanionClient.modeName(prefs.getInt("connectionMode", 0))
                            + "\nActive Endpoint: " + activeHost
                            + "\n\n" + measuredStatistics;
                    NyxMenuDialog.showMessage(activity, "Measured stream statistics", details, "Close");
                    break;
                case 21:boolean feedback=!prefs.getBoolean("clickIndicator",true);prefs.edit().putBoolean("clickIndicator",feedback).apply();toast(feedback?"Click feedback enabled":"Click feedback disabled");break;
                case 22:remoteAccessMenu();break;
            }
        });
    }
    private String liveConnectionStatus() {
        if (reconnecting) return CompanionClient.STATUS_RECONNECTING;
        if (ready) return connectedRemotely ? CompanionClient.STATUS_CONNECTED_REMOTELY : CompanionClient.STATUS_CONNECTED_LOCALLY;
        if (connectionUnavailable) return CompanionClient.STATUS_UNAVAILABLE;
        return CompanionClient.STATUS_CONNECTING;
    }
    private void refreshSavedEndpoints() {
        if (pcFingerprint == null || pcFingerprint.isEmpty()) return;
        SharedPreferences pairings = activity.getSharedPreferences("nyxlink-pcs", Context.MODE_PRIVATE);
        String raw = pairings.getString(pcFingerprint, null);
        if (raw == null) return;
        try {
            JSONObject obj = new JSONObject(raw);
            String savedLocal = obj.optString("address", "").trim();
            String savedRemote = obj.optString("remoteAddress", "").trim();
            if (!savedLocal.isEmpty() && !CompanionClient.isRemotePrivateAddress(savedLocal)) localHost = savedLocal;
            if (!savedRemote.isEmpty()) remoteHost = savedRemote;
        } catch (Exception ignored) { }
    }
    private String remoteConfigSummary() {
        refreshSavedEndpoints();
        String pcName = activity.getIntent().getStringExtra(Game.EXTRA_PC_NAME);
        if (pcName == null || pcName.isEmpty()) pcName = "Paired PC";
        if (remoteHost != null && !remoteHost.trim().isEmpty()) {
            return "Remote Connectivity: Configured — " + pcName + " (" + remoteHost + ")";
        }
        return "Remote Connectivity: Not configured";
    }
    private void remoteAccessMenu() {
        NyxMenuDialog.showStreamingSettings(
                activity,
                prefs,
                false,
                liveConnectionStatus(),
                this::remoteConfigSummary,
                this::configureRemotePcInStream,
                api,
                () -> {
                    boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
                    int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
                    if (connectedRemotely && (!remoteEnabled || mode == CompanionClient.MODE_LOCAL_ONLY)) {
                        toast("Remote Access disabled — disconnecting remote stream.");
                        if (localHost != null && !localHost.isEmpty() && !CompanionClient.isRemotePrivateAddress(localHost)) {
                            connectionLost("Switching to Local Only");
                        } else {
                            intentional = true;
                            releaseInput();
                            activity.finish();
                        }
                    }
                }
        );
    }
    private void configureRemotePcInStream() {
        refreshSavedEndpoints();
        String pcName = activity.getIntent().getStringExtra(Game.EXTRA_PC_NAME);
        if (pcName == null || pcName.isEmpty()) pcName = "Paired PC";
        final String displayPcName = pcName;
        NyxMenuDialog.showTextInput(
                activity,
                "Remote Access — " + displayPcName,
                "Enter the private remote IPv4 address for " + displayPcName + " (for example, its Tailscale address 100.x.y.z). Leave empty to clear.",
                remoteHost,
                "100.x.y.z (Tailscale / private IP)",
                "Save",
                "Cancel",
                input -> {
                    String trimmed = input == null ? "" : input.trim();
                    network.execute(() -> {
                        try {
                            if (!trimmed.isEmpty()) CompanionClient.requireLocal(trimmed);
                            remoteHost = trimmed;
                            activity.getIntent().putExtra("com.limelight.NyxLinkRemoteHost", remoteHost);
                            if (pcFingerprint != null && !pcFingerprint.isEmpty()) {
                                SharedPreferences pairings = activity.getSharedPreferences("nyxlink-pcs", Context.MODE_PRIVATE);
                                String existing = pairings.getString(pcFingerprint, null);
                                if (existing != null) {
                                    JSONObject data = new JSONObject(existing);
                                    data.put("remoteAddress", remoteHost);
                                    pairings.edit().putString(pcFingerprint, data.toString()).commit();
                                }
                            }
                            ui.post(() -> toast(remoteHost.isEmpty() ? "Cleared remote address." : "Saved remote address: " + remoteHost));
                        } catch (Exception e) {
                            ui.post(() -> toast("Invalid remote address: " + e.getMessage()));
                        }
                    });
                }
        );
    }
    private void registerNetworkMonitor() {
        try {
            connectivityManager = (android.net.ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) return;
            activeNetwork = connectivityManager.getActiveNetwork();
            networkCallback = new android.net.ConnectivityManager.NetworkCallback() {
                @Override public void onAvailable(android.net.Network network) {
                    ui.removeCallbacks(networkChangeRunnable);
                    ui.postDelayed(networkChangeRunnable, 1500);
                }
                @Override public void onLost(android.net.Network network) {
                    ui.removeCallbacks(networkChangeRunnable);
                    ui.postDelayed(networkChangeRunnable, 1500);
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
        } catch (Exception ignored) { }
    }
    private void handleDebouncedNetworkChange() {
        if (destroyed || intentional || privacy || switchingMonitor) return;
        android.net.Network current = connectivityManager != null ? connectivityManager.getActiveNetwork() : null;
        if (!ready) {
            if ((reconnecting || connectionUnavailable) && current != null) {
                retry = 0;
                ui.removeCallbacks(recoveryStep);
                attemptRecovery("Network changed");
            }
            return;
        }
        if (current == null) {
            connectionLost("Network disconnected");
            return;
        }
        if (!java.util.Objects.equals(current, activeNetwork)) {
            activeNetwork = current;
            network.execute(() -> {
                try {
                    refreshSavedEndpoints();
                    boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
                    int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
                    if (connectedRemotely && (!remoteEnabled || mode == CompanionClient.MODE_LOCAL_ONLY)) {
                        ui.post(() -> { if (!destroyed && !intentional) connectionLost("Remote Access disabled"); });
                        return;
                    }
                    // Check if the current active endpoint is still reachable
                    if (api == null) throw new Exception("Disconnected");
                    api.get("status", 1500);
                    // In Automatic mode, if we are currently on remote and local LAN is now reachable, switch to direct local
                    if (remoteEnabled && mode == CompanionClient.MODE_AUTOMATIC && connectedRemotely
                            && localHost != null && !localHost.isEmpty() && !localHost.equals(activeHost)
                            && !CompanionClient.isRemotePrivateAddress(localHost)) {
                        try {
                            CompanionClient localProbe = new CompanionClient(activity, localHost, serverCert, identity);
                            localProbe.get("status", 1200);
                            ui.post(() -> { if (!destroyed && !intentional && !privacy) connectionLost("Switching to local Wi-Fi"); });
                        } catch (Exception ignored) { }
                    }
                } catch (Exception unreachable) {
                    ui.post(() -> { if (!destroyed && !intentional && !privacy) connectionLost("Network switched"); });
                }
            });
        }
    }
    private CompanionClient resolveReachableClient() throws Exception {
        refreshSavedEndpoints();
        boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
        int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
        String localCandidate = (localHost != null && !localHost.trim().isEmpty()) ? localHost.trim() : activeHost;
        String remoteCandidate = (remoteHost != null && !remoteHost.trim().isEmpty())
                ? remoteHost.trim()
                : (CompanionClient.isRemotePrivateAddress(activeHost) ? activeHost : "");

        if (!remoteEnabled || mode == CompanionClient.MODE_LOCAL_ONLY) {
            if (localCandidate.isEmpty() || CompanionClient.isRemotePrivateAddress(localCandidate)) {
                throw new java.io.IOException("Local network unreachable and Remote Access is disabled.");
            }
            CompanionClient localClient = new CompanionClient(activity, localCandidate, serverCert, identity);
            localClient.get("status", 2500);
            updateActiveEndpoint(localClient, localCandidate, false);
            return localClient;
        }
        if (mode == CompanionClient.MODE_REMOTE_ACCESS) {
            if (remoteCandidate.isEmpty()) {
                throw new java.io.IOException("No remote address configured for this PC.");
            }
            CompanionClient remoteClient = new CompanionClient(activity, remoteCandidate, serverCert, identity);
            remoteClient.get("status", 4000);
            updateActiveEndpoint(remoteClient, remoteCandidate, true);
            return remoteClient;
        }
        // Automatic mode: try direct local first, then fall back to remote
        Exception lastError = null;
        if (!localCandidate.isEmpty() && !CompanionClient.isRemotePrivateAddress(localCandidate)) {
            try {
                CompanionClient localClient = new CompanionClient(activity, localCandidate, serverCert, identity);
                JSONObject st = localClient.get("status", 1500);
                String reportedRemote = st.optString("remoteAddress", "").trim();
                if (!reportedRemote.isEmpty() && remoteCandidate.isEmpty()) {
                    remoteHost = reportedRemote;
                }
                updateActiveEndpoint(localClient, localCandidate, false);
                return localClient;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (!remoteCandidate.isEmpty()) {
            try {
                CompanionClient remoteClient = new CompanionClient(activity, remoteCandidate, serverCert, identity);
                remoteClient.get("status", 4000);
                updateActiveEndpoint(remoteClient, remoteCandidate, true);
                return remoteClient;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (lastError != null) throw lastError;
        throw new java.io.IOException("PC is currently unreachable.");
    }
    private void updateActiveEndpoint(CompanionClient client, String host, boolean isRemote) {
        this.api = client;
        this.activeHost = host;
        this.connectedRemotely = isRemote;
        activity.getIntent().putExtra(Game.EXTRA_HOST, host);
        activity.getIntent().putExtra("com.limelight.NyxLinkRemoteSession", isRemote);
        if (client != null) {
            NyxReportingManager.flushPendingQueue(activity, client);
        }
    }
    private void cancelReconnection() {
        reconnecting = false;
        connectionUnavailable = true;
        retry = 99;
        ui.removeCallbacks(recoveryStep);
        if (cancelReconnectButton != null) cancelReconnectButton.setVisibility(View.GONE);
        setVisible(true);
        state.setText(CompanionClient.STATUS_UNAVAILABLE + " · Reconnection cancelled. Tap Reconnect or Disconnect.");
    }
    private interface Slider {void changed(int value);}
    private void resetZoom(){zoom=1;video.setScaleX(1);video.setScaleY(1);video.setTranslationX(0);video.setTranslationY(0);}
    private void slider(String label,int value,int max,Slider action,Runnable reset){
        NyxMenuDialog.showSlider(activity, label, value, max, "Done", reset != null ? "System default" : null, action::changed, reset);
    }
    private void setBrightness(float value){WindowManager.LayoutParams p=activity.getWindow().getAttributes();p.screenBrightness=value;activity.getWindow().setAttributes(p);}
    public void connectionReady(){
        ready=true;reconnecting=false;connectionUnavailable=false;retry=0;intentional=false;
        if(cancelReconnectButton!=null)cancelReconnectButton.setVisibility(View.GONE);
        if(connectivityManager!=null)activeNetwork=connectivityManager.getActiveNetwork();
        String statusPrefix = connectedRemotely ? CompanionClient.STATUS_CONNECTED_REMOTELY : CompanionClient.STATUS_CONNECTED_LOCALLY;
        state.setText(statusPrefix+" · "+(trackpad?"Trackpad":"Direct touch")+" · double-tap controls");
        ui.postDelayed(()->restoreLiveMonitor(5),1000);
    }
    public void connectionLost(String why){
        ready=false;switchingMonitor=false;releaseInput();
        if(destroyed||intentional||privacy)return;
        reconnecting=true;connectionUnavailable=false;
        activity.bedStop();
        setVisible(true);
        if(cancelReconnectButton!=null)cancelReconnectButton.setVisibility(View.VISIBLE);
        state.setText(CompanionClient.STATUS_RECONNECTING+" · "+why+"…");
        attemptRecovery(why);
    }
    private void attemptRecovery(String why){
        if(destroyed||intentional||privacy)return;
        lastRecoveryWhy = why;
        if(retry++>=6){
            reconnecting=false;
            connectionUnavailable=true;
            if(cancelReconnectButton!=null)cancelReconnectButton.setVisibility(View.GONE);
            state.setText(CompanionClient.STATUS_UNAVAILABLE+" · "+why+" · tap Reconnect or return home.");
            return;
        }
        reconnecting=true;
        if(cancelReconnectButton!=null)cancelReconnectButton.setVisibility(View.VISIBLE);
        state.setText(CompanionClient.STATUS_RECONNECTING+" ("+retry+"/6) · "+why+"…");
        ui.removeCallbacks(recoveryStep);
        ui.postDelayed(recoveryStep, Math.min(8000, retry * 1500L));
    }
    private void executeRecoveryStep() {
        if (destroyed || intentional || privacy || !reconnecting) return;
        network.execute(() -> {
            try {
                CompanionClient resolved = resolveReachableClient();
                JSONObject s = resolved.get("status", 3000);
                if (s.optBoolean("privacy")) {
                    ui.post(() -> {
                        reconnecting = false;
                        if (cancelReconnectButton != null) cancelReconnectButton.setVisibility(View.GONE);
                        privacy = true;
                        privacyScreen.setVisibility(View.VISIBLE);
                        state.setText("Privacy Pause — tap Privacy to resume.");
                    });
                    return;
                }
                if (!s.optBoolean("running")) throw new Exception("PC server stopped");
                rememberSelectedMonitor();
                ui.post(() -> {
                    if (!destroyed && !intentional && !privacy && reconnecting) {
                        reconnecting = false;
                        if (cancelReconnectButton != null) cancelReconnectButton.setVisibility(View.GONE);
                        activity.bedReconnect();
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    if (!destroyed && !intentional && !privacy && reconnecting) {
                        attemptRecovery(lastRecoveryWhy);
                    }
                });
            }
        });
    }
    public void releaseInput(){if(gestures!=null)gestures.release();clearModifiers();for(int key:new int[]{0x10,0x11,0x12,0x5B,0x5C,0xA0,0xA1,0xA2,0xA3,0xA4,0xA5})conn.sendKeyboardInput((short)(0x8000|key),KeyboardPacket.KEY_UP,(byte)0,(byte)0);}
    public void destroy(){
        releaseInput();destroyed=true;reconnecting=false;
        ui.removeCallbacksAndMessages(null);
        if(connectivityManager!=null&&networkCallback!=null){try{connectivityManager.unregisterNetworkCallback(networkCallback);}catch(Exception ignored){}}
        com.limelight.binding.audio.AndroidAudioRenderer.setPrivacyMuted(false);
        com.limelight.binding.audio.AndroidAudioRenderer.restoreNormalAudioRouting(activity);
        if(api!=null)network.execute(()->{try{api.post("session",new JSONObject().put("active",false).put("preventSleep",false));}catch(Exception ignored){}});
        network.shutdown();
    }
    public void pipChanged(boolean pip){if(pip){hideKeyboard();setVisible(false);unlock.setVisibility(View.GONE);}else{setVisible(true);unlock.setVisibility(locked?View.VISIBLE:View.GONE);}}
    public boolean volumeKey(KeyEvent event){int key=event.getKeyCode();if(!prefs.getBoolean("volumeLongPress",false)||(key!=KeyEvent.KEYCODE_VOLUME_UP&&key!=KeyEvent.KEYCODE_VOLUME_DOWN))return false;if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0){volumeDirection=key==KeyEvent.KEYCODE_VOLUME_UP?1:-1;volumeHeld=false;ui.postDelayed(volumeLongPress,ViewConfiguration.getLongPressTimeout());}else if(event.getAction()==KeyEvent.ACTION_UP){ui.removeCallbacks(volumeLongPress);if(!volumeHeld)((android.media.AudioManager)activity.getSystemService(Context.AUDIO_SERVICE)).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,volumeDirection,android.media.AudioManager.FLAG_SHOW_UI);}return true;}
    public void qualityStatus(boolean poor){long now=SystemClock.elapsedRealtime();if(!poor){poorSince=0;poorGeneration++;return;}if(poorSince==0){poorSince=now;long generation=++poorGeneration;ui.postDelayed(()->{if(generation==poorGeneration&&poorSince!=0)qualityStatus(true);},6500);}if(prefs.getBoolean("autoQuality",true)&&now-poorSince>6000&&now-lastQualityChange>30000&&prefs.getInt("quality",0)!=0){lastQualityChange=now;prefs.edit().putInt("quality",0).apply();toast("Connection unstable — reconnecting at 720p / 30 FPS");reconnect();}}
    public void statistics(String measured){measuredStatistics=measured;if(ready&&!privacy&&!switchingMonitor){String[] lines=measured.split("\n");String brief=lines.length>0?lines[0]:"Connected";for(String line:lines)if(line.startsWith("Average network latency:"))brief+="\n"+line;state.setText(brief);}}

    private void click(byte button){if(canControl()){editor.finishComposition();editor.invalidateContext();conn.sendMouseButtonDown(button);conn.sendMouseButtonUp(button);}}
    private final class GestureSurface extends View {
        final GestureDetector detector;
        final int slop;
        float x,y,downX,downY,secondX,secondY,rightClickX,rightClickY;
        float lastDistance,pinchStartDistance,lastCenterY,lastCenterX,mouseX,mouseY;
        boolean drag,moved,multi,pinching,locate,controlTap,longAction,twoFingerTap,showFinger;
        long clickedUntil;
        final Paint paint=new Paint(3);
        GestureSurface(Context c){super(c);setContentDescription("Remote desktop. Double-tap to hide or restore controls.");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            slop=ViewConfiguration.get(c).getScaledTouchSlop();
            detector=new GestureDetector(c,new GestureDetector.SimpleOnGestureListener(){
                public boolean onDown(MotionEvent e){return true;}
                public boolean onDoubleTap(MotionEvent e){release();controlTap=true;boolean show=!visible;if(!show)hideKeyboard();setVisible(show);return true;}
                public boolean onSingleTapConfirmed(MotionEvent e){if(!moved&&!multi&&!longAction&&canControl()){if(trackpad||position(e.getX(),e.getY())){click((byte)1);if(prefs.getBoolean("clickIndicator",true)){downX=e.getX();downY=e.getY();clickedUntil=SystemClock.elapsedRealtime()+200;invalidate();postInvalidateDelayed(210);}}}return true;}
                public void onLongPress(MotionEvent e){
                    if(multi||!canControl()||(!trackpad&&!position(x,y)))return;
                    editor.finishComposition();
                    editor.invalidateContext();
                    conn.sendMouseButtonDown((byte)1);
                    drag=true;
                    moved=longAction=true;
                }
            });
        }
        void release(){
            if(drag)conn.sendMouseButtonUp((byte)1);
            drag=false;
            showFinger=false;
            invalidate();
        }
        boolean position(float px,float py){
            // Video and overlay share a parent. Translate into the gesture surface's coordinates
            // before removing the video pan and centered scale.
            float left=video.getX()-layer.getX()-getX();
            float top=video.getY()-layer.getY()-getY();
            float[] mapped=ViewportMapper.map(px,py,left,top,video.getWidth(),video.getHeight(),zoom);
            if(mapped==null||!ViewportMapper.insideCapture(mapped[0],mapped[1],activity.bedStreamWidth(),activity.bedStreamHeight(),captureWidth,captureHeight))return false;
            mouseX=px;mouseY=py;
            conn.sendMousePosition((short)(mapped[0]*30000),(short)(mapped[1]*30000),(short)30000,(short)30000);
            return true;
        }
        public boolean onTouchEvent(MotionEvent e){
            if(Build.VERSION.SDK_INT>=26&&activity.isInPictureInPictureMode())return true;
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                requestUnbufferedDispatch(e);
                x=downX=e.getX();y=downY=e.getY();
                moved=multi=pinching=controlTap=longAction=twoFingerTap=false;
            }
            detector.onTouchEvent(e); // Always runs before Touch Lock / View Only filtering.
            if(controlTap||privacy)return true;
            if(action==MotionEvent.ACTION_CANCEL){release();twoFingerTap=false;return true;}
            if(e.getPointerCount()>2){multi=true;twoFingerTap=false;release();return true;}
            if(e.getPointerCount()==2){
                if(action==MotionEvent.ACTION_POINTER_DOWN){
                    twoFingerTap=!multi&&!moved&&!longAction&&canControl();
                    multi=true;
                    pinching=false;
                    release();
                    rightClickX=e.getX(0);rightClickY=e.getY(0);
                    secondX=e.getX(1);secondY=e.getY(1);
                    lastDistance=pinchStartDistance=distance(e);
                    lastCenterX=(rightClickX+secondX)/2;
                    lastCenterY=(rightClickY+secondY)/2;
                } else if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_POINTER_UP) {
                    if(Math.hypot(e.getX(0)-rightClickX,e.getY(0)-rightClickY)>slop
                            ||Math.hypot(e.getX(1)-secondX,e.getY(1)-secondY)>slop)twoFingerTap=false;
                    if(action==MotionEvent.ACTION_MOVE&&!twoFingerTap)moveTwoFingers(e);
                    if(action==MotionEvent.ACTION_POINTER_UP){x=e.getX(1-e.getActionIndex());y=e.getY(1-e.getActionIndex());}
                }
                return true;
            }
            float nextX=e.getX(),nextY=e.getY();
            if(multi){
                if(Math.hypot(nextX-x,nextY-y)>slop)twoFingerTap=false;
                if(action==MotionEvent.ACTION_UP&&twoFingerTap&&canControl()
                        &&e.getEventTime()-e.getDownTime()<ViewConfiguration.getLongPressTimeout()){
                    if(trackpad||position(rightClickX,rightClickY))click((byte)3);
                }
                return true;
            }
            if(canControl()){
                if(action==MotionEvent.ACTION_MOVE&&Math.hypot(nextX-downX,nextY-downY)>slop)moved=true;
                if(trackpad){
                    if(action==MotionEvent.ACTION_MOVE&&(moved||drag))
                        conn.sendMouseMove((short)((nextX-x)*sensitivity),(short)((nextY-y)*sensitivity));
                }else{
                    showFinger=position(nextX,nextY)&&action!=MotionEvent.ACTION_UP;
                    invalidate();
                }
                if(action==MotionEvent.ACTION_MOVE){x=nextX;y=nextY;}
            }
            if(action==MotionEvent.ACTION_UP)release();
            return true;
        }
        private float distance(MotionEvent e){return (float)Math.hypot(e.getX(1)-e.getX(0),e.getY(1)-e.getY(0));}
        private void moveTwoFingers(MotionEvent e){
            float distance=distance(e);
            float centerX=(e.getX(0)+e.getX(1))/2,centerY=(e.getY(0)+e.getY(1))/2;
            if(Math.abs(distance-pinchStartDistance)>dp(6))pinching=true;
            float prevZoom=zoom;
            if(!editor.isShown()&&pinching){
                zoom=Math.max(1,Math.min(4,zoom*distance/Math.max(1,lastDistance)));
                video.setScaleX(zoom);video.setScaleY(zoom);
            }
            if(!editor.isShown()&&zoom>1){
                float ratio=zoom/Math.max(1f,prevZoom);
                float vcx=(video.getLeft()-layer.getX()-getX())+video.getWidth()/2f+video.getTranslationX();
                float vcy=(video.getTop()-layer.getY()-getY())+video.getHeight()/2f+video.getTranslationY();
                float nextTx=video.getTranslationX()+(centerX-lastCenterX)-(ratio-1f)*(lastCenterX-vcx);
                float nextTy=video.getTranslationY()+(centerY-lastCenterY)-(ratio-1f)*(lastCenterY-vcy);
                float limitX=video.getWidth()*(zoom-1)/2,limitY=video.getHeight()*(zoom-1)/2;
                video.setTranslationX(Math.max(-limitX,Math.min(limitX,nextTx)));
                video.setTranslationY(Math.max(-limitY,Math.min(limitY,nextTy)));
            }else if(!pinching&&canControl())conn.sendMouseHighResScroll((short)((centerY-lastCenterY)*3));
            else if(zoom==1){video.setTranslationX(0);video.setTranslationY(0);}
            lastDistance=distance;lastCenterX=centerX;lastCenterY=centerY;
        }
        protected void onDraw(Canvas canvas){super.onDraw(canvas);if(locate||showFinger){paint.setColor(Color.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawCircle(mouseX,mouseY,dp(locate?35:9),paint);}if(SystemClock.elapsedRealtime()<clickedUntil){paint.setColor(0x9933DDFF);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(downX,downY,dp(12),paint);}}
        public boolean performClick(){setVisible(true);return super.performClick();}
    }
}
