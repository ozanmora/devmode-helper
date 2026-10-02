package works.mora.devmode;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ana ekran. Gösterilen her değer telefonun ayarlarından o an okunur; her dokunuş telefona yazılır. */
public class MainActivity extends Activity {

    private OneUi ui;
    private OneUi.Row master, syncRow, modeNameRow, autoBlockerRow, permissionRow, widgetRow;
    private final Map<Reconciler.Item, OneUi.Row> itemRows = new EnumMap<>(Reconciler.Item.class);
    private final Map<Reconciler.Item, CompoundButton.OnCheckedChangeListener> itemListeners =
            new EnumMap<>(Reconciler.Item.class);
    private CompoundButton.OnCheckedChangeListener masterListener, syncListener;
    private TextView securityHeader;
    private OneUi.Row logRow, aboutRow;

    // Sistem ayarı değişince sadece ekranı yenile; olayları arka plan işi (ModeWatchJob) işler.
    // İki yerden işlenirse Auto Blocker değişimi iki kez sayılırdı.
    private final ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            render();
        }
    };

    // Arka plan işi bir şey değiştirdiğinde (ör. Auto Blocker durumu) ekranı yenile.
    private final SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
            (prefs, key) -> runOnUiThread(this::render);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new OneUi(this);
        LinearLayout content = ui.scaffold("Geliştirme modu", null);

        master = ui.masterBar();
        content.addView(master.view, ui.cardParams(ui.dp(4)));
        content.addView(ui.description("Açıldığında tüm geliştirici ayarları etkinleşir. "
                + "Kapatıldığında hepsi kapanır ve telefon normal kullanıma döner. "
                + "Aşağıdaki anahtarlar telefonun anlık durumunu gösterir."));

        content.addView(ui.category("Geliştirici ayarları"));
        LinearLayout devCard = ui.card();
        for (Reconciler.Item item : Reconciler.Item.values()) {
            itemRows.put(item, ui.row(item.title, true, item == Reconciler.Item.WIFI));
        }
        itemRows.get(Reconciler.Item.WIFI).textArea.setOnClickListener(
                v -> startActivity(new Intent(this, WirelessDebuggingActivity.class)));
        ui.addRows(devCard, itemRows.get(Reconciler.Item.USB), itemRows.get(Reconciler.Item.WIFI),
                itemRows.get(Reconciler.Item.STAY_ON), itemRows.get(Reconciler.Item.SCREEN_ALWAYS));
        content.addView(devCard, ui.cardParams(0));

        content.addView(ui.category("Otomasyon"));
        LinearLayout autoCard = ui.card();
        syncRow = ui.row("Samsung moduyla eşitle", true, false);
        modeNameRow = ui.row("İzlenen Samsung modu", false, false);
        modeNameRow.view.setOnClickListener(v -> editModeName());
        ui.addRows(autoCard, syncRow, modeNameRow);
        content.addView(autoCard, ui.cardParams(0));

        content.addView(ui.category("Ana ekran"));
        LinearLayout homeCard = ui.card();
        widgetRow = ui.row("Test uygulamaları widget'ı", false, false);
        widgetRow.view.setOnClickListener(v -> pinWidget());
        ui.addRows(homeCard, widgetRow);
        content.addView(homeCard, ui.cardParams(0));

        securityHeader = ui.category("Güvenlik");
        content.addView(securityHeader);
        LinearLayout securityCard = ui.card();
        autoBlockerRow = ui.row("Auto Blocker", false, false);
        autoBlockerRow.view.setOnClickListener(v -> openAutoBlocker());
        permissionRow = ui.row("Ayar yazma izni", false, false);
        permissionRow.summary.setTextIsSelectable(true);
        ui.addRows(securityCard, autoBlockerRow, permissionRow);
        content.addView(securityCard, ui.cardParams(0));

        // İkincil içerik kendi sayfalarında: olay günlüğü ve Hakkında (destek bağlantıları orada).
        LinearLayout moreCard = ui.card();
        logRow = ui.row("Olay günlüğü", false, false);
        logRow.view.setOnClickListener(v -> startActivity(new Intent(this, LogActivity.class)));
        aboutRow = ui.row("Hakkında", false, false);
        aboutRow.view.setOnClickListener(v -> startActivity(new Intent(this, AboutActivity.class)));
        ui.addRows(moreCard, logRow, aboutRow);
        content.addView(moreCard, ui.cardParams(ui.dp(24)));

        wireListeners();

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        ModeWatchJob.schedule(this);
        Reconciler.onSystemChange(this, "uygulama açıldı");
        Reconciler.updateStatusNotification(this);
        DevAppsWidget.refresh(this);
        for (String k : new String[]{Reconciler.KEY_MODE_ENABLED, Reconciler.KEY_MODE_NAME}) {
            getContentResolver().registerContentObserver(Settings.Global.getUriFor(k), false, observer);
        }
        for (Reconciler.Item i : Reconciler.Item.values()) {
            getContentResolver().registerContentObserver(i.uri(), false, observer);
        }
        Reconciler.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener);
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(observer);
        Reconciler.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener);
    }

    private void wireListeners() {
        masterListener = (b, on) -> {
            Reconciler.setAll(this, on, "Uygulamadan");
            if (on && Reconciler.isPendingOn(this)) openAutoBlocker();
            render();
        };
        master.sw.setOnCheckedChangeListener(masterListener);
        for (Map.Entry<Reconciler.Item, OneUi.Row> e : itemRows.entrySet()) {
            CompoundButton.OnCheckedChangeListener l = (b, on) -> {
                Reconciler.setItem(this, e.getKey(), on);
                render();
            };
            itemListeners.put(e.getKey(), l);
            e.getValue().sw.setOnCheckedChangeListener(l);
        }
        syncListener = (b, on) -> {
            Reconciler.setSyncWithMode(this, on);
            render();
        };
        syncRow.sw.setOnCheckedChangeListener(syncListener);
    }

    /** Her şey telefonun ayarlarından o an okunur; uygulama kendi "açık mı" bilgisini tutmaz. */
    private void render() {
        boolean hasAb = Reconciler.hasAutoBlocker(this);
        Boolean ab = hasAb ? Reconciler.readAutoBlocker(this) : Boolean.FALSE;
        boolean abOn = Boolean.TRUE.equals(ab);
        boolean all = Reconciler.allOn(this);
        boolean any = Reconciler.anyOn(this);
        boolean pending = Reconciler.isPendingOn(this);
        String modeName = Reconciler.modeName(this);

        OneUi.setChecked(master.sw, any || pending, masterListener);
        ui.setMasterState(master, any || pending,
                all ? "Açık" : pending ? "Auto Blocker kapatılması bekleniyor" : any ? "Kısmen açık" : "Kapalı");

        Boolean mode = Reconciler.readDevModeActive(this);
        ui.setSubtitle("Samsung \"" + modeName + "\" modu "
                + (mode == null ? "okunamadı" : mode ? "açık" : "kapalı"));

        for (Map.Entry<Reconciler.Item, OneUi.Row> e : itemRows.entrySet()) {
            Reconciler.Item item = e.getKey();
            OneUi.Row r = e.getValue();
            boolean locked = item.blockedByAutoBlocker && abOn;
            OneUi.setChecked(r.sw, Reconciler.isOn(this, item), itemListeners.get(item));
            r.sw.setEnabled(!locked);
            r.sw.setAlpha(locked ? 0.4f : 1f);
            // Kablosuz satırının yazı kısmı detay sayfasına gider; kilitliyken de açılabilir.
            if (item != Reconciler.Item.WIFI) r.view.setEnabled(!locked);
            String hint;
            switch (item) {
                case WIFI: hint = "Yalnızca daha önce eşleştirilmiş Wi‑Fi ağlarında çalışır"; break;
                case STAY_ON: hint = "Şarj olurken ekran kapanmaz"; break;
                case SCREEN_ALWAYS: hint = "Şarjda değilken de ekran kapanmaz; kapatınca eski zaman aşımı geri gelir"; break;
                default: hint = "adb ile USB üzerinden bağlantı";
            }
            r.summary.setText(locked ? "Auto Blocker açıkken kullanılamaz" : hint);
            r.summary.setTextColor(locked ? ui.warn : ui.textSecondary);
        }

        boolean sync = Reconciler.isSyncWithMode(this);
        OneUi.setChecked(syncRow.sw, sync, syncListener);
        syncRow.summary.setText(sync
                ? "\"" + modeName + "\" modu açılınca/kapanınca geliştirici ayarları da otomatik açılır/kapanır"
                : "Kapalı · sadece bu ekrandaki anahtar kullanılır");
        modeNameRow.summary.setText(modeName);
        modeNameRow.summary.setTextColor(ui.accent);

        securityHeader.setVisibility(hasAb ? TextView.VISIBLE : TextView.GONE);
        autoBlockerRow.view.setVisibility(hasAb ? TextView.VISIBLE : TextView.GONE);
        if (hasAb) {
            autoBlockerRow.summary.setText(ab == null
                    ? "Durum henüz bilinmiyor · Samsung ayarlarında görmek için dokun"
                    : (abOn ? "Açık · hata ayıklama engelleniyor" : "Kapalı") + " · Değiştirmek için dokun");
            autoBlockerRow.summary.setTextColor(ab == null ? ui.warn : abOn ? ui.accent : ui.textSecondary);
        }

        int devApps = DevApps.list(this).size();
        widgetRow.summary.setText(DevAppsWidget.hasWidgets(this)
                ? "Ana ekranda · " + devApps + " uygulama listeleniyor"
                : "adb ile yüklediğin uygulamaları ana ekranda listeler · Eklemek için dokun, "
                + "sonra istediğin sayfaya taşı");
        widgetRow.summary.setTextColor(DevAppsWidget.hasWidgets(this) ? ui.accent : ui.textSecondary);

        boolean perm = Reconciler.canWriteSecureSettings(this);
        permissionRow.summary.setText(perm ? "Verildi"
                : "Verilmedi · Bilgisayarda çalıştır:\nadb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS");
        permissionRow.summary.setTextColor(perm ? ui.accent : ui.warn);

        List<String> lines = Reconciler.readLog(this);
        logRow.summary.setText(lines.isEmpty() ? "Henüz olay yok" : "Son: " + lines.get(lines.size() - 1));
        aboutRow.summary.setText("Sürüm " + OneUi.versionName(this) + " · Destek ol");
    }

    private void editModeName() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(Reconciler.modeName(this));
        input.setSelection(input.getText().length());
        FrameLayout box = new FrameLayout(this);
        box.setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle("İzlenen Samsung modu")
                .setMessage("Modlar ve Rutinler'deki modun adını birebir aynı yaz.")
                .setView(box)
                .setNegativeButton("İptal", null)
                .setPositiveButton("Kaydet", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) Reconciler.setModeName(this, name);
                    render();
                })
                .show();
    }

    private void pinWidget() {
        android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(this);
        if (mgr.isRequestPinAppWidgetSupported()) {
            mgr.requestPinAppWidget(new android.content.ComponentName(this, DevAppsWidget.class), null, null);
        } else {
            android.widget.Toast.makeText(this, "Ana ekrana uzun bas › Widget'lar › DevMode Helper",
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void openAutoBlocker() {
        try {
            startActivity(Reconciler.autoBlockerIntent());
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }
}
