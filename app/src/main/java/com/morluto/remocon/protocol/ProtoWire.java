package com.morluto.remocon.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Minimal bounded protobuf wire codec for the fields used by Android TV Remote v2. */
public final class ProtoWire {
    public static final int MAX_FRAME = 1024 * 1024;
    private ProtoWire() {}
    public static final class Message {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        public Message number(int field, long value) { varint(bytes, ((long)field << 3)); varint(bytes, value); return this; }
        public Message data(int field, byte[] value) { varint(bytes, ((long)field << 3) | 2); varint(bytes, value.length); bytes.write(value, 0, value.length); return this; }
        public Message text(int field, String value) { return data(field, value.getBytes(StandardCharsets.UTF_8)); }
        public Message nested(int field, Message value) { return data(field, value.build()); }
        public byte[] build() { return bytes.toByteArray(); }
    }
    public static final class Fields {
        private final Map<Integer, Long> numbers = new HashMap<>();
        private final Map<Integer, byte[]> blobs = new HashMap<>();
        public boolean has(int field) { return numbers.containsKey(field) || blobs.containsKey(field); }
        public long number(int field) { return numbers.getOrDefault(field, 0L); }
        public byte[] data(int field) { return blobs.getOrDefault(field, new byte[0]); }
        public Fields nested(int field) throws IOException { return parse(data(field)); }
    }
    public static Fields parse(byte[] bytes) throws IOException {
        Fields result = new Fields(); ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        while (in.available() > 0) {
            long tag = readVarint(in); int field = (int)(tag >>> 3); int wire = (int)(tag & 7);
            if (field <= 0) throw new IOException("Invalid protobuf field");
            if (wire == 0) result.numbers.put(field, readVarint(in));
            else if (wire == 2) {
                long count = readVarint(in);
                if (count > in.available() || count > MAX_FRAME || count < 0) throw new IOException("Invalid protobuf length");
                byte[] value = new byte[(int)count]; readFully(in, value); result.blobs.put(field, value);
            } else if (wire == 1 || wire == 5) {
                int count = wire == 1 ? 8 : 4;
                if (in.skip(count) != count) throw new EOFException();
            } else throw new IOException("Unsupported protobuf wire type");
        }
        return result;
    }
    public static byte[] readFrame(InputStream in) throws IOException {
        long size = readVarint(in);
        if (size < 0 || size > MAX_FRAME) throw new IOException("Remote frame too large");
        byte[] bytes = new byte[(int)size]; readFully(in, bytes); return bytes;
    }
    public static void writeFrame(OutputStream out, byte[] bytes) throws IOException {
        if (bytes.length > MAX_FRAME) throw new IOException("Remote frame too large");
        ByteArrayOutputStream prefix = new ByteArrayOutputStream(); varint(prefix, bytes.length);
        out.write(prefix.toByteArray()); out.write(bytes); out.flush();
    }
    private static void readFully(InputStream in, byte[] bytes) throws IOException {
        int position = 0;
        while (position < bytes.length) { int n = in.read(bytes, position, bytes.length-position); if (n < 0) throw new EOFException("Remote closed connection"); position += n; }
    }
    static void varint(ByteArrayOutputStream out, long value) {
        while ((value & ~0x7fL) != 0) { out.write(((int)value & 0x7f) | 0x80); value >>>= 7; } out.write((int)value);
    }
    static long readVarint(InputStream in) throws IOException {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int b = in.read(); if (b < 0) throw new EOFException("Incomplete protobuf varint");
            if (shift == 63 && (b & 0xfe) != 0) throw new IOException("Overflowing protobuf varint");
            value |= (long)(b & 0x7f) << shift; if ((b & 0x80) == 0) return value;
        }
        throw new IOException("Malformed protobuf varint");
    }
}
