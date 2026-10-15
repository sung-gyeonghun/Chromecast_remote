package com.morluto.remocon.protocol;

import java.net.Socket;
import java.util.concurrent.*;

/** TLS close_notify can perform network IO, including on an otherwise idle socket. */
final class SocketCloser {
    private static final ExecutorService IO=Executors.newFixedThreadPool(2,r->{Thread t=new Thread(r,"remocon-socket-close");t.setDaemon(true);return t;});
    private SocketCloser(){}
    static void close(Socket socket){if(socket!=null)IO.execute(()->{try{socket.close();}catch(Exception ignored){}});}
}
