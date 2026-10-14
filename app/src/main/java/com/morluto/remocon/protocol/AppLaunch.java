package com.morluto.remocon.protocol;

/** Deep links handled by the corresponding TV apps, not by the phone. */
public final class AppLaunch {
    public static final String YOUTUBE="https://www.youtube.com";
    public static final String NETFLIX="netflix://";
    private AppLaunch() { }
    public static byte[] encode(String link) {
        if(!YOUTUBE.equals(link)&&!NETFLIX.equals(link))throw new IllegalArgumentException("Unsupported TV app link");
        return new ProtoWire.Message().nested(90,new ProtoWire.Message().text(1,link)).build();
    }
}
