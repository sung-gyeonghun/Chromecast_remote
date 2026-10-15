package com.morluto.remocon;

import android.content.Context;
import android.net.*;
import android.net.nsd.*;
import android.net.wifi.WifiManager;
import android.os.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Finds Remote v2 and Google Cast advertisements; DIAL and a saved IP are fallbacks. */
public final class DeviceDiscovery implements AutoCloseable {
    public interface Listener {void onDevice(String name,String host);void onDiscoveryError(String message);}
    private final Context context;
    private final NsdManager nsd;
    private final ConnectivityManager connectivity;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final List<NsdManager.DiscoveryListener> searches=new ArrayList<>();
    private final ArrayDeque<NsdServiceInfo> pending=new ArrayDeque<>();
    private final Set<String> queued=new HashSet<>(),shown=new HashSet<>();
    private WifiManager.MulticastLock multicast;
    private volatile int generation;
    private volatile MulticastSocket ssdp;
    private boolean resolving;
    public DeviceDiscovery(Context context,Listener listener){
        this.context=context.getApplicationContext();this.listener=listener;
        nsd=context.getSystemService(NsdManager.class);connectivity=context.getSystemService(ConnectivityManager.class);
    }
    public void start(){
        stop();int ticket=generation;
        WifiManager wifi=context.getSystemService(WifiManager.class);
        try{multicast=wifi.createMulticastLock("remocon-discovery");multicast.setReferenceCounted(false);multicast.acquire();}catch(RuntimeException ignored){}
        for(String type:new String[]{"_androidtvremote2._tcp.","_googlecast._tcp.","_androidtvremote._tcp."}){
            NsdManager.DiscoveryListener search=new NsdManager.DiscoveryListener(){
                public void onDiscoveryStarted(String t){}
                public void onDiscoveryStopped(String t){}
                public void onStopDiscoveryFailed(String t,int e){}
                public void onStartDiscoveryFailed(String t,int e){}
                public void onServiceLost(NsdServiceInfo s){}
                public void onServiceFound(NsdServiceInfo service){main.post(()->{
                    if(ticket!=generation)return;
                    if(queued.add(service.getServiceType()+"|"+service.getServiceName())){pending.add(service);resolveNext(ticket);}
                });}
            };
            try{nsd.discoverServices(type,NsdManager.PROTOCOL_DNS_SD,search);searches.add(search);}catch(RuntimeException ignored){}
        }
        workers.execute(()->discoverDial(ticket));
        android.content.SharedPreferences p=context.getSharedPreferences("remocon-ui",Context.MODE_PRIVATE);
        String saved=p.getString("host","");
        if(!saved.isEmpty())probe(p.getString("name","저장된 Chromecast"),saved,ticket);
        main.postDelayed(()->{if(ticket==generation&&shown.isEmpty())listener.onDiscoveryError("검색을 계속하고 있어요. 기기가 보이지 않으면 IP 직접 입력도 사용할 수 있습니다.");},15000);
    }
    @SuppressWarnings("deprecation") private void resolveNext(int ticket){
        if(ticket!=generation||resolving||pending.isEmpty())return;
        resolving=true;NsdServiceInfo candidate=pending.remove();
        try{nsd.resolveService(candidate,new NsdManager.ResolveListener(){
            public void onResolveFailed(NsdServiceInfo service,int error){main.post(()->{
                if(ticket!=generation)return;queued.remove(candidate.getServiceType()+"|"+candidate.getServiceName());complete(ticket);
            });}
            public void onServiceResolved(NsdServiceInfo service){main.post(()->{
                if(ticket!=generation)return;
                String name=service.getServiceName();byte[] friendly=service.getAttributes().get("fn");
                if(friendly!=null&&friendly.length>0)name=new String(friendly,StandardCharsets.UTF_8);
                List<InetAddress> addresses=new ArrayList<>();
                if(Build.VERSION.SDK_INT>=34)addresses.addAll(service.getHostAddresses());
                if(service.getHost()!=null&&!addresses.contains(service.getHost()))addresses.add(service.getHost());
                for(InetAddress address:addresses)if(address instanceof Inet4Address)probe(name,address.getHostAddress(),ticket);
                complete(ticket);
            });}
        });}catch(RuntimeException e){complete(ticket);}
    }
    private void complete(int ticket){if(ticket==generation){resolving=false;resolveNext(ticket);}}
    private Network wifiNetwork(){
        for(Network network:connectivity.getAllNetworks()){
            NetworkCapabilities c=connectivity.getNetworkCapabilities(network);
            if(c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!c.hasTransport(NetworkCapabilities.TRANSPORT_VPN))return network;
        }return null;
    }
    private void probe(String name,String host,int ticket){workers.execute(()->{
        if(ticket!=generation)return;
        try(Socket socket=new Socket()){
            Network wifi=wifiNetwork();if(wifi!=null)wifi.bindSocket(socket);
            socket.connect(new InetSocketAddress(host,6466),1000);
            main.post(()->{if(ticket==generation&&shown.add(host))listener.onDevice(name,host);});
        }catch(Exception ignored){}
    });}
    private void discoverDial(int ticket){
        try(MulticastSocket socket=new MulticastSocket(null)){
            socket.setReuseAddress(true);socket.bind(new InetSocketAddress(0));socket.setSoTimeout(2500);socket.setTimeToLive(1);
            Network wifi=wifiNetwork();if(wifi!=null)wifi.bindSocket(socket);
            if(ticket!=generation)return;ssdp=socket;
            byte[] query=("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:dial-multiscreen-org:service:dial:1\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
            DatagramPacket request=new DatagramPacket(query,query.length,InetAddress.getByName("239.255.255.250"),1900);
            byte[] bytes=new byte[8192];
            while(ticket==generation){
                socket.send(request);
                for(;;){
                    DatagramPacket packet=new DatagramPacket(bytes,bytes.length);
                    try{socket.receive(packet);}catch(SocketTimeoutException timeout){break;}
                    String response=new String(packet.getData(),packet.getOffset(),packet.getLength(),StandardCharsets.US_ASCII).toLowerCase(Locale.ROOT);
                    if(response.startsWith("http/1.1 200")&&response.contains("dial-multiscreen-org")&&packet.getAddress() instanceof Inet4Address)probe("Chromecast / Google TV",packet.getAddress().getHostAddress(),ticket);
                    if(ticket!=generation)return;
                }
            }
        }catch(Exception ignored){}finally{if(ticket==generation)ssdp=null;}
    }
    public void stop(){
        generation++;pending.clear();queued.clear();shown.clear();resolving=false;
        for(NsdManager.DiscoveryListener search:searches)try{nsd.stopServiceDiscovery(search);}catch(RuntimeException ignored){}
        searches.clear();MulticastSocket socket=ssdp;ssdp=null;if(socket!=null)socket.close();
        if(multicast!=null&&multicast.isHeld())multicast.release();multicast=null;
    }
    @Override public void close(){stop();workers.shutdownNow();}
}
