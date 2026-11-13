package com.limelight.bedmirror;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record NearbyPc(String name, String address, String fingerprint, String remoteAddress) {
    NearbyPc(String name, String address, String fingerprint) {
        this(name, address, fingerprint, "");
    }

    private static final int PORT = 47996;
    private static final String PROTOCOL = "nyxlink/1";

    static String token() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    static String fingerprint(X509Certificate certificate) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
        StringBuilder result = new StringBuilder(64);
        for (byte value : hash) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    static boolean validCode(String code) {
        if (!code.matches("[0-9]{5}")) return false;
        int sum = 0;
        for (int i = 0; i < 4; i++) sum += code.charAt(i) - '0';
        return sum % 10 == code.charAt(4) - '0';
    }

    static List<NearbyPc> discover(List<String> savedAddresses) throws Exception {
        Set<InetAddress> targets = new HashSet<>();
        targets.add(InetAddress.getByName("255.255.255.255"));
        var interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            var adapter = interfaces.nextElement();
            if (!adapter.isUp() || adapter.isLoopback()) continue;
            for (var network : adapter.getInterfaceAddresses()) {
                if (network.getBroadcast() != null) targets.add(network.getBroadcast());
            }
        }
        for (String address : savedAddresses) {
            if (address.isEmpty()) continue;
            try {
                CompanionClient.requireLocal(address);
                targets.add(InetAddress.getByName(address));
            } catch (Exception ignored) { }
        }

        var found = new LinkedHashMap<String, NearbyPc>();
        String token = token();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (InetAddress target : targets) {
                try { send(socket, target, "discover", token); }
                catch (java.io.IOException ignored) { }
            }
            long deadline = android.os.SystemClock.elapsedRealtime() + 1000;
            while (android.os.SystemClock.elapsedRealtime() < deadline) {
                socket.setSoTimeout((int) Math.max(1, deadline - android.os.SystemClock.elapsedRealtime()));
                try {
                    DatagramPacket reply = receive(socket);
                    JSONObject data = response(reply, token);
                    String fingerprint = data.optString("fingerprint");
                    String name = data.optString("name").trim();
                    String remoteAddr = data.optString("remoteAddress").trim();
                    if (fingerprint.matches("[0-9a-f]{64}") && !name.isEmpty() && name.length() <= 64) {
                        found.putIfAbsent(fingerprint, new NearbyPc(name, reply.getAddress().getHostAddress(), fingerprint, remoteAddr));
                    }
                } catch (SocketTimeoutException end) { break; }
                catch (Exception ignored) { }
            }
        }
        return new ArrayList<>(found.values());
    }

    String requestPairing(String token) throws Exception {
        InetAddress peer = InetAddress.getByName(address);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(peer, PORT);
            socket.setSoTimeout(800);
            for (int retry = 0; retry < 3; retry++) {
                send(socket, peer, "pair", token);
                try { return response(receive(socket), token).getString("status"); }
                catch (SocketTimeoutException ignored) { }
            }
        }
        throw new java.io.IOException("The PC did not answer. Check that NyxLink is running on the same network.");
    }

    void cancelPairing(String token) {
        try (DatagramSocket socket = new DatagramSocket()) {
            send(socket, InetAddress.getByName(address), "cancel", token);
        } catch (Exception ignored) { }
    }

    private static void send(DatagramSocket socket, InetAddress address, String command, String token) throws Exception {
        byte[] bytes = (PROTOCOL + " " + command + " " + token).getBytes(StandardCharsets.US_ASCII);
        socket.send(new DatagramPacket(bytes, bytes.length, address, PORT));
    }

    private static DatagramPacket receive(DatagramSocket socket) throws java.io.IOException {
        byte[] bytes = new byte[768];
        DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
        socket.receive(packet);
        return packet;
    }

    private static JSONObject response(DatagramPacket packet, String token) throws Exception {
        if (!(packet.getAddress() instanceof Inet4Address) || packet.getPort() != PORT) throw new java.io.IOException("Invalid discovery source");
        CompanionClient.requireLocal(packet.getAddress().getHostAddress());
        JSONObject data = new JSONObject(new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8));
        if (!PROTOCOL.equals(data.optString("protocol")) || !token.equals(data.optString("token"))) throw new java.io.IOException("Discovery reply does not match request");
        return data;
    }
}
