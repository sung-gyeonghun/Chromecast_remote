package com.morluto.remocon.protocol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.function.BooleanSupplier;
import javax.net.ssl.*;

/** Cast receiver volume; does not launch a receiver app or interrupt playback. */
final class CastVolume {
    private static final String RECEIVER="urn:x-cast:com.google.cast.receiver";
    private static final String CONNECTION="urn:x-cast:com.google.cast.tp.connection";
    private static final String HEARTBEAT="urn:x-cast:com.google.cast.tp.heartbeat";
    private final SharedPreferences pins;
    private volatile SSLSocket active;
    CastVolume(Context context){pins=context.getSharedPreferences("cast-volume-keys",Context.MODE_PRIVATE);}
    void cancel(){SSLSocket s=active;active=null;SocketCloser.close(s);}
    void adjust(String host,int key,BooleanSupplier live)throws Exception{
        if(!live.getAsBoolean())return;
        String saved=pins.getString(host,null);
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{throw new java.security.cert.CertificateException("No client authentication");}
            public void checkServerTrusted(X509Certificate[] c,String a)throws java.security.cert.CertificateException{
                if(c==null||c.length==0)throw new java.security.cert.CertificateException("Cast certificate missing");
                if(saved!=null&&!MessageDigest.isEqual(Base64.decode(saved,Base64.NO_WRAP),c[0].getPublicKey().getEncoded()))throw new java.security.cert.CertificateException("Cast 기기 인증서가 변경되었습니다.");
            }
        };
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{trust},new SecureRandom());
        SSLSocket socket=(SSLSocket)tls.getSocketFactory().createSocket();active=socket;
        try(SSLSocket s=socket){
            s.setSoTimeout(4000);s.connect(new InetSocketAddress(host,8009),3500);s.startHandshake();
            if(!live.getAsBoolean())return;
            // Cast has a separate TLS identity. First use is scoped to the already-paired host.
            if(saved==null&&!pins.edit().putString(host,Base64.encodeToString(s.getSession().getPeerCertificates()[0].getPublicKey().getEncoded(),Base64.NO_WRAP)).commit())throw new IOException("Cast 인증서 저장 실패");
            send(s,CONNECTION,new JSONObject().put("type","CONNECT").put("origin",new JSONObject()));
            send(s,RECEIVER,new JSONObject().put("type","GET_STATUS").put("requestId",1));
            JSONObject status=receiveStatus(s,1,live).getJSONObject("status").optJSONObject("volume");
            if(status==null)throw new IOException("Chromecast가 볼륨 정보를 제공하지 않습니다.");
            if("fixed".equalsIgnoreCase(status.optString("controlType")))throw new IOException("기기 볼륨이 고정되어 있습니다. Google TV의 볼륨 제어를 Chromecast 또는 HDMI-CEC로 설정하세요.");
            JSONObject volume=new JSONObject();
            if(key==164)volume.put("muted",!status.optBoolean("muted",false));
            else volume.put("level",Math.max(0,Math.min(1,status.optDouble("level",.5)+(key==24?.05:-.05)))).put("muted",false);
            if(!live.getAsBoolean())return;
            send(s,RECEIVER,new JSONObject().put("type","SET_VOLUME").put("requestId",2).put("volume",volume));
            JSONObject actual=receiveStatus(s,2,live).getJSONObject("status").getJSONObject("volume");
            boolean accepted=key==164?actual.optBoolean("muted")==volume.getBoolean("muted"):Math.abs(actual.optDouble("level",-1)-volume.getDouble("level"))<.02;
            if(!accepted)throw new IOException("TV가 볼륨 변경을 적용하지 않았습니다. Google TV의 볼륨 제어 설정을 확인하세요.");
        }finally{if(active==socket)active=null;}
    }
    private static JSONObject receiveStatus(SSLSocket s,int request,BooleanSupplier live)throws Exception{
        long deadline=android.os.SystemClock.elapsedRealtime()+6000;
        while(live.getAsBoolean()&&android.os.SystemClock.elapsedRealtime()<deadline){
            ProtoWire.Fields msg=ProtoWire.parse(read(s.getInputStream()));
            String ns=new String(msg.data(4),java.nio.charset.StandardCharsets.UTF_8);
            if(msg.number(5)!=0||(!RECEIVER.equals(ns)&&!HEARTBEAT.equals(ns)&&!CONNECTION.equals(ns)))continue;
            JSONObject data=new JSONObject(new String(msg.data(6),java.nio.charset.StandardCharsets.UTF_8));
            String type=data.optString("type");
            if(HEARTBEAT.equals(ns)&&"PING".equals(type))send(s,HEARTBEAT,new JSONObject().put("type","PONG"));
            if(CONNECTION.equals(ns)&&"CLOSE".equals(type))throw new IOException("Chromecast 볼륨 채널이 닫혔습니다.");
            if(RECEIVER.equals(ns)&&data.optInt("requestId",-1)==request){
                if("RECEIVER_STATUS".equals(type))return data;
                throw new IOException("Chromecast가 볼륨 요청을 거절했습니다.");
            }
        }
        throw new IOException("Chromecast 볼륨 응답 시간이 초과되었습니다.");
    }
    private static void send(SSLSocket s,String namespace,JSONObject payload)throws IOException{
        byte[] message=new ProtoWire.Message().number(1,0).text(2,"remocon-volume").text(3,"receiver-0").text(4,namespace).number(5,0).text(6,payload.toString()).build();
        DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeInt(message.length);out.write(message);out.flush();
    }
    private static byte[] read(InputStream input)throws IOException{
        DataInputStream in=new DataInputStream(input);int size=in.readInt();
        if(size<=0||size>ProtoWire.MAX_FRAME)throw new IOException("Invalid Cast frame length");
        byte[] bytes=new byte[size];in.readFully(bytes);return bytes;
    }
}
