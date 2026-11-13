package com.limelight.bedmirror;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Typeface;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import com.limelight.Game;
import com.limelight.binding.crypto.AndroidCryptoProvider;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.http.PairingManager;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class HomeActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService scanner = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, SavedPc> saved = new LinkedHashMap<>();
    private final Runnable scanAgain = this::scan;
    private SharedPreferences prefs;
    private SharedPreferences pairings;
    private LinearLayout pcRows;
    private TextView status;
    private boolean foreground;
    private boolean scanning;
    private boolean busy;
    private Dialog codeDialog;
    private List<NearbyPc> nearby = List.of();
    private final java.util.Set<String> reachableLocal = new java.util.HashSet<>();
    private final java.util.Set<String> reachableRemote = new java.util.HashSet<>();
    private String currentConnectionStatus = CompanionClient.STATUS_UNAVAILABLE;

    private record SavedPc(NearbyPc pc, X509Certificate certificate, String identity, String remoteAddress) {
        SavedPc(NearbyPc pc, X509Certificate certificate, String identity) {
            this(pc, certificate, identity, pc.remoteAddress() != null ? pc.remoteAddress() : "");
        }
        String localAddress() {
            return pc.address() != null ? pc.address() : "";
        }
        boolean hasRemoteAddress() {
            return remoteAddress != null && !remoteAddress.trim().isEmpty();
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("bedmirror", MODE_PRIVATE);
        pairings = getSharedPreferences("nyxlink-pcs", MODE_PRIVATE);
        loadPairings();
        android.preference.PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putBoolean("checkbox_enable_sops", false)
                .putBoolean("checkbox_host_audio", true)
                .putBoolean("checkbox_stretch_video", false)
                .putBoolean("checkbox_enable_hdr", false)
                .putBoolean("checkbox_enable_osc", false)
                .putBoolean("checkbox_enable_usb_driver", false)
                .apply();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(16), dp(18), dp(24));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(new HomeBackground());
        getWindow().setStatusBarColor(Color.rgb(2, 5, 25));
        getWindow().setNavigationBarColor(Color.rgb(2, 5, 25));
        scroll.addView(root);
        setContentView(scroll);

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER);
        TextView moon = text("☾", 43);
        moon.setTextColor(0xff39dfff);
        moon.setShadowLayer(dp(10), 0, 0, 0xff8d36ff);
        brand.addView(moon);
        TextView title = text("NyxLink", 30);
        brand.addView(title);
        root.addView(brand);
        TextView heading = text("Nearby PCs", 23);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        pcRows = new LinearLayout(this);
        pcRows.setOrientation(LinearLayout.VERTICAL);
        root.addView(pcRows);
        root.addView(actionButton("⟳", "Refresh nearby PCs", this::refreshNearby));
        root.addView(actionButton("☷", "Streaming settings", this::settings));
        status = text("Open NyxLink on your PC and connect both devices to the same network.", 16);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(26), dp(12), dp(14));
        root.addView(status);
        Button help = NyxLinkTutorial.createHomeHelpButton(this, prefs);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-2, -2);
        helpParams.gravity = Gravity.CENTER_HORIZONTAL;
        helpParams.setMargins(0, dp(6), 0, dp(4));
        root.addView(help, helpParams);
        renderPcs();
        NyxReportingManager.init(this);
        NyxLinkTutorial.maybeShowFirstLaunch(this, prefs);
    }

    private void loadPairings() {
        for (var entry : pairings.getAll().entrySet()) {
            try {
                JSONObject data = new JSONObject((String) entry.getValue());
                X509Certificate cert = certificate(data.getString("certificate"));
                String fingerprint = NearbyPc.fingerprint(cert);
                if (fingerprint.equals(entry.getKey())) {
                    String remoteAddr = data.optString("remoteAddress", "").trim();
                    saved.put(fingerprint, new SavedPc(
                            new NearbyPc(data.getString("name"), data.getString("address"), fingerprint, remoteAddr),
                            cert,
                            data.getString("identity"),
                            remoteAddr
                    ));
                }
            } catch (Exception ignored) { }
        }

        // Import the original single-PC pairing without requesting another code.
        String legacy = prefs.getString("serverCert", null);
        if (legacy == null) return;
        try {
            X509Certificate cert = certificate(legacy);
            String fingerprint = NearbyPc.fingerprint(cert);
            if (!saved.containsKey(fingerprint)) {
                SavedPc record = new SavedPc(new NearbyPc("Saved PC", prefs.getString("pairedHost", prefs.getString("host", "")), fingerprint), cert, "");
                persist(record);
                saved.put(fingerprint, record);
            }
            prefs.edit().remove("serverCert").remove("pairedHost").apply();
        } catch (Exception ignored) { }
    }

    private static X509Certificate certificate(String encoded) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(Base64.decode(encoded, Base64.DEFAULT)));
    }

    private void persist(SavedPc record) throws Exception {
        String remoteAddr = record.remoteAddress() == null ? "" : record.remoteAddress().trim();
        JSONObject data = new JSONObject()
                .put("name", record.pc().name())
                .put("address", record.pc().address())
                .put("remoteAddress", remoteAddr)
                .put("certificate", Base64.encodeToString(record.certificate().getEncoded(), Base64.NO_WRAP))
                .put("identity", record.identity());
        if (!pairings.edit().putString(record.pc().fingerprint(), data.toString()).commit()) {
            throw new IOException("Couldn't save the PC pairing on this phone.");
        }
    }

    private void scan() {
        if (!foreground || scanning) return;
        ui.removeCallbacks(scanAgain);
        scanning = true;
        boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
        int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
        List<String> addresses = new ArrayList<>();
        List<SavedPc> snapshot = new ArrayList<>(saved.values());
        for (SavedPc pc : snapshot) {
            if (mode != CompanionClient.MODE_REMOTE_ACCESS || !remoteEnabled) {
                addresses.add(pc.localAddress());
            }
            if (remoteEnabled && mode != CompanionClient.MODE_LOCAL_ONLY && pc.hasRemoteAddress()) {
                addresses.add(pc.remoteAddress());
            }
        }
        scanner.execute(() -> {
            List<NearbyPc> result;
            try { result = NearbyPc.discover(addresses); }
            catch (Exception e) { result = List.of(); }
            java.util.Set<String> localFound = new java.util.HashSet<>();
            java.util.Set<String> remoteFound = new java.util.HashSet<>();
            for (NearbyPc item : result) {
                if (CompanionClient.isRemotePrivateAddress(item.address())) {
                    remoteFound.add(item.fingerprint());
                } else {
                    localFound.add(item.fingerprint());
                }
            }
            if (remoteEnabled && mode != CompanionClient.MODE_LOCAL_ONLY) {
                for (SavedPc s : snapshot) {
                    String fp = s.pc().fingerprint();
                    if (!localFound.contains(fp) && !remoteFound.contains(fp) && s.hasRemoteAddress()) {
                        try {
                            JSONObject st = new CompanionClient(this, s.remoteAddress(), s.certificate(), s.identity()).get("status", 1500);
                            if (st.optBoolean("running") || st.optBoolean("privacy")) {
                                remoteFound.add(fp);
                            }
                        } catch (Exception ignored) { }
                    }
                }
            }
            final List<NearbyPc> found = result;
            ui.post(() -> {
                scanning = false;
                if (!foreground || isDestroyed()) return;
                boolean changed = !nearby.equals(found)
                        || !reachableLocal.equals(localFound)
                        || !reachableRemote.equals(remoteFound);
                nearby = found;
                reachableLocal.clear();
                reachableLocal.addAll(localFound);
                reachableRemote.clear();
                reachableRemote.addAll(remoteFound);
                for (NearbyPc item : found) {
                    SavedPc existing = saved.get(item.fingerprint());
                    if (existing != null) {
                        String newLocal = CompanionClient.isRemotePrivateAddress(item.address()) ? existing.localAddress() : item.address();
                        String newRemote = existing.hasRemoteAddress() ? existing.remoteAddress() : item.remoteAddress();
                        if (!newLocal.equals(existing.localAddress()) || !newRemote.equals(existing.remoteAddress())) {
                            SavedPc updated = new SavedPc(new NearbyPc(item.name(), newLocal, item.fingerprint(), newRemote), existing.certificate(), existing.identity(), newRemote);
                            saved.put(item.fingerprint(), updated);
                            try { persist(updated); } catch (Exception ignored) { }
                            changed = true;
                        }
                    }
                }
                if (changed) {
                    renderPcs();
                }
                if (status.getText().toString().equals("Looking for nearby PCs…")) {
                    status.setText(found.isEmpty() && remoteFound.isEmpty()
                            ? "No nearby PCs found. Check that NyxLink is running on your PC."
                            : "Nearby PCs updated.");
                }
                ui.postDelayed(scanAgain, 3000);
            });
        });
    }

    private void refreshNearby() {
        status.setText("Looking for nearby PCs…");
        scan();
    }

    private void renderPcs() {
        pcRows.removeAllViews();
        var visible = new LinkedHashMap<String, NearbyPc>();
        for (NearbyPc pc : nearby) visible.put(pc.fingerprint(), pc);
        for (SavedPc pc : saved.values()) visible.putIfAbsent(pc.pc().fingerprint(), pc.pc());
        if (visible.isEmpty()) pcRows.addView(text("Looking for PCs on this network…", 16));
        boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
        int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
        for (NearbyPc pc : visible.values()) {
            SavedPc paired = saved.get(pc.fingerprint());
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(10), dp(14), dp(8));
            card.setBackgroundResource(com.limelight.R.drawable.nyx_button);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, dp(10), 0, dp(4));
            pcRows.addView(card, cardParams);

            boolean isLocal = reachableLocal.contains(pc.fingerprint());
            boolean isRemote = remoteEnabled && mode != CompanionClient.MODE_LOCAL_ONLY && reachableRemote.contains(pc.fingerprint());
            String badgeText;
            if (paired == null) {
                badgeText = "◉  Nearby";
            } else if (isLocal && (!remoteEnabled || mode != CompanionClient.MODE_REMOTE_ACCESS)) {
                badgeText = "◉  Nearby · Disconnected (Local Ready)";
            } else if (isRemote) {
                badgeText = "◉  Remote Access · Disconnected (Remote Ready)";
            } else {
                badgeText = "♧  Saved PC · Unavailable" + (paired.hasRemoteAddress() ? " (" + paired.remoteAddress() + ")" : " (Offline)");
            }
            TextView badge = text(badgeText, 12);
            badge.setTextColor(0xff78eaff);
            card.addView(badge);
            LinearLayout device = new LinearLayout(this);
            device.setGravity(Gravity.CENTER_VERTICAL);
            TextView icon = text("▣", 28);
            icon.setTextColor(Color.WHITE);
            icon.setShadowLayer(dp(6), 0, 0, 0xff36d8ff);
            device.addView(icon);
            TextView name = text(pc.name(), 17);
            name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            name.setPadding(dp(12), 0, dp(4), 0);
            name.setMaxLines(2);
            device.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button connect = button(paired == null ? "Pair" : "Connect", () -> select(pc, paired));
            connect.setAllCaps(false);
            connect.setContentDescription(pc.name() + (paired == null ? " · Pair" : " · Connect"));
            device.addView(connect);
            card.addView(device);
            if (paired != null) {
                View divider = new View(this);
                divider.setBackgroundColor(0x806EB3D6);
                card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
                Button forget = button("♧   Forget device · " + pc.name() + "   ›", () -> confirmForget(pc, paired));
                forget.setAllCaps(false);
                forget.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                forget.setBackgroundColor(Color.TRANSPARENT);
                card.addView(forget, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }

    private Button actionButton(String icon, String label, Runnable action) {
        Button result = button(icon + "     " + label + "     ›", action);
        result.setAllCaps(false);
        result.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(8), 0, 0);
        result.setLayoutParams(params);
        return result;
    }

    private void select(NearbyPc pc, SavedPc paired) {
        if (busy || codeDialog != null) return;
        if (paired != null) {
            String localAddr = CompanionClient.isRemotePrivateAddress(pc.address()) && !paired.localAddress().isEmpty()
                    ? paired.localAddress()
                    : pc.address();
            String remoteAddr = paired.hasRemoteAddress() ? paired.remoteAddress() : pc.remoteAddress();
            currentConnectionStatus = CompanionClient.STATUS_CONNECTING;
            work("Connecting to " + pc.name() + "…",
                    () -> connect(new SavedPc(new NearbyPc(pc.name(), localAddr, pc.fingerprint(), remoteAddr), paired.certificate(), paired.identity(), remoteAddr)));
            return;
        }
        String token = NearbyPc.token();
        work("Requesting a pairing code…", () -> {
            String result = pc.requestPairing(token);
            if (!result.equals("ready")) throw new IOException("This PC is busy or unavailable. Try again in a moment.");
            ui.post(() -> {
                if (foreground && !isFinishing() && !isDestroyed()) showCodeDialog(pc, token);
                else if (!scanner.isShutdown()) scanner.execute(() -> pc.cancelPairing(token));
            });
        });
    }

    private void showCodeDialog(NearbyPc pc, String token) {
        codeDialog = NyxMenuDialog.showPairingCode(
                this,
                "Pair with " + pc.name(),
                "Enter the five-digit code showing on your PC.",
                "Pair",
                "Stop pairing",
                (code, input) -> {
                    if (!NearbyPc.validCode(code)) {
                        input.setError("Check the five digits on your PC.");
                        return false;
                    }
                    work("Pairing with " + pc.name() + "…", () -> pair(pc, token, code));
                    return true;
                },
                () -> {
                    codeDialog = null;
                    if (!scanner.isShutdown()) {
                        scanner.execute(() -> pc.cancelPairing(token));
                        status.setText("Pairing stopped.");
                    }
                }
        );
    }

    private void pair(NearbyPc pc, String token, String code) throws Exception {
        AndroidCryptoProvider crypto = new AndroidCryptoProvider(this, token);
        boolean remembered = false;
        try {
            NvHTTP http = http(pc.address(), null, crypto);
            http.configureNyxLinkPairing(token);
            PairingManager manager = new PairingManager(http, crypto);
            if (manager.pair(http.getServerInfo(true), code.substring(0, 4)) != PairingManager.PairState.PAIRED) {
                throw new IOException("Pairing failed. Select the PC again for a new code.");
            }
            X509Certificate cert = manager.getPairedCert();
            if (!NearbyPc.fingerprint(cert).equals(pc.fingerprint())) {
                throw new IOException("The PC identity changed. Refresh nearby PCs and try again.");
            }
            SavedPc record = new SavedPc(pc, cert, token, pc.remoteAddress());
            JSONObject st = client(record).get("status");
            String reportedRemote = st.optString("remoteAddress", "").trim();
            if (!reportedRemote.isEmpty() && !record.hasRemoteAddress()) {
                record = new SavedPc(new NearbyPc(pc.name(), pc.address(), pc.fingerprint(), reportedRemote), cert, token, reportedRemote);
            }
            persist(record);
            remembered = true;
            final SavedPc pairedRecord = record;
            ui.post(() -> {
                saved.put(pc.fingerprint(), pairedRecord);
                renderPcs();
            });
            connect(pairedRecord);
        } finally {
            if (!remembered) {
                pc.cancelPairing(token);
                crypto.forget();
            }
        }
    }

    private void confirmForget(NearbyPc pc, SavedPc paired) {
        if (busy) return;
        NyxMenuDialog.showConfirmation(
                this,
                "Forget " + pc.name() + "?",
                "You'll need a new code to pair with this PC again.",
                "Forget device",
                "Cancel",
                () -> work("Forgetting device…", () -> {
                    boolean removedOnPc;
                    try {
                        client(new SavedPc(pc, paired.certificate(), paired.identity(), paired.remoteAddress())).post("forget", new JSONObject());
                        removedOnPc = true;
                    } catch (Exception unavailable) { removedOnPc = false; }
                    if (!pairings.edit().remove(pc.fingerprint()).commit()) throw new IOException("Couldn't remove this pairing from the phone.");
                    new AndroidCryptoProvider(this, paired.identity()).forget();
                    String message = removedOnPc ? "Device forgotten." :
                            "Device forgotten on this phone. Remove its old entry in NyxLink on the PC when available.";
                    ui.post(() -> {
                        saved.remove(pc.fingerprint());
                        renderPcs();
                        status.setText(message);
                    });
                })
        );
    }

    private CompanionClient client(SavedPc record) throws Exception {
        return new CompanionClient(this, record.pc().address(), record.certificate(), record.identity());
    }

    private NvHTTP http(String address, X509Certificate certificate, AndroidCryptoProvider crypto) throws IOException {
        return new NvHTTP(new ComputerDetails.AddressTuple(address, 47989), 47984, "", certificate, crypto);
    }

    private void connect(SavedPc record) throws Exception {
        boolean remoteEnabled = prefs.getBoolean("remoteAccessEnabled", false);
        int mode = prefs.getInt("connectionMode", CompanionClient.MODE_AUTOMATIC);
        String localAddr = record.localAddress();
        String remoteAddr = record.hasRemoteAddress()
                ? record.remoteAddress().trim()
                : (CompanionClient.isRemotePrivateAddress(localAddr) ? localAddr : "");

        CompanionClient client = null;
        JSONObject state = null;
        String activeAddress;
        boolean connectedRemotely;

        if (!remoteEnabled) {
            if (mode == CompanionClient.MODE_REMOTE_ACCESS) {
                throw new IOException("Remote Access is disabled. Enable Remote Access in Streaming settings first.");
            }
            if (CompanionClient.isRemotePrivateAddress(localAddr)) {
                throw new IOException("Remote Access is disabled. Connect to your home Wi-Fi or enable Remote Access in Streaming settings.");
            }
            activeAddress = localAddr;
            connectedRemotely = false;
            client = new CompanionClient(this, activeAddress, record.certificate(), record.identity());
            state = client.get("status");
        } else if (mode == CompanionClient.MODE_LOCAL_ONLY) {
            if (CompanionClient.isRemotePrivateAddress(localAddr)) {
                throw new IOException("Connection Mode is Local Only. Switch to Automatic or Remote Access to connect remotely.");
            }
            activeAddress = localAddr;
            connectedRemotely = false;
            client = new CompanionClient(this, activeAddress, record.certificate(), record.identity());
            state = client.get("status");
        } else if (mode == CompanionClient.MODE_REMOTE_ACCESS) {
            if (remoteAddr.isEmpty()) {
                throw new IOException("No remote address configured for " + record.pc().name() + ". Configure it in Streaming settings → Remote Access.");
            }
            activeAddress = remoteAddr;
            connectedRemotely = true;
            client = new CompanionClient(this, activeAddress, record.certificate(), record.identity());
            state = client.get("status");
        } else {
            // Automatic (Recommended): prefer direct local connection when available, fall back to authorized remote connection
            Exception localError = null;
            if (!localAddr.isEmpty() && !CompanionClient.isRemotePrivateAddress(localAddr)) {
                try {
                    CompanionClient localClient = new CompanionClient(this, localAddr, record.certificate(), record.identity());
                    state = localClient.get("status", 1800);
                    client = localClient;
                } catch (Exception e) {
                    localError = e;
                }
            }
            if (client != null && state != null) {
                activeAddress = localAddr;
                connectedRemotely = false;
            } else if (!remoteAddr.isEmpty()) {
                activeAddress = remoteAddr;
                connectedRemotely = true;
                client = new CompanionClient(this, activeAddress, record.certificate(), record.identity());
                state = client.get("status");
            } else if (localError != null) {
                throw localError;
            } else {
                throw new IOException("Could not reach " + record.pc().name() + ".");
            }
        }

        if (state.optBoolean("privacy")) client.post("resume", new JSONObject());
        if (!state.optBoolean("running") && !state.optBoolean("privacy")) throw new IOException("Start NyxLink on the PC and try again.");
        NyxReportingManager.flushPendingQueue(this, client);
        String reportedRemote = state.optString("remoteAddress", "").trim();
        if (!reportedRemote.isEmpty() && !record.hasRemoteAddress()) {
            record = new SavedPc(new NearbyPc(record.pc().name(), record.localAddress(), record.pc().fingerprint(), reportedRemote), record.certificate(), record.identity(), reportedRemote);
        }
        JSONObject listing = client.get("monitors");
        var monitors = listing.getJSONArray("monitors");
        if (monitors.length() == 0) throw new IOException("No active physical monitors detected.");
        JSONObject monitor = monitors.getJSONObject(0);
        for (int i = 0; i < monitors.length(); i++) {
            JSONObject candidate = monitors.getJSONObject(i);
            if (candidate.getString("id").equals(listing.optString("selected"))) {
                monitor = candidate;
                break;
            }
        }
        NvHTTP http = http(activeAddress, record.certificate(), new AndroidCryptoProvider(this, record.identity()));
        NvApp desktop = null;
        for (NvApp app : http.getAppList()) {
            if (app.getAppName().equals("NyxLink Desktop") || app.getAppName().equals("BedMirror Desktop")) desktop = app;
        }
        if (desktop == null) throw new IOException("NyxLink Desktop is missing from the PC configuration.");
        ComputerDetails pc = http.getComputerDetails(true);
        persist(record);
        prefs.edit()
                .putString("monitor", monitor.getString("id"))
                .putInt("monitorWidth", monitor.getInt("width"))
                .putInt("monitorHeight", monitor.getInt("height"))
                .apply();
        Intent intent = new Intent(this, Game.class)
                .putExtra(Game.EXTRA_HOST, activeAddress)
                .putExtra(Game.EXTRA_PORT, 47989)
                .putExtra(Game.EXTRA_HTTPS_PORT, 47984)
                .putExtra(Game.EXTRA_APP_NAME, desktop.getAppName())
                .putExtra(Game.EXTRA_APP_ID, desktop.getAppId())
                .putExtra(Game.EXTRA_UNIQUEID, record.identity().isEmpty() ? "0123456789ABCDEF" : record.identity().substring(0, 16))
                .putExtra(Game.EXTRA_PC_UUID, pc.uuid)
                .putExtra(Game.EXTRA_PC_NAME, record.pc().name())
                .putExtra(Game.EXTRA_APP_HDR, false)
                .putExtra(Game.EXTRA_SERVER_CERT, record.certificate().getEncoded())
                .putExtra(Game.EXTRA_NYXLINK_IDENTITY, record.identity())
                .putExtra("com.limelight.NyxLinkLocalHost", record.localAddress())
                .putExtra("com.limelight.NyxLinkRemoteHost", record.remoteAddress())
                .putExtra("com.limelight.NyxLinkFingerprint", record.pc().fingerprint())
                .putExtra("com.limelight.NyxLinkRemoteSession", connectedRemotely);
        final SavedPc finalRecord = record;
        final boolean isRemote = connectedRemotely;
        ui.post(() -> {
            currentConnectionStatus = isRemote ? CompanionClient.STATUS_CONNECTED_REMOTELY : CompanionClient.STATUS_CONNECTED_LOCALLY;
            saved.put(finalRecord.pc().fingerprint(), finalRecord);
            renderPcs();
            status.setText(isRemote ? "Connected Remotely to " + finalRecord.pc().name() + "." : "Ready to connect.");
            if (foreground && !isFinishing() && !isDestroyed()) startActivity(intent);
        });
    }

    private interface Work { void run() throws Exception; }

    private void work(String message, Work action) {
        if (busy) return;
        busy = true;
        status.setText(message);
        worker.execute(() -> {
            try { action.run(); }
            catch (Exception e) {
                String error = e instanceof javax.net.ssl.SSLException
                        ? "The PC no longer accepts this pairing. If it was revoked, choose Forget device and pair again."
                        : e.getMessage();
                ui.post(() -> {
                    currentConnectionStatus = CompanionClient.STATUS_UNAVAILABLE;
                    if (!isDestroyed()) status.setText(error == null ? "Connection failed. Try again." : error);
                });
            } finally { ui.post(() -> busy = false); }
        });
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(Color.WHITE);
        view.setTextSize(size);
        view.setPadding(0, 12, 0, 12);
        return view;
    }

    private Button button(String label, Runnable action) {
        Button button = new Button(this);
        int left = button.getPaddingLeft(), top = button.getPaddingTop();
        int right = button.getPaddingRight(), bottom = button.getPaddingBottom();
        button.setBackgroundTintList(null);
        button.setBackgroundResource(com.limelight.R.drawable.nyx_button);
        button.setPadding(left, top, right, bottom);
        button.setTextColor(Color.WHITE);
        button.setText(label);
        button.setMinHeight(52);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private String remoteConfigSummary() {
        if (saved.isEmpty()) {
            return "Remote Connectivity: Not configured (No paired PC yet)";
        }
        StringBuilder sb = new StringBuilder();
        for (SavedPc pc : saved.values()) {
            if (pc.hasRemoteAddress()) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(pc.pc().name()).append(" (").append(pc.remoteAddress()).append(")");
            }
        }
        if (sb.length() == 0) {
            return "Remote Connectivity: Not configured";
        }
        return "Remote Connectivity: Configured — " + sb;
    }

    private void configureRemotePc() {
        if (saved.isEmpty()) {
            NyxMenuDialog.showMessage(
                    this,
                    "Configure Authorized Remote PC",
                    "No paired PCs found on this phone.\n\nPair with your Windows PC on the same local Wi-Fi first using its 5-digit code. Once paired, return here to configure its private remote address (e.g. Tailscale 100.x.y.z).",
                    "Close"
            );
            return;
        }
        List<SavedPc> list = new ArrayList<>(saved.values());
        if (list.size() == 1) {
            editRemoteAddress(list.get(0));
            return;
        }
        String[] labels = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            SavedPc item = list.get(i);
            labels[i] = item.pc().name() + (item.hasRemoteAddress() ? " · " + item.remoteAddress() : " · Not configured");
        }
        NyxMenuDialog.showActionList(this, "Choose a paired PC", labels, index -> editRemoteAddress(list.get(index)));
    }

    private void editRemoteAddress(SavedPc target) {
        NyxMenuDialog.showTextInput(
                this,
                "Remote Access — " + target.pc().name(),
                "Enter the private remote IPv4 address for " + target.pc().name() + " (for example, its Tailscale address 100.x.y.z). Leave empty to clear.",
                target.remoteAddress(),
                "100.x.y.z (Tailscale / private IP)",
                "Save",
                "Cancel",
                input -> {
                    String trimmed = input == null ? "" : input.trim();
                    if (trimmed.isEmpty()) {
                        SavedPc cleared = new SavedPc(
                                new NearbyPc(target.pc().name(), target.localAddress(), target.pc().fingerprint(), ""),
                                target.certificate(),
                                target.identity(),
                                ""
                        );
                        try {
                            persist(cleared);
                            saved.put(cleared.pc().fingerprint(), cleared);
                            renderPcs();
                            status.setText("Cleared remote address for " + target.pc().name() + ".");
                        } catch (Exception e) {
                            status.setText(e.getMessage());
                        }
                        return;
                    }
                    worker.execute(() -> {
                        try {
                            CompanionClient.requireLocal(trimmed);
                            SavedPc updated = new SavedPc(
                                    new NearbyPc(target.pc().name(), target.localAddress(), target.pc().fingerprint(), trimmed),
                                    target.certificate(),
                                    target.identity(),
                                    trimmed
                            );
                            persist(updated);
                            ui.post(() -> {
                                saved.put(updated.pc().fingerprint(), updated);
                                renderPcs();
                                status.setText("Saved remote address " + trimmed + " for " + target.pc().name() + ".");
                                scan();
                            });
                        } catch (Exception e) {
                            ui.post(() -> status.setText("Invalid remote address: " + e.getMessage()));
                        }
                    });
                }
        );
    }

    private void settings() {
        String displayedStatus = busy ? CompanionClient.STATUS_CONNECTING : CompanionClient.STATUS_UNAVAILABLE;
        CompanionClient companion = null;
        if (!saved.isEmpty()) {
            try {
                companion = client(saved.values().iterator().next());
            } catch (Exception ignored) {}
        }
        NyxMenuDialog.showStreamingSettings(
                this,
                prefs,
                true,
                displayedStatus,
                this::remoteConfigSummary,
                this::configureRemotePc,
                companion,
                () -> {
                    renderPcs();
                    scan();
                }
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == NyxMenuDialog.REQUEST_PICK_SCREENSHOT && resultCode == RESULT_OK && data != null && data.getData() != null) {
            NyxMenuDialog.onScreenshotResult(this, data.getData());
        }
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (!busy) currentConnectionStatus = CompanionClient.STATUS_UNAVAILABLE;
        loadPairings();
        renderPcs();
        scan();
    }

    @Override protected void onPause() {
        foreground = false;
        ui.removeCallbacks(scanAgain);
        super.onPause();
    }

    @Override protected void onDestroy() {
        NyxLinkTutorial.dismissActive();
        if (codeDialog != null) codeDialog.dismiss();
        worker.shutdown();
        scanner.shutdown();
        super.onDestroy();
    }
}
