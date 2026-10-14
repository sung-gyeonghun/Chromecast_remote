package com.morluto.remocon;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Serial NSD resolution keeps Android's older single-resolution API reliable. */
public final class DeviceDiscovery {
    public interface Listener { void onDevice(String name, String host); void onDiscoveryError(String message); }
    private final NsdManager nsd;
    private final WifiManager wifi;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final ArrayDeque<NsdServiceInfo> pending = new ArrayDeque<>();
    private final Set<String> seen = new HashSet<>();
    private NsdManager.DiscoveryListener discovery;
    private WifiManager.MulticastLock lock;
    private boolean resolving;
    private int generation;

    public DeviceDiscovery(Context context, Listener listener) {
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        this.listener = listener;
    }
    public void start() {
        stop();
        int ticket = generation;
        try {
            lock = wifi.createMulticastLock("remocon-discovery");
            lock.setReferenceCounted(false); lock.acquire();
            discovery = new NsdManager.DiscoveryListener() {
                public void onDiscoveryStarted(String type) { }
                public void onDiscoveryStopped(String type) { }
                public void onStartDiscoveryFailed(String type, int error) { main.post(() -> {
                    if (ticket == generation) { stop(); listener.onDiscoveryError("자동 검색을 시작하지 못했어요. IP 주소로 연결해 주세요."); }
                }); }
                public void onStopDiscoveryFailed(String type, int error) { }
                public void onServiceLost(NsdServiceInfo service) { }
                public void onServiceFound(NsdServiceInfo service) { main.post(() -> {
                    if (ticket != generation || !seen.add(service.getServiceName())) return;
                    pending.add(service); resolveNext(ticket);
                }); }
            };
            nsd.discoverServices("_androidtvremote2._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery);
            main.postDelayed(() -> { if (ticket == generation) stop(); }, 20000);
        } catch (RuntimeException e) { stop(); listener.onDiscoveryError("자동 검색이 불가능해요. 같은 Wi-Fi인지 확인하거나 IP 주소를 입력해 주세요."); }
    }
    @SuppressWarnings("deprecation")
    private void resolveNext(int ticket) {
        if (ticket != generation || resolving || pending.isEmpty()) return;
        resolving = true;
        NsdServiceInfo next = pending.remove();
        try { nsd.resolveService(next, new NsdManager.ResolveListener() {
            public void onResolveFailed(NsdServiceInfo service, int error) { main.post(() -> complete(ticket)); }
            public void onServiceResolved(NsdServiceInfo service) { main.post(() -> {
                if (ticket != generation) return;
                if (service.getHost() != null) listener.onDevice(service.getServiceName(), service.getHost().getHostAddress());
                complete(ticket);
            }); }
        }); } catch (RuntimeException e) { complete(ticket); }
    }
    private void complete(int ticket) { if (ticket == generation) { resolving = false; resolveNext(ticket); } }
    public void stop() {
        generation++; pending.clear(); seen.clear(); resolving = false;
        if (discovery != null) { try { nsd.stopServiceDiscovery(discovery); } catch (RuntimeException ignored) { } discovery = null; }
        if (lock != null && lock.isHeld()) lock.release(); lock = null;
    }
}
