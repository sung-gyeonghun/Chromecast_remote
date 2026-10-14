import com.morluto.remocon.protocol.ProtoWire;
import com.morluto.remocon.protocol.PairingSecret;
import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import javax.net.ssl.*;
import java.util.concurrent.*;

/** TEST ONLY: local simulated TV. Trusts test client certificates, never use in production.
 * java MockTv <PKCS12 path> <password> [bind address]
 * Bind 127.0.0.1 for emulator's 10.0.2.2, or a LAN address only for deliberate device tests.
 */
public final class MockTv {
    private static X509Certificate serverCertificate;
    private static final java.util.Set<String> paired = ConcurrentHashMap.newKeySet();
    public static void main(String[] args) throws Exception {
        if(args.length < 2) throw new IllegalArgumentException("Usage: MockTv identity.p12 password [127.0.0.1]");
        KeyStore store = KeyStore.getInstance("PKCS12");
        try(InputStream in = new FileInputStream(args[0])) { store.load(in,args[1].toCharArray()); }
        String alias = store.aliases().nextElement(); serverCertificate = (X509Certificate)store.getCertificate(alias);
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()); keys.init(store,args[1].toCharArray());
        X509TrustManager testTrust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain,String type) throws java.security.cert.CertificateException {
                if(chain.length == 0 || !(chain[0].getPublicKey() instanceof RSAPublicKey)) throw new java.security.cert.CertificateException("Expected RSA client");
            }
            public void checkServerTrusted(X509Certificate[] chain,String type) { throw new UnsupportedOperationException(); }
        };
        SSLContext tls = SSLContext.getInstance("TLS"); tls.init(keys.getKeyManagers(),new TrustManager[]{testTrust},new SecureRandom());
        String bind = args.length > 2 ? args[2] : "127.0.0.1";
        ExecutorService threads = Executors.newCachedThreadPool();
        for(int port : new int[]{6467,6466}) {
            SSLServerSocket listener = (SSLServerSocket)tls.getServerSocketFactory().createServerSocket(port,16,java.net.InetAddress.getByName(bind));
            listener.setNeedClientAuth(true);
            threads.submit(() -> {
                for(;;) try {
                    SSLSocket socket = (SSLSocket)listener.accept();
                    threads.submit(() -> {
                        try(SSLSocket s = socket) {
                            s.setSoTimeout(120000); s.startHandshake();
                            if(port == 6467) pair(s); else control(s);
                        } catch(Exception error) { log("ERROR="+port+":"+error); }
                    });
                } catch(Exception error) { log("ACCEPT_ERROR="+error); break; }
            });
        }
        log("MOCK_READY="+bind+":6467,6466");
    }
    private static String clientKey(SSLSocket s) throws Exception {
        return java.util.Base64.getEncoder().encodeToString(s.getSession().getPeerCertificates()[0].getPublicKey().getEncoded());
    }
    private static void pair(SSLSocket s) throws Exception {
        ProtoWire.Fields request = receive(s); poloCheck(request,10);
        check(new String(request.nested(10).data(1),java.nio.charset.StandardCharsets.UTF_8).equals("atvremote"),"service_name");
        send(s,polo(11,new ProtoWire.Message().text(1,"Mock Chromecast")));
        ProtoWire.Fields options = receive(s); poloCheck(options,20);
        check(options.nested(20).number(3)==1,"input role"); checkEncoding(options.nested(20).nested(1));
        send(s,polo(20,new ProtoWire.Message().nested(2,new ProtoWire.Message().number(1,3).number(2,6)).number(3,2)));
        ProtoWire.Fields configure = receive(s); poloCheck(configure,30);
        check(configure.nested(30).number(2)==1,"configuration role"); checkEncoding(configure.nested(30).nested(1));
        RSAPublicKey client = (RSAPublicKey)s.getSession().getPeerCertificates()[0].getPublicKey();
        RSAPublicKey server = (RSAPublicKey)serverCertificate.getPublicKey();
        byte[] expected = digest(client,server,new byte[]{0x12,0x34});
        String code = String.format("%02X1234",expected[0]&255);
        check(MessageDigest.isEqual(expected,PairingSecret.compute(client,server,code)),"reference challenge computation");
        log("PAIRING_CODE="+code);
        send(s,polo(31,new ProtoWire.Message()));
        ProtoWire.Fields secret = receive(s); poloCheck(secret,40);
        if(!MessageDigest.isEqual(expected,secret.nested(40).data(1))) {
            send(s,new ProtoWire.Message().number(1,2).number(2,402)); throw new IOException("Wrong secret");
        }
        paired.add(clientKey(s)); send(s,polo(41,new ProtoWire.Message().data(1,expected))); log("PAIRING_ACCEPTED");
    }
    private static byte[] digest(RSAPublicKey client,RSAPublicKey server,byte[] challenge) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for(java.math.BigInteger value : new java.math.BigInteger[]{client.getModulus(),client.getPublicExponent(),server.getModulus(),server.getPublicExponent()}) {
            byte[] bytes = value.toByteArray(); if(bytes[0]==0) bytes = java.util.Arrays.copyOfRange(bytes,1,bytes.length); digest.update(bytes);
        }
        digest.update(challenge); return digest.digest();
    }
    private static void control(SSLSocket s) throws Exception {
        if(!paired.contains(clientKey(s))) {
            // Restarting the mock with the SAME certificate allows an app's persisted pin reconnect.
            log("CONTROL_CLIENT_NOT_PAIRED_THIS_RUN");
        }
        send(s,new ProtoWire.Message().nested(1,new ProtoWire.Message().number(1,103).nested(2,new ProtoWire.Message().text(1,"Chromecast Mock").text(2,"Local Test"))));
        ProtoWire.Fields config = receive(s); check(config.has(1),"remote_configure");
        check(config.nested(1).number(1)==103,"features 103 with IME"); check(config.nested(1).nested(2).number(3)==1,"device unknown1");
        send(s,new ProtoWire.Message().nested(2,new ProtoWire.Message().number(1,103)));
        ProtoWire.Fields active = receive(s); check(active.has(2) && active.nested(2).number(1)==103,"remote_set_active");
        send(s,new ProtoWire.Message().nested(20,new ProtoWire.Message().nested(1,new ProtoWire.Message().text(12,"mock.search"))));
        send(s,new ProtoWire.Message().nested(21,new ProtoWire.Message().number(1,17).number(2,42)));
        send(s,new ProtoWire.Message().nested(40,new ProtoWire.Message().number(1,1)));
        log("CONTROL_CONNECTED"); s.setSoTimeout(10000);
        int counter = 1;
        for(;;) {
            send(s,new ProtoWire.Message().nested(8,new ProtoWire.Message().number(1,counter).number(2,0)));
            boolean pong = false;
            while(!pong) {
                ProtoWire.Fields message = receive(s);
                if(message.has(9)) { check(message.nested(9).number(1)==counter,"ping id"); pong=true; log("PING_OK="+counter); }
                if(message.has(10)) { check(message.nested(10).number(2)==3,"short key"); log("KEY="+message.nested(10).number(1)); }
                if(message.has(21)) checkText(message.nested(21));
            }
            counter++;
            // Use socket timeout as a bounded wait, also collecting keys between pings.
            long until = System.currentTimeMillis()+4000;
            while(System.currentTimeMillis()<until) {
                s.setSoTimeout((int)Math.max(1,until-System.currentTimeMillis()));
                try {
                    ProtoWire.Fields message = receive(s);
                    if(message.has(10)) { check(message.nested(10).number(2)==3,"short key"); log("KEY="+message.nested(10).number(1)); }
                    if(message.has(21)) checkText(message.nested(21));
                } catch(java.net.SocketTimeoutException expected) { break; }
            }
            s.setSoTimeout(10000);
        }
    }
    private static void checkText(ProtoWire.Fields batch)throws IOException{
        check(batch.number(1)==17&&batch.number(2)==42,"TV IME counters");
        ProtoWire.Fields edit=batch.nested(3);check(edit.number(1)==1,"text insert");
        ProtoWire.Fields value=edit.nested(2);String text=new String(value.data(3),java.nio.charset.StandardCharsets.UTF_8);
        check(value.number(1)==text.length()-1&&value.number(2)==text.length()-1,"text positions");
        log("TEXT="+text);
    }
    private static void checkEncoding(ProtoWire.Fields field) throws IOException { check(field.number(1)==3 && field.number(2)==6,"hex encoding"); }
    private static void poloCheck(ProtoWire.Fields fields,int field) throws IOException { check(fields.number(1)==2 && fields.number(2)==200 && fields.has(field),"polo field "+field); }
    private static void check(boolean condition,String label) throws IOException { if(!condition) throw new IOException("ASSERTION_FAILED="+label); }
    private static ProtoWire.Fields receive(SSLSocket s) throws IOException { return ProtoWire.parse(ProtoWire.readFrame(s.getInputStream())); }
    private static ProtoWire.Message polo(int field,ProtoWire.Message nested) { return new ProtoWire.Message().number(1,2).number(2,200).nested(field,nested); }
    private static void send(SSLSocket s,ProtoWire.Message message) throws IOException { ProtoWire.writeFrame(s.getOutputStream(),message.build()); }
    private static synchronized void log(String value) { System.out.println(value); System.out.flush(); }
}
