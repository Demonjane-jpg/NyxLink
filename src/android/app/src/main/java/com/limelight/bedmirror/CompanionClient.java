package com.limelight.bedmirror;

import android.content.Context;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.nvstream.http.LimelightCryptoProvider;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetAddress;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.*;
import okhttp3.*;

/** Client and server identities are the same certificates authenticated by Sunshine pairing. */
public final class CompanionClient {
    public static final int MODE_AUTOMATIC = 0;
    public static final int MODE_LOCAL_ONLY = 1;
    public static final int MODE_REMOTE_ACCESS = 2;

    public static final String STATUS_CONNECTED_LOCALLY = "Connected Locally";
    public static final String STATUS_CONNECTED_REMOTELY = "Connected Remotely";
    public static final String STATUS_CONNECTING = "Connecting";
    public static final String STATUS_RECONNECTING = "Reconnecting";
    public static final String STATUS_UNAVAILABLE = "Connection Unavailable";

    private final OkHttpClient client;
    private final String base;
    private final String hostAddress;

    public CompanionClient(Context context, String address, X509Certificate server) throws Exception {
        this(context, address, server, "");
    }

    public CompanionClient(Context context, String address, X509Certificate server, String identity) throws Exception {
        String trimmed = address == null ? "" : address.trim();
        requireLocal(trimmed);
        if (server == null) throw new IOException("Pair with this PC first.");
        this.hostAddress = trimmed;
        LimelightCryptoProvider crypto = new AndroidCryptoProvider(context, identity);
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null);
        store.setKeyEntry("client", crypto.getClientPrivateKey(), new char[0], new java.security.cert.Certificate[]{crypto.getClientCertificate()});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(store, new char[0]);
        X509TrustManager trust = new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{server}; }
            public void checkClientTrusted(X509Certificate[] c, String a) throws java.security.cert.CertificateException { throw new java.security.cert.CertificateException("Server only"); }
            public void checkServerTrusted(X509Certificate[] c, String a) throws java.security.cert.CertificateException {
                if (c.length == 0 || !Arrays.equals(c[0].getEncoded(), server.getEncoded())) throw new java.security.cert.CertificateException("PC identity changed. Pair again on the PC.");
            }
        };
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), new TrustManager[]{trust}, null);
        client = new OkHttpClient.Builder().sslSocketFactory(ssl.getSocketFactory(), trust)
                .hostnameVerifier((h,s) -> true) // The exact paired certificate is checked above.
                .connectTimeout(4, TimeUnit.SECONDS).readTimeout(35, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).proxy(java.net.Proxy.NO_PROXY).build();
        base = "https://" + trimmed + ":47995/v1/";
    }

    public String address() {
        return hostAddress;
    }

    public static String modeName(int mode) {
        if (mode == MODE_LOCAL_ONLY) return "Local Only";
        if (mode == MODE_REMOTE_ACCESS) return "Remote Access";
        return "Automatic";
    }

    public static boolean isLanBytes(byte[] a) {
        if (a == null || a.length != 4) return false;
        int first = a[0] & 255, second = a[1] & 255;
        return first == 10 || (first == 192 && second == 168) || (first == 172 && second >= 16 && second <= 31) || (first == 169 && second == 254);
    }

    public static boolean isRemotePrivateBytes(byte[] a) {
        if (a == null || a.length != 4) return false;
        int first = a[0] & 255, second = a[1] & 255;
        return first == 100 && second >= 64 && second <= 127;
    }

    public static boolean isRemotePrivateAddress(String address) {
        if (address == null || address.trim().isEmpty()) return false;
        try {
            return isRemotePrivateBytes(InetAddress.getByName(address.trim()).getAddress());
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isTrustedPrivateAddress(String address) {
        if (address == null || address.trim().isEmpty()) return false;
        try {
            byte[] a = InetAddress.getByName(address.trim()).getAddress();
            return isLanBytes(a) || isRemotePrivateBytes(a);
        } catch (Exception e) {
            return false;
        }
    }

    public static void requireLocal(String address) throws IOException {
        if (address == null || address.trim().isEmpty()) {
            throw new IOException("Enter a private IPv4 LAN or Tailscale address.");
        }
        byte[] a = InetAddress.getByName(address.trim()).getAddress();
        if (!isLanBytes(a) && !isRemotePrivateBytes(a)) {
            throw new IOException("Enter a private IPv4 LAN or Tailscale (100.x.y.z) address.");
        }
    }

    public JSONObject get(String route) throws Exception { return call(client, new Request.Builder().url(base + route).get().build()); }
    public JSONObject get(String route, int timeoutMillis) throws Exception {
        OkHttpClient timed = client.newBuilder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
        return call(timed, new Request.Builder().url(base + route).get().build());
    }
    public JSONObject post(String route, JSONObject body) throws Exception { return call(client, new Request.Builder().url(base + route).post(RequestBody.create(body.toString(), MediaType.get("application/json"))).build()); }
    private JSONObject call(OkHttpClient httpClient, Request request) throws Exception {
        try (Response response = httpClient.newCall(request).execute()) {
            String text = response.body() == null ? "{}" : response.body().string();
            if (!response.isSuccessful()) throw new IOException("PC request failed (" + response.code() + "): " + text);
            return text.isEmpty() ? new JSONObject() : new JSONObject(text);
        }
    }
}
