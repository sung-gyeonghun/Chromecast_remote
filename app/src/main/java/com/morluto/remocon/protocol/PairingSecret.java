package com.morluto.remocon.protocol;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;

/** Polo pairing challenge binds the displayed code to both TLS identities. */
public final class PairingSecret {
    private PairingSecret() {}
    public static byte[] compute(RSAPublicKey client, RSAPublicKey server, String code) throws Exception {
        if (code == null || !code.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException("TV에 표시된 6자리 영문·숫자 코드를 입력하세요.");
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(unsigned(client.getModulus())); hash.update(unsigned(client.getPublicExponent()));
        hash.update(unsigned(server.getModulus())); hash.update(unsigned(server.getPublicExponent()));
        hash.update(new byte[] {(byte)Integer.parseInt(code.substring(2,4),16), (byte)Integer.parseInt(code.substring(4,6),16)});
        byte[] secret = hash.digest();
        if ((secret[0] & 255) != Integer.parseInt(code.substring(0,2),16)) throw new IllegalArgumentException("페어링 코드가 맞지 않습니다. TV의 코드를 다시 확인하세요.");
        return secret;
    }
    static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray(); return bytes.length > 1 && bytes[0] == 0 ? Arrays.copyOfRange(bytes,1,bytes.length) : bytes;
    }
}
