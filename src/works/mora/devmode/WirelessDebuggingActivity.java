package works.mora.devmode;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * Kablosuz hata ayıklama detay sayfası. Samsung, sistemdeki detay sayfasının başka uygulamalardan
 * açılmasına izin vermediği için aynı bilgiler burada telefonun kendi verilerinden gösterilir:
 * IP adresi bağlantı özelliklerinden, port ise telefonun yerel ağa yaptığı adb mDNS duyurusundan
 * (_adb-tls-connect._tcp) okunur ve gerçekten açık olduğu yoklanır.
 */
public class WirelessDebuggingActivity extends Activity {

    private static final String ADB_SERVICE = "_adb-tls-connect._tcp";

    private OneUi ui;
    private OneUi.Row master, nameRow, addressRow, pairRow;
    private TextView info, command;
    private CompoundButton.OnCheckedChangeListener masterListener;

    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private final List<NsdManager.ServiceInfoCallback> callbacks = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private String ip;
    private int port = -1;

    private final ContentObserver observer = new ContentObserver(main) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            render();
            restartDiscovery();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new OneUi(this);
        LinearLayout content = ui.scaffold("Kablosuz hata ayıklama", this::finish);
        try {
            nsd = getSystemService(NsdManager.class);
        } catch (RuntimeException e) {
            nsd = null; // Port gösterilemez ama sayfa çalışır.
        }

        master = ui.masterBar();
        masterListener = (b, on) -> {
            Reconciler.setItem(this, Reconciler.Item.WIFI, on);
            render();
        };
        master.sw.setOnCheckedChangeListener(masterListener);
        content.addView(master.view, ui.cardParams(ui.dp(4)));

        info = ui.description("");
        content.addView(info);

        content.addView(ui.category("Bu cihaz"));
        LinearLayout deviceCard = ui.card();
        nameRow = ui.row("Cihaz adı", false, false);
        addressRow = ui.row("IP adresi ve bağlantı noktası", false, false);
        addressRow.view.setOnClickListener(v -> copyCommand());
        ui.addRows(deviceCard, nameRow, addressRow);
        content.addView(deviceCard, ui.cardParams(0));

        content.addView(ui.category("Bilgisayardan bağlan"));
        LinearLayout cmdCard = ui.card();
        command = ui.text("", 14, ui.textPrimary, false);
        command.setTypeface(Typeface.MONOSPACE);
        command.setTextIsSelectable(true);
        command.setMaxLines(10);
        command.setLineSpacing(ui.dp(4), 1f);
        command.setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16));
        cmdCard.addView(command);
        content.addView(cmdCard, ui.cardParams(0));

        content.addView(ui.category("Eşleştirme"));
        LinearLayout pairCard = ui.card();
        pairRow = ui.row("Cihazı eşleştirme kodu ile eşleştir", false, false);
        pairRow.summary.setText("Eşleştirmeyi yalnızca sistem yapabilir. Geliştirici seçenekleri açılır; "
                + "oradan \"Kablosuz hata ayıklama\" yazısına dokun.");
        pairRow.view.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)));
        ui.addRows(pairCard, pairRow);
        content.addView(pairCard, ui.cardParams(0));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Reconciler.ADB_WIFI_ENABLED), false, observer);
        render();
        restartDiscovery();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(observer);
        stopDiscovery();
    }

    private void render() {
        boolean on = Reconciler.isOn(this, Reconciler.Item.WIFI);
        boolean abOn = Reconciler.hasAutoBlocker(this)
                && Boolean.TRUE.equals(Reconciler.readAutoBlocker(this));
        ip = findWifiIpv4();

        OneUi.setChecked(master.sw, on, masterListener);
        ui.setMasterState(master, on, on ? "Açık" : "Kapalı");
        master.setEnabled(!abOn);

        info.setText(abOn
                ? "Auto Blocker açıkken Samsung kablosuz hata ayıklamayı kapatır. Önce Auto Blocker'ı kapat."
                : "Bilgisayarın bu telefona Wi‑Fi üzerinden adb ile bağlanmasını sağlar. Yalnızca güvendiğin "
                + "ve daha önce eşleştirdiğin ağlarda kullan; ağ değişince sistem kapatabilir.");
        info.setTextColor(abOn ? ui.warn : ui.textSecondary);

        String name = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        nameRow.summary.setText(name == null ? "-" : name);
        nameRow.summary.setTextColor(ui.accent);

        String address;
        if (!on) address = "Kablosuz hata ayıklama kapalı";
        else if (ip == null) address = "Wi‑Fi bağlantısı yok";
        else if (port < 0) address = ip + (nsd == null ? " · bağlantı noktası okunamadı"
                : " · bağlantı noktası aranıyor…");
        else address = ip + ":" + port + " · komutu kopyalamak için dokun";
        addressRow.summary.setText(address);
        addressRow.summary.setTextColor(on && ip != null ? ui.accent : ui.textSecondary);
        addressRow.view.setEnabled(on && ip != null && port > 0);

        String target = (ip == null ? "<IP>" : ip) + ":" + (port > 0 ? port : "<PORT>");
        command.setText("# İlk kez: telefonda \"eşleştirme kodu ile eşleştir\"\n"
                + "adb pair <IP>:<EŞLEŞTİRME_PORTU>\n\n"
                + "# Sonra bağlan\nadb connect " + target);
    }

    private void copyCommand() {
        if (ip == null || port < 0) return;
        String cmd = "adb connect " + ip + ":" + port;
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("adb", cmd));
        Toast.makeText(this, "Kopyalandı: " + cmd, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------- IP ve port

    private String findWifiIpv4() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        for (Network n : cm.getAllNetworks()) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(n);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue;
            LinkProperties lp = cm.getLinkProperties(n);
            if (lp == null) continue;
            for (LinkAddress la : lp.getLinkAddresses()) {
                if (la.getAddress() instanceof Inet4Address) return la.getAddress().getHostAddress();
            }
        }
        return null;
    }

    /** Telefonun kendi IP'sindeki portu yoklar (dışarıya bağlantı değil). */
    private static boolean isOpen(String host, int port) {
        try (java.net.Socket sock = new java.net.Socket()) {
            sock.connect(new java.net.InetSocketAddress(host, port), 500);
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private void restartDiscovery() {
        stopDiscovery();
        port = -1;
        if (nsd == null || !Reconciler.isOn(this, Reconciler.Item.WIFI)) return;
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onStartDiscoveryFailed(String t, int e) { }
            @Override public void onStopDiscoveryFailed(String t, int e) { }
            @Override public void onDiscoveryStarted(String t) { }
            @Override public void onDiscoveryStopped(String t) { }
            @Override public void onServiceLost(NsdServiceInfo s) { }

            @Override
            public void onServiceFound(NsdServiceInfo s) {
                NsdManager.ServiceInfoCallback cb = new NsdManager.ServiceInfoCallback() {
                    @Override public void onServiceInfoCallbackRegistrationFailed(int e) { }
                    @Override public void onServiceLost() { }
                    @Override public void onServiceInfoCallbackUnregistered() { }

                    @Override
                    public void onServiceUpdated(NsdServiceInfo info) {
                        // Ağdaki başka telefonların duyuruları da gelir; sadece kendi IP'mizle eşleşeni al.
                        // Eski (kapanmış) duyurular da kalabildiği için port gerçekten açık mı diye bak.
                        String own = findWifiIpv4();
                        for (InetAddress addr : info.getHostAddresses()) {
                            if (own != null && own.equals(addr.getHostAddress())) {
                                int candidate = info.getPort();
                                new Thread(() -> {
                                    if (isOpen(own, candidate)) main.post(() -> {
                                        port = candidate;
                                        render();
                                    });
                                }).start();
                                return;
                            }
                        }
                    }
                };
                try {
                    nsd.registerServiceInfoCallback(s, getMainExecutor(), cb);
                    synchronized (callbacks) {
                        callbacks.add(cb);
                    }
                } catch (RuntimeException ignored) {
                    // Aynı servis için ikinci kayıt vb.; port zaten diğer kayıttan gelir.
                }
            }
        };
        try {
            nsd.discoverServices(ADB_SERVICE, NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (RuntimeException e) {
            discovery = null;
        }
    }

    private void stopDiscovery() {
        if (nsd == null) return;
        synchronized (callbacks) {
            for (NsdManager.ServiceInfoCallback cb : callbacks) {
                try {
                    nsd.unregisterServiceInfoCallback(cb);
                } catch (RuntimeException ignored) { }
            }
            callbacks.clear();
        }
        if (discovery != null) {
            try {
                nsd.stopServiceDiscovery(discovery);
            } catch (RuntimeException ignored) { }
            discovery = null;
        }
    }
}
