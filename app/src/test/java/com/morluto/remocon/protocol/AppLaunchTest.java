package com.morluto.remocon.protocol;

import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AppLaunchTest {
    @Test public void encodesTvAppLinksInLaunchField()throws Exception{
        for(String link:new String[]{AppLaunch.YOUTUBE,AppLaunch.NETFLIX}){
            ProtoWire.Fields request=ProtoWire.parse(AppLaunch.encode(link));
            assertTrue(request.has(90));assertFalse(request.has(10));
            assertEquals(link,new String(request.nested(90).data(1),StandardCharsets.UTF_8));
        }
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsUnselectedLinks(){AppLaunch.encode("https://example.com");}
}
