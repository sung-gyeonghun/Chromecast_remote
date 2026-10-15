package com.morluto.remocon;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import com.morluto.remocon.protocol.RemoteClient;

/** Owns the connection independently of the activity's visibility. */
public final class ConnectionService extends Service implements RemoteClient.Listener {
    private static final String CHANNEL="tv-connection",DISCONNECT="com.morluto.remocon.DISCONNECT";
    private final LocalBinder binder=new LocalBinder();
    private RemoteClient client;
    private RemoteClient.Listener ui;
    private boolean wanted,connected,pairing;
    private String host="",message="기기 연결을 선택하세요.";
    private ConnectivityManager manager;
    private ConnectivityManager.NetworkCallback networkCallback;
    public final class LocalBinder extends Binder{public ConnectionService getService(){return ConnectionService.this;}}
    @Override public void onCreate(){
        super.onCreate();client=new RemoteClient(this,this);
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"TV 연결 유지",NotificationManager.IMPORTANCE_LOW));
    }
    @Override public IBinder onBind(Intent intent){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent!=null&&DISCONNECT.equals(intent.getAction())){disconnect();return START_NOT_STICKY;}
        if(!wanted){
            SharedPreferences p=getSharedPreferences("remocon-ui",MODE_PRIVATE);
            String saved=p.getString("host","");
            if(p.getBoolean("keep_connected",false)&&!saved.isEmpty())begin(saved,false);
            else{stopSelf();return START_NOT_STICKY;}
        }
        return START_STICKY;
    }
    public void attach(RemoteClient.Listener listener){
        ui=listener;
        if(connected)ui.onConnected();else if(pairing)ui.onPairingRequired();else if(wanted)ui.onReconnecting(message);else ui.onDisconnected(message);
    }
    public void detach(RemoteClient.Listener listener){if(ui==listener)ui=null;}
    public boolean isMaintainingConnection(){return wanted;}
    public String getHost(){return host;}
    public boolean isPaired(String target){return client.isPaired(target);}
    public void connect(String target){begin(target,false);}
    public void beginPairing(String target){begin(target,true);}
    public void submitCode(String code){client.submitCode(code);}
    public void sendKey(int key){if(key==24||key==25||key==164)client.sendVolume(key);else client.sendKey(key);}
    public boolean launchApp(String link){return client.launchApp(link);}
    private void begin(String target,boolean needsPairing){
        wanted=true;connected=false;pairing=false;host=target;message="Chromecast에 연결하고 있습니다…";
        getSharedPreferences("remocon-ui",MODE_PRIVATE).edit().putString("host",target).putBoolean("keep_connected",true).apply();
        startForegroundService(new Intent(this,ConnectionService.class));updateNotification();watchNetwork();
        if(needsPairing)client.beginPairing(target);else client.connect(target);
    }
    private void watchNetwork(){
        if(networkCallback!=null)return;
        manager=getSystemService(ConnectivityManager.class);
        networkCallback=new ConnectivityManager.NetworkCallback(){
            private Network last=manager.getActiveNetwork();
            @Override public void onLost(Network network){if(network.equals(last)){last=null;client.networkChanged();}}
            @Override public void onAvailable(Network network){if(last!=null&&!last.equals(network))client.networkChanged();last=network;}
        };
        manager.registerDefaultNetworkCallback(networkCallback);
    }
    private void updateNotification(){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent disconnect=PendingIntent.getService(this,2,new Intent(this,ConnectionService.class).setAction(DISCONNECT),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification notification=new Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle(connected?"Chromecast 연결 유지 중":"Chromecast 연결 중")
                .setContentText(message).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true).addAction(new Notification.Action.Builder(null,"연결 해제",disconnect).build()).build();
        if(Build.VERSION.SDK_INT>=29)startForeground(71,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(71,notification);
    }
    public void disconnect(){
        wanted=false;connected=false;pairing=false;client.disconnect();message="연결을 해제했어요.";
        getSharedPreferences("remocon-ui",MODE_PRIVATE).edit().putBoolean("keep_connected",false).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();if(ui!=null)ui.onDisconnected(message);
    }
    @Override public void onStatus(String text){message=text;if(wanted)updateNotification();if(ui!=null)ui.onStatus(text);}
    @Override public void onPairingRequired(){pairing=true;if(ui!=null)ui.onPairingRequired();}
    @Override public void onConnected(){connected=true;pairing=false;message=host+" · 연결 유지 중";updateNotification();if(ui!=null)ui.onConnected();}
    @Override public void onReconnecting(String text){connected=false;pairing=false;message=text;updateNotification();if(ui!=null)ui.onReconnecting(text);}
    @Override public void onNotice(String text){if(ui!=null)ui.onNotice(text);}
    @Override public void onError(String text){disconnect();message=text;if(ui!=null)ui.onError(text);}
    @Override public void onDestroy(){if(networkCallback!=null)manager.unregisterNetworkCallback(networkCallback);client.close();super.onDestroy();}
}
