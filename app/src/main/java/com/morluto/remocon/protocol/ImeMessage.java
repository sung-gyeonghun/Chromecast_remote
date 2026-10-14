package com.morluto.remocon.protocol;

/** Remote v2 batch edit using the TV's current IME and text-field counters. */
public final class ImeMessage {
    private ImeMessage() { }
    public static byte[] encode(String text,int imeCounter,int fieldCounter){
        if(text==null||text.isEmpty()||text.length()>1000)throw new IllegalArgumentException("Invalid remote text length");
        int selection=text.length()-1;
        ProtoWire.Message value=new ProtoWire.Message().number(1,selection).number(2,selection).text(3,text);
        ProtoWire.Message edit=new ProtoWire.Message().number(1,1).nested(2,value);
        return new ProtoWire.Message().nested(21,new ProtoWire.Message().number(1,imeCounter).number(2,fieldCounter).nested(3,edit)).build();
    }
}
