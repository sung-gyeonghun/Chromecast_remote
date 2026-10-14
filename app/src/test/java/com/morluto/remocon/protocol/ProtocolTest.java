package com.morluto.remocon.protocol;

import org.junit.Test;
import java.io.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import static org.junit.Assert.*;

public class ProtocolTest {
    @Test public void fragmentedAndAdjacentFramesDecodeExactly() throws Exception {
        byte[] message = new ProtoWire.Message().number(1,200).nested(40,new ProtoWire.Message().text(1,"TV")).build();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ProtoWire.writeFrame(bytes,message); ProtoWire.writeFrame(bytes,message);
        InputStream fragments = new FilterInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override public int read(byte[] b,int offset,int size) throws IOException { return super.read(b,offset,Math.min(1,size)); }
        };
        assertArrayEquals(message,ProtoWire.readFrame(fragments));
        assertArrayEquals(message,ProtoWire.readFrame(fragments));
        assertEquals(200,ProtoWire.parse(message).number(1));
        assertTrue(ProtoWire.parse(message).nested(40).has(1));
    }
    @Test public void rejectsCorruptFramesAndFields() throws Exception {
        rejects(() -> ProtoWire.readFrame(new ByteArrayInputStream(new byte[]{(byte)128})));
        rejects(() -> ProtoWire.readFrame(new ByteArrayInputStream(new byte[]{(byte)255,(byte)255,(byte)255,127})));
        rejects(() -> ProtoWire.parse(new byte[]{10,100,1}));
        rejects(() -> ProtoWire.parse(new byte[]{0}));
        rejects(() -> ProtoWire.readFrame(new ByteArrayInputStream(new byte[]{(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,(byte)255,2})));
    }
    @Test public void pairingCodeBindsBothTlsKeysAndRejectsWrongCode() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        RSAPublicKey client = (RSAPublicKey)generator.generateKeyPair().getPublic();
        RSAPublicKey server = (RSAPublicKey)generator.generateKeyPair().getPublic();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(PairingSecret.unsigned(client.getModulus())); digest.update(PairingSecret.unsigned(client.getPublicExponent()));
        digest.update(PairingSecret.unsigned(server.getModulus())); digest.update(PairingSecret.unsigned(server.getPublicExponent()));
        digest.update(new byte[]{0x12,0x34}); byte[] expected = digest.digest();
        String code = String.format("%02X1234",expected[0]&255);
        assertArrayEquals(expected,PairingSecret.compute(client,server,code));
        rejects(() -> PairingSecret.compute(client,server,String.format("%02X1234",(expected[0]+1)&255)));
        rejects(() -> PairingSecret.compute(client,server,"zzzzzz"));
        assertArrayEquals(new byte[]{(byte)0x80},PairingSecret.unsigned(new java.math.BigInteger("128")));
        assertArrayEquals(new byte[]{1,0,1},PairingSecret.unsigned(java.math.BigInteger.valueOf(65537)));
    }
    private interface Attempt { void run() throws Exception; }
    private static void rejects(Attempt attempt) throws Exception {
        try { attempt.run(); } catch(IOException | IllegalArgumentException expected) { return; }
        fail("Malformed input accepted");
    }
}
