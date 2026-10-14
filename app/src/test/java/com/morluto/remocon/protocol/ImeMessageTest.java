package com.morluto.remocon.protocol;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public final class ImeMessageTest {
    @Test public void preservesTvCountersAndUnicodeText() throws Exception {
        String text="한글 검색";
        ProtoWire.Fields batch=ProtoWire.parse(ImeMessage.encode(text,17,42)).nested(21);
        assertEquals(17,batch.number(1));assertEquals(42,batch.number(2));
        ProtoWire.Fields edit=batch.nested(3);assertEquals(1,edit.number(1));
        ProtoWire.Fields value=edit.nested(2);
        assertEquals(text,new String(value.data(3),StandardCharsets.UTF_8));
        assertEquals(text.length()-1,value.number(1));assertEquals(text.length()-1,value.number(2));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsEmptyText(){ImeMessage.encode("",0,0);}
}
