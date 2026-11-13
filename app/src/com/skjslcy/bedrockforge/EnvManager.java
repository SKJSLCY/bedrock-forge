package com.skjslcy.bedrockforge;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnvManager {

    private EnvManager() {
    }

    public static File baseDir(Context ctx) {
        return new File(ctx.getFilesDir(), "mcbds");
    }

    public static File serverDir(Context ctx) {
        return new File(baseDir(ctx), "server12662");
    }

    public static boolean isInstalled(Context ctx) {
        java.io.File bin = new java.io.File(
                "/data/local/tmp/box64/rootfs/usr/local/bin/box64");
        java.io.File ready = new java.io.File("/data/local/tmp/box64/.mcbds_ready");
        return bin.isFile() && ready.isFile();
    }

    public static Map<String, String> readProperties(Context ctx) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        File f = new File(serverDir(ctx), "server.properties");
        if (!f.isFile()) return map;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) continue;
                int eq = t.indexOf('=');
                if (eq <= 0) continue;
                map.put(t.substring(0, eq).trim(), t.substring(eq + 1).trim());
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) try { reader.close(); } catch (Exception ignored) { }
        }
        return map;
    }

    public static void writeProperties(Context ctx, Map<String, String> updates) throws IOException {
        File f = new File(serverDir(ctx), "server.properties");
        if (!f.isFile()) throw new IOException("server.properties 不存在");
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) lines.add(line);
        } finally {
            try { reader.close(); } catch (Exception ignored) { }
        }

        Map<String, String> remain = new LinkedHashMap<String, String>(updates);
        List<String> out = new ArrayList<String>();
        for (String line : lines) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) {
                int eq = t.indexOf('=');
                if (eq > 0) {
                    String key = t.substring(0, eq).trim();
                    if (remain.containsKey(key)) {
                        out.add(key + "=" + remain.remove(key));
                        continue;
                    }
                }
            }
            out.add(line);
        }
        for (Map.Entry<String, String> e : remain.entrySet()) {
            out.add(e.getKey() + "=" + e.getValue());
        }

        File tmp = new File(f.getParentFile(), "server.properties.tmp");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), "UTF-8"));
        try {
            for (String line : out) {
                writer.write(line);
                writer.write("\n");
            }
        } finally {
            try { writer.close(); } catch (Exception ignored) { }
        }
        if (!tmp.renameTo(f)) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            if (!tmp.renameTo(f)) throw new IOException("无法写入 server.properties");
        }
    }

    public static String getLanIp(Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            Network[] networks = cm.getAllNetworks();
            String fallback = null;
            for (Network n : networks) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(n);
                if (nc == null) continue;
                boolean lan = nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                        || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET);
                LinkProperties lp = cm.getLinkProperties(n);
                if (lp == null) continue;
                for (LinkAddress la : lp.getLinkAddresses()) {
                    InetAddress a = la.getAddress();
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        String ip = a.getHostAddress();
                        if (lan) return ip;
                        if (fallback == null) fallback = ip;
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Throwable ignored) {
        }
        return getLanIp();
    }

    public static String getLanIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            if (ifaces == null) return null;
            List<NetworkInterface> list = Collections.list(ifaces);
            // prefer wlan0
            for (NetworkInterface nif : list) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String name = nif.getName();
                if (!"wlan0".equals(name) && !name.startsWith("wlan")) continue;
                String ip = firstIpv4(nif);
                if (ip != null) return ip;
            }
            for (NetworkInterface nif : list) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                String ip = firstIpv4(nif);
                if (ip != null) return ip;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String firstIpv4(NetworkInterface nif) {
        Enumeration<InetAddress> addrs = nif.getInetAddresses();
        while (addrs.hasMoreElements()) {
            InetAddress a = addrs.nextElement();
            if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                return a.getHostAddress();
            }
        }
        return null;
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
