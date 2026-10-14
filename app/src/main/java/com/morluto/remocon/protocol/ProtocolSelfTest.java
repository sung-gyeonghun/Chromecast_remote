package com.morluto.remocon.protocol;

import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/** Host-side smoke tests; run with java -ea. No Android runtime needed. */
public final class ProtocolSelfTest {
    public static void main(String[] args) throws Exception {
        byte[] payload = new ProtoWire.Message().number(1,200).nested(40,new ProtoWire.Message().text(1,"test")).build();
        ByteArrayOutputStream stream = new ByteArrayOutputStream(); ProtoWire.writeFrame(stream,payload); ProtoWire.writeFrame(stream,payload);
        InputStream fragmented = new FilterInputStream(new ByteArrayInputStream(stream.toByteArray())) {
            public int read(byte[] b,int offset,int size) throws IOException { return super.read(b,offset,Math.min(1,size)); }
        };
        assert Arrays.equals(payload,ProtoWire.readFrame(fragmented)); assert Arrays.equals(payload,ProtoWire.readFrame(fragmented));
        assert ProtoWire.parse(payload).number(1) == 200;
        assert ProtoWire.parse(payload).nested(40).has(1);
        rejects(() -> ProtoWire.readFrame(new ByteArrayInputStream(new byte[]{(byte)128})));
        rejects(() -> ProtoWire.parse(new byte[]{10,100,1}));
        rejects(() -> ProtoWire.readFrame(new ByteArrayInputStream(new byte[]{(byte)255,(byte)255,(byte)255,127})));
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        RSAPublicKey client = (RSAPublicKey)generator.generateKeyPair().getPublic(); RSAPublicKey server = (RSAPublicKey)generator.generateKeyPair().getPublic();
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        sha.update(PairingSecret.unsigned(client.getModulus())); sha.update(PairingSecret.unsigned(client.getPublicExponent()));
        sha.update(PairingSecret.unsigned(server.getModulus())); sha.update(PairingSecret.unsigned(server.getPublicExponent())); sha.update(new byte[]{0x12,0x34});
        byte[] expected = sha.digest(); String code = String.format("%02X1234",expected[0]&255);
        assert Arrays.equals(expected,PairingSecret.compute(client,server,code));
        rejects(() -> PairingSecret.compute(client,server,String.format("%02X1234",(expected[0]+1)&255)));
        rejects(() -> PairingSecret.compute(client,server,"zzzzzz"));
        System.out.println("Protocol self-test passed: fragmented frames, multiple frames, malformed input, pairing challenge.");
    }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); } catch (IOException | IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed input accepted");
    }
}
