package com.morluto.remocon.protocol;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import java.io.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/** Android TV Remote v2. All listener callbacks run on the main thread. */
public final class RemoteClient implements AutoCloseable {
    public interface Listener {
        void onStatus(String status);
        void onPairingRequired();
        void onConnected();
        void onError(String message);
    }
    private final Context context;
    private final Listener listener;
    private final SharedPreferences pins;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Object lock = new Object();
    private volatile int generation;
    private volatile SSLSocket socket;
    private volatile boolean connected;
    private volatile boolean awaitingCode;
    private volatile boolean closed;
    private volatile String pendingCode;
    private volatile boolean imeSupported, imeReady, appLinkSupported;
    private int imeCounter, fieldCounter;
    private String currentApp = "";
    private KeyStore identity;
    private X509Certificate clientCertificate;

    public RemoteClient(Context context, Listener listener) {
        this.context = context.getApplicationContext(); this.listener = listener;
        pins = this.context.getSharedPreferences("remote_trusted_keys", Context.MODE_PRIVATE);
    }
    public boolean isPaired(String host) { return pins.contains(normalize(host)); }
    public void beginPairing(String host) { start(host, true); }
    public void connect(String host) { start(host, false); }
    public void submitCode(String code) {
        synchronized (lock) { if (!awaitingCode) return; pendingCode = code == null ? "" : code.trim(); lock.notifyAll(); }
    }
    public void sendKey(int keyCode) {
        final int token = generation;
        if (!connected || keyCode < 0 || keyCode > 304 || closed) return;
        writer.execute(() -> {
            try {
                SSLSocket target;
                synchronized(lock) { if (!current(token) || !connected) return; target = socket; }
                if (target != null) send(target, new ProtoWire.Message().nested(10,new ProtoWire.Message().number(1,keyCode).number(2,3)));
            }
            catch (Exception e) { fail(token,e); closeSocket(token); }
        });
    }
    /** False until the TV negotiates IME and supplies input counters. */
    public boolean sendText(String text, boolean enter) {
        final int token;final SSLSocket target;final byte[] payload;
        synchronized(lock) {
            if(!connected || closed || !imeSupported || !imeReady || text==null || text.isEmpty() || text.length()>1000)return false;
            token=generation;target=socket;payload=ImeMessage.encode(text,imeCounter,fieldCounter);
        }
        writer.execute(()->{
            try{
                synchronized(lock){if(!current(token)||!connected||socket!=target)return;}
                synchronized(target){ProtoWire.writeFrame(target.getOutputStream(),payload);}
                if(enter)send(target,new ProtoWire.Message().nested(10,new ProtoWire.Message().number(1,66).number(2,3)));
            }catch(Exception e){fail(token,e);closeSocket(token);}
        });
        return true;
    }
    public boolean launchApp(String link) {
        final int token;final SSLSocket target;final byte[] payload;
        synchronized(lock){
            if(!connected||closed||!appLinkSupported)return false;
            token=generation;target=socket;payload=AppLaunch.encode(link);
        }
        writer.execute(()->{
            try{
                synchronized(lock){if(!current(token)||!connected||socket!=target)return;imeReady=false;}
                synchronized(target){ProtoWire.writeFrame(target.getOutputStream(),payload);}
            }catch(Exception e){fail(token,e);closeSocket(token);}
        });return true;
    }
    public void disconnect() {
        synchronized (lock) { generation++; connected = false; imeSupported=false;imeReady=false;appLinkSupported=false;imeCounter=0;fieldCounter=0;currentApp="";awaitingCode = false; pendingCode = null; closeSocket(); lock.notifyAll(); }
    }
    @Override public void close() { disconnect(); closed = true; reader.shutdownNow(); writer.shutdownNow(); }
    private static String normalize(String host) { return host == null ? "" : host.trim().toLowerCase(java.util.Locale.ROOT); }
    private boolean current(int token) { return !closed && generation == token; }
    private void event(int token, Runnable action) { main.post(() -> { if (current(token)) action.run(); }); }
    private void status(int token,String text) { event(token,() -> listener.onStatus(text)); }
    private void fail(int token,Exception error) {
        synchronized(lock) { if (!current(token)) return; connected = false; }
        event(token,() -> listener.onError(error.getMessage() == null ? "연결에 실패했습니다." : error.getMessage()));
    }
    private void start(String input, boolean pair) {
        if (closed) return;
        disconnect(); final int token = generation; final String host = normalize(input);
        reader.execute(() -> {
            try {
                if (host.isEmpty() || host.contains("/") || host.contains(":") || host.contains(" ")) throw new IOException("Chromecast의 IPv4 주소 또는 호스트 이름을 입력하세요.");
                loadIdentity();
                if (!current(token)) return;
                if (pair) pair(host,token);
                if (!current(token)) return;
                control(host,token);
            } catch (Exception e) { if (current(token)) fail(token,e); }
            finally { closeSocket(token); }
        });
    }
    private void pair(String host,int token) throws Exception {
        status(token,"TV에서 페어링 코드를 준비하고 있습니다…");
        SSLSocket s = open(host,6467,null,token);
        X509Certificate server = (X509Certificate)s.getSession().getPeerCertificates()[0];
        send(s,polo(10,new ProtoWire.Message().text(1,"atvremote").text(2,"Remocon")));
        expect(s,11);
        ProtoWire.Message encoding = new ProtoWire.Message().number(1,3).number(2,6);
        send(s,polo(20,new ProtoWire.Message().nested(1,encoding).number(3,1)));
        expect(s,20);
        send(s,polo(30,new ProtoWire.Message().nested(1,encoding).number(2,1)));
        expect(s,31);
        synchronized(lock) { if (!current(token)) return; awaitingCode = true; pendingCode = null; }
        event(token,listener::onPairingRequired);
        long end = System.currentTimeMillis() + 120000;
        byte[] secret = null;
        while (current(token) && secret == null) {
            String code;
            synchronized(lock) {
                while (pendingCode == null && current(token) && System.currentTimeMillis() < end) lock.wait(Math.max(1,end-System.currentTimeMillis()));
                if (!current(token)) return;
                if (pendingCode == null) throw new IOException("페어링 코드 입력 시간이 지났습니다. 다시 시도하세요.");
                code = pendingCode; pendingCode = null;
            }
            try { secret = PairingSecret.compute((RSAPublicKey)clientCertificate.getPublicKey(),(RSAPublicKey)server.getPublicKey(),code); }
            catch (IllegalArgumentException e) { status(token,e.getMessage()); event(token,listener::onPairingRequired); }
        }
        awaitingCode = false;
        if (!current(token)) return;
        send(s,polo(40,new ProtoWire.Message().data(1,secret))); expect(s,41);
        // Persist only after the TV accepts a code bound to both RSA public keys.
        synchronized(lock) {
            if (!current(token)) return;
            if (!pins.edit().putString(host,Base64.encodeToString(server.getPublicKey().getEncoded(),Base64.NO_WRAP)).commit()) throw new IOException("페어링 정보를 저장하지 못했습니다.");
        }
        closeSocket(token); status(token,"페어링 완료. 리모컨에 연결하고 있습니다…");
    }
    private static ProtoWire.Message polo(int field,ProtoWire.Message value) { return new ProtoWire.Message().number(1,2).number(2,200).nested(field,value); }
    private static void expect(SSLSocket s,int field) throws IOException {
        ProtoWire.Fields message = ProtoWire.parse(ProtoWire.readFrame(s.getInputStream()));
        if (message.number(2) != 200 || !message.has(field)) throw new IOException("TV가 페어링을 거절했습니다. 다시 페어링하세요.");
    }
    private void control(String host,int token) throws Exception {
        String pin = pins.getString(host,null);
        if (pin == null) throw new IOException("먼저 TV와 페어링하세요.");
        status(token,"Chromecast에 연결하고 있습니다…");
        SSLSocket s = open(host,6466,Base64.decode(pin,Base64.NO_WRAP),token);
        int features = 1 | 2 | 4 | 32 | 64 | 512;
        long deadline = System.currentTimeMillis()+15000;
        while (current(token)) {
            ProtoWire.Fields message = ProtoWire.parse(ProtoWire.readFrame(s.getInputStream()));
            if (!current(token)) return;
            if (message.has(1)) {
                features &= (int)message.nested(1).number(1);
                synchronized(lock){if(!current(token))return;imeSupported=(features & 4)!=0;appLinkSupported=(features & 512)!=0;}
                if ((features & 2) == 0) throw new IOException("TV의 Remote Service가 키 입력을 지원하지 않습니다.");
                ProtoWire.Message info = new ProtoWire.Message().number(3,1).text(4,"1").text(5,"com.morluto.remocon").text(6,"1.0.0");
                send(s,new ProtoWire.Message().nested(1,new ProtoWire.Message().number(1,features).nested(2,info)));
            }
            if (message.has(2)) send(s,new ProtoWire.Message().nested(2,new ProtoWire.Message().number(1,features)));
            if (message.has(8)) send(s,new ProtoWire.Message().nested(9,new ProtoWire.Message().number(1,message.nested(8).number(1))));
            if (message.has(3)) throw new IOException("TV 리모컨 서비스가 오류를 반환했습니다.");
            synchronized(lock){
                if(!current(token))return;
                if(message.has(20)){
                    String app=new String(message.nested(20).nested(1).data(12),java.nio.charset.StandardCharsets.UTF_8);
                    if(!app.isEmpty()&&!app.equals(currentApp)){currentApp=app;imeReady=false;}
                }
                if(message.has(21)){
                    ProtoWire.Fields ime=message.nested(21);imeCounter=(int)ime.number(1);fieldCounter=(int)ime.number(2);imeReady=imeSupported;
                }
            }
            if (message.has(40) && !connected) {
                synchronized(lock) { if (!current(token)) return; connected = true; }
                s.setSoTimeout(16000); event(token,listener::onConnected);
            }
            if (!connected && System.currentTimeMillis() > deadline) throw new IOException("TV 리모컨 초기화 시간이 초과되었습니다.");
        }
    }
    private SSLSocket open(String host,int port,byte[] pin,int token) throws Exception {
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(identity,"remocon".toCharArray());
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException { throw new java.security.cert.CertificateException("Client trust unsupported"); }
            public void checkServerTrusted(X509Certificate[] chain,String auth) throws java.security.cert.CertificateException {
                if (chain == null || chain.length == 0 || !(chain[0].getPublicKey() instanceof RSAPublicKey)) throw new java.security.cert.CertificateException("TV RSA 인증서가 없습니다.");
                if (pin != null && !MessageDigest.isEqual(pin,chain[0].getPublicKey().getEncoded())) throw new java.security.cert.CertificateException("TV 인증서가 변경되었습니다. TV를 확인하고 다시 페어링하세요.");
            }
        };
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(keys.getKeyManagers(),new TrustManager[]{trust},new SecureRandom());
        SSLSocket s = (SSLSocket)tls.getSocketFactory().createSocket();
        synchronized(lock) { if (!current(token)) { s.close(); throw new IOException("Cancelled"); } socket = s; }
        s.setSoTimeout(10000); s.connect(new InetSocketAddress(host,port),8000); s.startHandshake(); return s;
    }
    private void send(SSLSocket s,ProtoWire.Message message) throws IOException {
        synchronized (s) { ProtoWire.writeFrame(s.getOutputStream(),message.build()); }
    }
    private void closeSocket(int token) { synchronized(lock) { if (generation == token) { connected = false; awaitingCode = false; closeSocket(); } } }
    private void closeSocket() { SSLSocket s = socket; socket = null; if (s != null) try { s.close(); } catch(IOException ignored) {} }
    private void loadIdentity() throws Exception {
        if (identity != null) return;
        File file = new File(context.getFilesDir(),"remote-identity.p12");
        KeyStore store = KeyStore.getInstance("PKCS12");
        if (file.exists()) { try(InputStream in = new FileInputStream(file)) { store.load(in,"remocon".toCharArray()); } }
        else {
            store.load(null,null); KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); KeyPair key = gen.generateKeyPair();
            long now = System.currentTimeMillis(); X500Name name = new X500Name("CN=Remocon");
            Provider provider = new BouncyCastleProvider();
            X509Certificate cert = new JcaX509CertificateConverter().setProvider(provider).getCertificate(new JcaX509v3CertificateBuilder(name,new BigInteger(128,new SecureRandom()),new Date(now-86400000L),new Date(now+10L*365*86400000L),name,key.getPublic()).build(new JcaContentSignerBuilder("SHA256withRSA").setProvider(provider).build(key.getPrivate())));
            store.setKeyEntry("remote",key.getPrivate(),"remocon".toCharArray(),new java.security.cert.Certificate[]{cert});
            File temp = new File(context.getFilesDir(),"remote-identity.tmp");
            try(FileOutputStream out = new FileOutputStream(temp)) { store.store(out,"remocon".toCharArray()); out.getFD().sync(); }
            if (!temp.renameTo(file)) throw new IOException("리모컨 인증서를 저장하지 못했습니다.");
        }
        clientCertificate = (X509Certificate)store.getCertificate("remote"); identity = store;
    }
}
