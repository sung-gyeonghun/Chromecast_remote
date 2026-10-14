package com.morluto.remocon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.morluto.remocon.protocol.RemoteClient;
import com.morluto.remocon.protocol.AppLaunch;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public final class MainActivity extends Activity implements RemoteClient.Listener, DeviceDiscovery.Listener {
    private int BG, CARD, INK, MUTED, BLUE, BUTTON, BORDER;
    private boolean dark;
    private final ArrayList<View> controls = new ArrayList<>();
    private final LinkedHashMap<String,String> devices = new LinkedHashMap<>();
    private RemoteClient remote;
    private DeviceDiscovery discovery;
    private SharedPreferences prefs;
    private TextView deviceName, status, badge;
    private Button connectButton;
    private AlertDialog deviceDialog, pairingDialog, keyboardDialog;
    private LinearLayout deviceList;
    private String host = "", name = "Chromecast with Google TV";
    private String pairingError = "";
    private boolean connected, busy, started;

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("remocon-ui", MODE_PRIVATE);
        boolean systemDark=(getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        dark=prefs.getBoolean("dark",systemDark);applyPalette();
        super.onCreate(state);
        host = prefs.getString("host", ""); name = prefs.getString("name", name);
        remote = new RemoteClient(getApplicationContext(), this);
        discovery = new DeviceDiscovery(this, this);
        buildUi();
    }
    @Override protected void onStart() { super.onStart(); started = true; if (!host.isEmpty() && remote.isPaired(host)) connectTo(host, name, false); }
    @Override protected void onStop() {
        started = false; discovery.stop(); remote.disconnect(); connected = false; busy = false;
        if (deviceDialog != null) deviceDialog.dismiss();
        if (pairingDialog != null) { pairingDialog.dismiss(); pairingDialog = null; }
        if (keyboardDialog != null) { keyboardDialog.dismiss(); keyboardDialog = null; }
        renderStatus("연결이 종료됐어요. 앱을 열면 다시 연결합니다."); super.onStop();
    }
    @Override protected void onDestroy() { remote.close(); super.onDestroy(); }
    private void applyPalette(){
        setTheme(dark?com.morluto.remocon.R.style.AppTheme_Dark:com.morluto.remocon.R.style.AppTheme_Light);
        BG=Color.parseColor(dark?"#0C1424":"#F2F6FC");CARD=Color.parseColor(dark?"#15233B":"#FFFFFF");
        INK=Color.parseColor(dark?"#ECF2FF":"#172D4C");MUTED=Color.parseColor(dark?"#9AACCA":"#58708E");
        BLUE=Color.parseColor(dark?"#7DABFF":"#246BFD");BUTTON=Color.parseColor(dark?"#243552":"#E7EFFB");BORDER=Color.parseColor(dark?"#304461":"#D8E3F2");
    }
    private void buildUi() {
        String previousStatus=status==null?"휴대폰과 Chromecast를 같은 Wi-Fi에 연결하세요.":status.getText().toString();
        controls.clear();
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        int flags=dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(android.os.Build.VERSION.SDK_INT>=27&&!dark)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        LinearLayout root=vertical();root.setBackgroundColor(BG);root.setPadding(dp(18),dp(10),dp(18),dp(10));
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;}
            else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}
            v.setPadding(dp(18)+left,dp(10)+top,dp(18)+right,dp(10)+bottom);return insets;
        });

        LinearLayout header=vertical(),toolbar=row();
        FrameLayout powerArea=new FrameLayout(this);
        ImageButton power=new ImageButton(this);power.setImageResource(com.morluto.remocon.R.drawable.ic_power);power.setContentDescription("전원 전환");
        power.setScaleType(ImageView.ScaleType.FIT_CENTER);power.setPadding(dp(12),dp(12),dp(12),dp(12));
        power.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(55,239,68,68)),shape(Color.parseColor(dark?"#3B2430":"#FDE7EB"),40),null));
        controls.add(power);power.setOnClickListener(v->{if(connected){v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);remote.sendKey(26);}});
        powerArea.addView(power,new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.LEFT|Gravity.CENTER_VERTICAL));
        powerArea.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int size=Math.max(0,Math.min(dp(56),Math.min(r-l,b-t)));FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)power.getLayoutParams();
            if(p.width!=size||p.height!=size){p.width=size;p.height=size;power.setLayoutParams(p);}
        });
        toolbar.addView(powerArea,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout tools=row();tools.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        Button help=button("?",false);help.setContentDescription("사용 안내");help.setOnClickListener(v->showHelp());
        Switch mode=new Switch(this);mode.setText("다크");mode.setTextSize(11);mode.setTextColor(MUTED);mode.setChecked(dark);mode.setContentDescription("다크 모드 설정");mode.setSwitchPadding(dp(4));
        mode.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{BLUE,Color.WHITE}));
        mode.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{Color.parseColor(dark?"#395D91":"#A7C4FF"),BORDER}));
        tools.addView(mode,new LinearLayout.LayoutParams(dp(82),dp(44)));
        LinearLayout.LayoutParams helpParams=new LinearLayout.LayoutParams(dp(44),dp(44));helpParams.leftMargin=dp(8);tools.addView(help,helpParams);
        tools.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int size=Math.max(0,Math.min(dp(44),b-t));LinearLayout.LayoutParams hp=(LinearLayout.LayoutParams)help.getLayoutParams();
            if(hp.width!=size||hp.height!=size){hp.width=size;hp.height=size;help.setLayoutParams(hp);}
            LinearLayout.LayoutParams mp=(LinearLayout.LayoutParams)mode.getLayoutParams();if(mp.height!=size){mp.height=size;mode.setLayoutParams(mp);}
        });
        toolbar.addView(tools,new LinearLayout.LayoutParams(dp(134),-1));header.addView(toolbar,new LinearLayout.LayoutParams(-1,0,3));
        header.addView(fittedLabel("Chromecast 4세대 · Google TV",10,12,MUTED),new LinearLayout.LayoutParams(-1,0,1));section(root,header,14);
        mode.setOnCheckedChangeListener((b,value)->{dark=value;prefs.edit().putBoolean("dark",dark).apply();applyPalette();buildUi();});

        LinearLayout card=row();card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(shape(CARD,18));
        LinearLayout details=vertical();
        badge=fittedLabel("○  연결 대기",10,11,BLUE);details.addView(badge,new LinearLayout.LayoutParams(-1,0,1));
        deviceName=fittedLabel(name,10,16,INK);deviceName.setTypeface(null,Typeface.BOLD);details.addView(deviceName,new LinearLayout.LayoutParams(-1,0,1.5f));
        status=fittedLabel(previousStatus,10,12,MUTED);details.addView(status,new LinearLayout.LayoutParams(-1,0,1.5f));
        card.addView(details,new LinearLayout.LayoutParams(0,-1,1));
        connectButton=button("기기 연결",true);connectButton.setAutoSizeTextTypeUniformWithConfiguration(10,14,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams connectParams=new LinearLayout.LayoutParams(dp(88),-1);connectParams.leftMargin=dp(8);card.addView(connectButton,connectParams);connectButton.setOnClickListener(v->chooseDevice());section(root,card,16);

        LinearLayout apps=row();
        Button keyboard=button("키보드",false);keyboard.setContentDescription("키보드 입력");controls.add(keyboard);keyboard.setOnClickListener(v->showKeyboard());addShortcut(apps,keyboard);
        addShortcut(apps,appButton("YouTube","YouTube 실행",com.morluto.remocon.R.drawable.ic_youtube,AppLaunch.YOUTUBE));
        addShortcut(apps,appButton("Netflix","Netflix 실행",com.morluto.remocon.R.drawable.ic_netflix,AppLaunch.NETFLIX));section(root,apps,9);

        FrameLayout padFrame=new FrameLayout(this);
        LinearLayout dpad=vertical();dpad.setPadding(dp(6),dp(6),dp(6),dp(6));dpad.setBackground(shape(CARD,150));
        for(int y=0;y<3;y++){
            LinearLayout r=row();
            for(int x=0;x<3;x++){
                int key=0;String text="",desc="";
                if(y==0&&x==1){key=19;text="↑";desc="위";}if(y==1&&x==0){key=21;text="←";desc="왼쪽";}
                if(y==1&&x==1){key=23;text="OK";desc="확인";}if(y==1&&x==2){key=22;text="→";desc="오른쪽";}if(y==2&&x==1){key=20;text="↓";desc="아래";}
                View cell=key==0?new View(this):keyButton(text,desc,key,y==1&&x==1);
                r.addView(cell,new LinearLayout.LayoutParams(0,-1,1));
            }
            dpad.addView(r,new LinearLayout.LayoutParams(-1,0,1));
        }
        padFrame.addView(dpad,new FrameLayout.LayoutParams(dp(220),dp(220),Gravity.CENTER));
        padFrame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{
            int size=Math.max(0,Math.min(r-l,b-t)-dp(4));
            FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)dpad.getLayoutParams();
            if(p.width!=size||p.height!=size){p.width=size;p.height=size;dpad.setLayoutParams(p);}
        });section(root,padFrame,32);

        LinearLayout navigation=row();addKey(navigation,"↶","뒤로",4);addKey(navigation,"⌂","홈",3);addKey(navigation,"▶Ⅱ","재생 / 일시정지",85);section(root,navigation,10);
        LinearLayout audio=row();addKey(audio,"−","볼륨 줄이기",25);addKey(audio,"음소거","음소거",164);addKey(audio,"＋","볼륨 높이기",24);section(root,audio,10);
        setContentView(root);renderStatus(previousStatus);
    }
    private void section(LinearLayout root,View child,float weight){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,0,weight);p.topMargin=dp(2);p.bottomMargin=dp(2);root.addView(child,p);
    }
    private TextView fittedLabel(String text,int min,int max,int color){
        TextView t=label(text,max,color);t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setAutoSizeTextTypeUniformWithConfiguration(min,max,1,android.util.TypedValue.COMPLEX_UNIT_SP);return t;
    }
    private void addShortcut(LinearLayout row,Button button){
        button.setAutoSizeTextTypeUniformWithConfiguration(10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP);button.setSingleLine(true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);row.addView(button,p);
    }
    private Button appButton(String text,String description,int icon,String link){
        Button b=button(text,false);b.setContentDescription(description);controls.add(b);
        android.graphics.drawable.Drawable logo=getDrawable(icon);logo.setBounds(0,0,dp(18),dp(18));b.setCompoundDrawables(logo,null,null,null);b.setCompoundDrawablePadding(dp(4));
        b.setOnClickListener(v->{if(!connected)return;if(remote.launchApp(link)){v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);Toast.makeText(this,text+" 실행 요청을 보냈어요.",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"TV가 앱 바로가기를 지원하지 않거나 연결되지 않았어요.",Toast.LENGTH_LONG).show();});return b;
    }
    private void addKey(LinearLayout row,String symbol,String desc,int key){
        Button b=keyButton(symbol.equals(desc)?desc:symbol+"\n"+desc,desc,key,false);
        b.setMaxLines(2);b.setAutoSizeTextTypeUniformWithConfiguration(9,15,1,android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(dp(3),0,dp(3),0);row.addView(b,p);
    }
    private Button keyButton(String text,String description,int key,boolean accent){Button b=button(text,accent);b.setContentDescription(description);controls.add(b);b.setOnClickListener(v->{if(connected){v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);remote.sendKey(key);}});return b;}
    private Button button(String text,boolean accent){Button b=new Button(this);b.setText(text);b.setTextSize(16);b.setAutoSizeTextTypeUniformWithConfiguration(10,20,1,android.util.TypedValue.COMPLEX_UNIT_SP);b.setAllCaps(false);b.setStateListAnimator(null);b.setTextColor(accent?(dark?BG:Color.WHITE):INK);b.setTypeface(null,Typeface.BOLD);b.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(45,100,160,255)),shape(accent?BLUE:BUTTON,24),null));b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),0,dp(8),0);return b;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private AlertDialog.Builder dialogBuilder(){
        return new AlertDialog.Builder(this){
            @Override public AlertDialog create(){
                AlertDialog d=super.create();d.setOnShowListener(v->{
                    d.getWindow().setBackgroundDrawable(shape(CARD,20));
                    for(int id:new int[]{-1,-2,-3}){Button b=d.getButton(id);if(b!=null){b.setTextColor(BLUE);b.setAllCaps(false);}}
                });return d;
            }
        };
    }
    private TextView label(String text,int size,int color){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void space(LinearLayout l,int n){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(n)));}
    private void setControls(boolean enable){for(View v:controls){v.setEnabled(enable);v.setAlpha(enable?1f:.5f);}}
    private void renderStatus(String message){status.setText(message);badge.setText(connected?"●  연결됨":busy?"●  연결 중":"○  연결 대기");badge.setTextColor(connected?BLUE:MUTED);connectButton.setText(connected?"기기 변경":busy?"연결 취소":"기기 연결");setControls(connected);}
    private void showKeyboard(){
        if(!connected)return;
        LinearLayout content=vertical();content.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView hint=label("먼저 TV에서 검색창을 선택하세요. 입력한 검색어로 TV의 입력 내용을 바꿉니다.",14,MUTED);content.addView(hint);
        EditText input=new EditText(this);input.setHint("검색어 입력");input.setTextColor(INK);input.setHintTextColor(MUTED);input.setSingleLine(true);input.setInputType(InputType.TYPE_CLASS_TEXT);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});content.addView(input);
        keyboardDialog=dialogBuilder().setTitle("TV 키보드 입력").setView(content).setPositiveButton("검색 / Enter",null).setNeutralButton("텍스트 전송",null).setNegativeButton("닫기",null).create();keyboardDialog.show();
        View.OnClickListener search=v->{String text=input.getText().toString();if(text.trim().isEmpty()){input.setError("검색어를 입력해 주세요.");return;}if(remote.sendText(text,true)){keyboardDialog.dismiss();keyboardDialog=null;}else input.setError("TV에서 검색창을 선택하고 다시 시도하세요.");};
        keyboardDialog.getButton(-1).setOnClickListener(search);
        keyboardDialog.getButton(-3).setOnClickListener(v->{String text=input.getText().toString();if(text.trim().isEmpty()){input.setError("검색어를 입력해 주세요.");return;}if(remote.sendText(text,false)){keyboardDialog.dismiss();keyboardDialog=null;}else input.setError("TV에서 검색창을 선택하고 다시 시도하세요.");});
        input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){search.onClick(v);return true;}return false;});input.requestFocus();
        keyboardDialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE|android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        input.postDelayed(()->{if(keyboardDialog!=null&&keyboardDialog.isShowing())((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);},180);
    }
    private void chooseDevice(){
        remote.disconnect(); connected=false;busy=false;renderStatus("기기를 선택해 주세요.");devices.clear();
        LinearLayout content=vertical();content.setPadding(dp(20),dp(12),dp(20),dp(12));
        content.addView(label("같은 Wi-Fi의 Google TV 기기를 검색합니다.",13,MUTED));space(content,12);
        deviceList=vertical();content.addView(deviceList);deviceList.addView(label("검색 중… (최대 20초)",14,INK));
        ScrollView deviceScroll=new ScrollView(this);deviceScroll.addView(content);
        deviceDialog=dialogBuilder().setTitle("Chromecast 연결").setView(deviceScroll).setPositiveButton("IP 직접 입력",(d,w)->manualHost()).setNegativeButton("닫기",null).create();
        deviceDialog.setOnDismissListener(d->discovery.stop());deviceDialog.show();discovery.start();
    }
    @Override public void onDevice(String device,String address){
        if(!started||deviceDialog==null||!deviceDialog.isShowing())return;devices.put(address,device);deviceList.removeAllViews();
        for(java.util.Map.Entry<String,String> entry:devices.entrySet()){
            Button b=button(entry.getValue()+"\n"+entry.getKey(),false);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(72));p.bottomMargin=dp(8);deviceList.addView(b,p);
            b.setOnClickListener(v->{deviceDialog.dismiss();connectTo(entry.getKey(),entry.getValue(),false);});
        }
    }
    @Override public void onDiscoveryError(String message){if(deviceList!=null&&deviceDialog!=null&&deviceDialog.isShowing()){deviceList.removeAllViews();deviceList.addView(label(message,14,MUTED));}}
    private void manualHost(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("예: 192.168.0.25");input.setText(host);input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout l=vertical();l.setPadding(dp(24),dp(8),dp(24),dp(8));l.addView(label("TV 설정 → 네트워크에서 IP 주소를 확인하세요.",13,MUTED));l.addView(input);
        AlertDialog d=dialogBuilder().setTitle("IP 주소로 연결").setView(l).setPositiveButton("연결",null).setNegativeButton("취소",null).create();d.show();
        d.getButton(-1).setOnClickListener(v->{String h=input.getText().toString().trim();if(!validIp(h)){input.setError("올바른 IPv4 주소를 입력해 주세요.");return;}d.dismiss();connectTo(h,"Chromecast with Google TV",false);});
    }
    static boolean validIp(String host){String[] pieces=host.split("\\.",-1);if(pieces.length!=4)return false;for(String s:pieces){if(!s.matches("[0-9]{1,3}")||Integer.parseInt(s)>255)return false;}return !host.equals("0.0.0.0")&&!host.equals("255.255.255.255");}
    private void connectTo(String address,String device,boolean pair){
        pairingError="";host=address;name=device;deviceName.setText(name);connected=false;busy=true;renderStatus("기기에 연결하고 있어요…");
        if(pair||!remote.isPaired(host))remote.beginPairing(host);else remote.connect(host);
    }
    @Override public void onStatus(String message){runOnUiThread(()->{if(started){if(message.contains("코드가 맞지"))pairingError=message;renderStatus(message);}});}
    @Override public void onPairingRequired(){runOnUiThread(()->{
        if(!started)return;busy=true;renderStatus("TV 화면의 6자리 코드를 입력하세요.");
        if(pairingDialog!=null)pairingDialog.dismiss();
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("A1B2C3");input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);input.setFilters(new InputFilter[]{new InputFilter.AllCaps(),new InputFilter.LengthFilter(6)});
        LinearLayout content=vertical();content.setPadding(dp(24),dp(8),dp(24),dp(8));content.addView(label(pairingError.isEmpty()?"Chromecast에 표시된 6자리 영문·숫자 코드를 입력하세요.":pairingError,14,MUTED));content.addView(input);pairingError="";
        pairingDialog=dialogBuilder().setTitle("TV와 페어링").setView(content).setPositiveButton("페어링",null).setNegativeButton("취소",(d,w)->cancelPairing()).create();
        pairingDialog.setOnCancelListener(d->cancelPairing());pairingDialog.show();
        pairingDialog.getButton(-1).setOnClickListener(v->{String code=input.getText().toString().trim();if(!code.matches("[0-9A-Fa-f]{6}")){input.setError("6자리 코드(0–9, A–F)를 입력해 주세요.");return;}pairingDialog.getButton(-1).setEnabled(false);input.setEnabled(false);remote.submitCode(code);});
    });}
    private void cancelPairing(){remote.disconnect();busy=false;connected=false;renderStatus("페어링을 취소했어요.");pairingDialog=null;}
    @Override public void onConnected(){runOnUiThread(()->{if(!started)return;connected=true;busy=false;if(pairingDialog!=null){pairingDialog.dismiss();pairingDialog=null;}prefs.edit().putString("host",host).putString("name",name).apply();renderStatus(host+" · 리모컨을 사용할 수 있어요.");});}
    @Override public void onError(String message){runOnUiThread(()->{if(!started)return;connected=false;busy=false;if(pairingDialog!=null){pairingDialog.dismiss();pairingDialog=null;}if(keyboardDialog!=null){keyboardDialog.dismiss();keyboardDialog=null;}renderStatus(message);if(!host.isEmpty())dialogBuilder().setTitle("연결을 확인해 주세요").setMessage(message+"\n\n같은 Wi-Fi인지, TV가 켜져 있는지 확인하세요. 페어링 정보가 바뀌었다면 다시 페어링해 주세요.").setPositiveButton("다시 연결",(d,w)->connectTo(host,name,false)).setNeutralButton("다시 페어링",(d,w)->connectTo(host,name,true)).setNegativeButton("닫기",null).show();});}
    private void showHelp(){dialogBuilder().setTitle("리모콘 사용 안내").setMessage("1. 휴대폰과 Chromecast 4세대를 같은 Wi-Fi에 연결하세요.\n2. ‘기기 연결’에서 TV를 선택하세요.\n3. TV에 표시되는 6자리 코드를 입력하세요.\n\n검색되지 않으면 IP 직접 입력을 이용하세요. 게스트 Wi-Fi나 AP 격리가 켜진 네트워크에서는 연결되지 않을 수 있어요.\n\n볼륨과 전원은 Chromecast 및 TV의 HDMI-CEC 설정에 따라 동작합니다. 음성 입력과 TV 입력 전환은 이 버전에서 지원하지 않습니다.\n\n인증 정보는 이 휴대폰의 앱 저장소에만 보관합니다.").setPositiveButton("확인",null).show();}
}

