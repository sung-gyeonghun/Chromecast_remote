package com.morluto.remocon;

import android.net.nsd.*;
import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.net.*;
import java.util.concurrent.*;

/** Exercises the Google Cast advertisement route, not just the Remote-v2 route. */
@RunWith(AndroidJUnit4.class)
public final class DiscoveryTest {
    private android.app.Instrumentation getInstrumentation(){return InstrumentationRegistry.getInstrumentation();}
    @Test public void testFindsGoogleCastAsRemoteCandidate()throws Exception{
        Context context=getInstrumentation().getTargetContext();
        context.getSharedPreferences("remocon-ui",0).edit().remove("host").commit();
        NsdManager nsd=context.getSystemService(NsdManager.class);
        CountDownLatch registered=new CountDownLatch(1),found=new CountDownLatch(1);
        String[] registrationError={null};
        NsdManager.RegistrationListener registration=new NsdManager.RegistrationListener(){
            public void onServiceRegistered(NsdServiceInfo info){registered.countDown();}
            public void onRegistrationFailed(NsdServiceInfo info,int error){registrationError[0]="registration "+error;registered.countDown();}
            public void onServiceUnregistered(NsdServiceInfo info){}
            public void onUnregistrationFailed(NsdServiceInfo info,int error){}
        };
        try(ServerSocket server=new ServerSocket(6466)){
            Thread accepting=new Thread(()->{while(!server.isClosed())try{server.accept().close();}catch(Exception ignored){}});accepting.setDaemon(true);accepting.start();
            NsdServiceInfo info=new NsdServiceInfo();info.setServiceName("RemoconDiscoveryFixture");info.setServiceType("_googlecast._tcp.");info.setPort(6466);info.setAttribute("fn","Discovery Test TV");
            nsd.registerService(info,NsdManager.PROTOCOL_DNS_SD,registration);
            assertTrue("NSD registration timed out",registered.await(10,TimeUnit.SECONDS));assertNull(registrationError[0]);
            DeviceDiscovery discovery=new DeviceDiscovery(context,new DeviceDiscovery.Listener(){
                public void onDevice(String name,String host){if(name.equals("Discovery Test TV")&&host.indexOf(':')<0)found.countDown();}
                public void onDiscoveryError(String error){}
            });
            try{getInstrumentation().runOnMainSync(discovery::start);assertTrue("Google Cast service was not resolved as an IPv4 remote candidate",found.await(18,TimeUnit.SECONDS));}
            finally{getInstrumentation().runOnMainSync(discovery::close);nsd.unregisterService(registration);}
        }
    }
}
