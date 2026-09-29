package works.mora.devmode;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.provider.Settings;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Tek doğruluk kaynağı telefonun kendi ayarlarıdır; uygulama "açık mı" bilgisini kendisi saklamaz.
 *
 *  - Samsung "Development" modu açılınca -> tüm geliştirici ayarları açılır, kapanınca -> hepsi kapanır.
 *  - Uygulamadaki her anahtar doğrudan ilgili sistem ayarını yazar.
 *  - Auto Blocker açıkken Samsung hata ayıklamayı kapatır (ve kapanınca eski hâline döndürür).
 *    Bu yüzden Auto Blocker açıkken "aç" isteği bekletilir; kullanıcı Auto Blocker'ı kapatınca uygulanır.
 */
final class Reconciler {

    /** Samsung Modes & Routines'teki modun varsayılan adı; uygulamadan değiştirilebilir. */
    static final String DEFAULT_MODE_NAME = "Development";

    // Samsung, aktif modu bu Settings.Global anahtarlarına yazıyor (One UI 8.5 / S928B üzerinde doğrulandı).
    static final String KEY_MODE_ENABLED = "mode_enabled";
    static final String KEY_MODE_NAME = "mode_display_name";
    // Auto Blocker ana anahtarı (Settings.Secure). Sadece okunur; değiştirmek Samsung'un kendi ekranına bırakılır.
    static final String KEY_AUTO_BLOCKER = "rampart_main_switch_enabled";

    static final String ADB_WIFI_ENABLED = "adb_wifi_enabled"; // Settings.Global.ADB_WIFI_ENABLED (@hide)

    private static final int STAY_ON_ALL = BatteryManager.BATTERY_PLUGGED_AC
            | BatteryManager.BATTERY_PLUGGED_USB
            | BatteryManager.BATTERY_PLUGGED_WIRELESS;

    /** Açılış sırası baştan sona; kapanış tersten (USB en son kapanır, bağlı adb oturumu o an kopar). */
    enum Item {
        USB("USB hata ayıklama", Settings.Global.ADB_ENABLED, 1, true),
        WIFI("Kablosuz hata ayıklama", ADB_WIFI_ENABLED, 1, true),
        STAY_ON("Ekranı açık tut", Settings.Global.STAY_ON_WHILE_PLUGGED_IN, STAY_ON_ALL, false);

        final String title;
        final String key;
        final int onValue;
        /** Auto Blocker açıkken Samsung tarafından kapatılır/kilitlenir. */
        final boolean blockedByAutoBlocker;

        Item(String title, String key, int onValue, boolean blockedByAutoBlocker) {
            this.title = title;
            this.key = key;
            this.onValue = onValue;
            this.blockedByAutoBlocker = blockedByAutoBlocker;
        }
    }

    private static final String PREFS = "state";
    private static final String PREF_LAST_MODE = "last_mode";
    private static final String PREF_SYNC = "sync_with_mode";
    private static final String PREF_PENDING_ON = "pending_on";
    private static final String PREF_PENDING_TRIES = "pending_tries";
    private static final String PREF_AB_STATE = "ab_state"; // -1 bilinmiyor, 0 kapalı, 1 açık
    private static final int MAX_PENDING_TRIES = 3;
    private static final String PREF_MODE_NAME = "mode_name";
    private static final String CHANNEL = "devmode";
    private static final String CHANNEL_STATUS = "status";
    private static final int NOTIF_ID = 1;
    private static final int STATUS_NOTIF_ID = 2;
    static final String ACTION_REPOST = "works.mora.devmode.REPOST_STATUS";
    static final String ACTION_TURN_OFF = "works.mora.devmode.TURN_OFF";
    private static final int MAX_LOG_LINES = 300;

    private Reconciler() {}

    // ---------------------------------------------------------------- Olaylar

    /** Sistem ayarlarından biri değişti (arka plan işi, açılış, ekran). */
    static synchronized void onSystemChange(Context ctx, String reason) {
        SharedPreferences p = prefs(ctx);

        if (isSyncWithMode(ctx)) {
            Boolean mode = readDevModeActive(ctx);
            if (mode != null && mode != p.getBoolean(PREF_LAST_MODE, false)) {
                p.edit().putBoolean(PREF_LAST_MODE, mode).apply();
                log(ctx, "Samsung \"" + modeName(ctx) + "\" modu " + (mode ? "açıldı" : "kapandı"));
                setAll(ctx, mode, "mod");
                return;
            }
        }

        // Auto Blocker kapanınca bekleyen "aç" isteğini uygula. Samsung da o an eski değerleri geri
        // yazdığı için, ayarlar gerçekten açık görünene kadar birkaç kez dene.
        if (p.getBoolean(PREF_PENDING_ON, false) && Boolean.FALSE.equals(readAutoBlocker(ctx))) {
            if (allOn(ctx)) {
                p.edit().putBoolean(PREF_PENDING_ON, false).putInt(PREF_PENDING_TRIES, 0).apply();
                log(ctx, "Auto Blocker kapandı, geliştirici ayarları açık");
                cancelNotification(ctx);
                return;
            }
            int tries = p.getInt(PREF_PENDING_TRIES, 0);
            if (tries >= MAX_PENDING_TRIES) {
                p.edit().putBoolean(PREF_PENDING_ON, false).putInt(PREF_PENDING_TRIES, 0).apply();
                log(ctx, "Bekleyen açma isteği " + MAX_PENDING_TRIES + " denemede tutmadı, bırakıldı");
                return;
            }
            p.edit().putInt(PREF_PENDING_TRIES, tries + 1).apply();
            writeAll(ctx, true);
        }
    }

    /** Ana anahtar / mod: tüm geliştirici ayarlarını aç veya kapat. */
    static synchronized void setAll(Context ctx, boolean on, String reason) {
        try {
            setAllInner(ctx, on, reason);
        } finally {
            updateStatusNotification(ctx);
        }
    }

    private static void setAllInner(Context ctx, boolean on, String reason) {
        if (!checkPermission(ctx)) return;
        SharedPreferences p = prefs(ctx);

        if (on && Boolean.TRUE.equals(readAutoBlocker(ctx))) {
            // Auto Blocker'dan etkilenmeyenleri hemen aç, gerisini beklet.
            for (Item i : Item.values()) {
                if (!i.blockedByAutoBlocker) write(ctx, i, true);
            }
            p.edit().putBoolean(PREF_PENDING_ON, true).putInt(PREF_PENDING_TRIES, 0).apply();
            log(ctx, reason + ": açılacak, ama Auto Blocker açık -> kapatılması bekleniyor");
            notify(ctx, "Auto Blocker açık",
                    "Hata ayıklama için Auto Blocker'ı kapat. Kapatınca ayarlar otomatik açılır.",
                    autoBlockerIntent());
            return;
        }

        p.edit().putBoolean(PREF_PENDING_ON, false).putInt(PREF_PENDING_TRIES, 0).commit();
        writeAll(ctx, on);
        if (on) {
            log(ctx, reason + ": geliştirici ayarları açıldı");
            cancelNotification(ctx);
        } else {
            log(ctx, reason + ": geliştirici ayarları kapatıldı (normal kullanım)");
            if (Boolean.FALSE.equals(readAutoBlocker(ctx))) {
                notify(ctx, "Normal kullanıma dönüldü",
                        "Hata ayıklama kapatıldı. Auto Blocker'ı tekrar açmak için dokun.", autoBlockerIntent());
            } else {
                cancelNotification(ctx);
            }
        }
    }

    /** Tek bir satırdaki anahtar. */
    static synchronized void setItem(Context ctx, Item item, boolean on) {
        if (!checkPermission(ctx)) return;
        if (!on) prefs(ctx).edit().putBoolean(PREF_PENDING_ON, false).apply();
        if (write(ctx, item, on)) log(ctx, "Uygulamadan: " + item.title + " " + (on ? "açıldı" : "kapatıldı"));
        updateStatusNotification(ctx);
    }

    static String modeName(Context ctx) {
        return prefs(ctx).getString(PREF_MODE_NAME, DEFAULT_MODE_NAME);
    }

    static synchronized void setModeName(Context ctx, String name) {
        SharedPreferences.Editor e = prefs(ctx).edit().putString(PREF_MODE_NAME, name.trim());
        e.commit();
        Boolean mode = readDevModeActive(ctx);
        // Yeni adla şu anki durumu "görüldü" say; ad değişti diye ayarlar değişmesin.
        if (mode != null) prefs(ctx).edit().putBoolean(PREF_LAST_MODE, mode).apply();
        log(ctx, "İzlenen Samsung modu: \"" + name.trim() + "\"");
    }

    /** Samsung Auto Blocker bu cihazda var mı (Samsung dışı cihazlarda yok). */
    static boolean hasAutoBlocker(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo("com.samsung.android.rampart", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Geliştirici ayarlarından biri açıkken kaldırılamayan durum bildirimi. Dokununca uygulama açılır.
     * Android 14+ kullanıcının "ongoing" bildirimleri kaydırmasına izin verebildiği için, silinirse
     * deleteIntent ile hemen yeniden gösterilir.
     */
    static void updateStatusNotification(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (!anyOn(ctx)) {
            nm.cancel(STATUS_NOTIF_ID);
            return;
        }
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_STATUS, "Geliştirme modu durumu", NotificationManager.IMPORTANCE_LOW));
        StringBuilder on = new StringBuilder();
        for (Item i : Item.values()) {
            if (isOn(ctx, i)) on.append(on.length() > 0 ? " · " : "").append(i.title);
        }
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent repost = new Intent(ACTION_REPOST).setClass(ctx, StatusReceiver.class);
        Intent off = new Intent(ACTION_TURN_OFF).setClass(ctx, StatusReceiver.class);
        Notification n = new Notification.Builder(ctx, CHANNEL_STATUS)
                .setSmallIcon(R.drawable.ic_stat_dev)
                .setColor(0xFF2F6BFF)
                .setContentTitle(allOn(ctx) ? "Geliştirme modu açık" : "Geliştirme modu kısmen açık")
                .setContentText(on)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE))
                .setDeleteIntent(PendingIntent.getBroadcast(ctx, 2, repost, PendingIntent.FLAG_IMMUTABLE))
                .addAction(new Notification.Action.Builder(null, "Kapat",
                        PendingIntent.getBroadcast(ctx, 3, off, PendingIntent.FLAG_IMMUTABLE)).build())
                .build();
        n.flags |= Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
        nm.notify(STATUS_NOTIF_ID, n);
    }

    static synchronized void setSyncWithMode(Context ctx, boolean sync) {
        SharedPreferences.Editor e = prefs(ctx).edit().putBoolean(PREF_SYNC, sync);
        Boolean mode = readDevModeActive(ctx);
        // Şu anki mod durumunu "görüldü" say; eşitleme açılır açılmaz ayarlar değişmesin.
        if (mode != null) e.putBoolean(PREF_LAST_MODE, mode);
        e.apply();
        log(ctx, "Samsung moduyla eşitleme " + (sync ? "açıldı" : "kapatıldı"));
    }

    // ---------------------------------------------------------------- Okuma (her zaman canlı)

    static boolean isOn(Context ctx, Item item) {
        return Settings.Global.getInt(ctx.getContentResolver(), item.key, 0) != 0;
    }

    static boolean allOn(Context ctx) {
        for (Item i : Item.values()) if (!isOn(ctx, i)) return false;
        return true;
    }

    static boolean anyOn(Context ctx) {
        for (Item i : Item.values()) if (isOn(ctx, i)) return true;
        return false;
    }

    static boolean isPendingOn(Context ctx) {
        return prefs(ctx).getBoolean(PREF_PENDING_ON, false);
    }

    static boolean isSyncWithMode(Context ctx) {
        return prefs(ctx).getBoolean(PREF_SYNC, true);
    }

    /** null: okunamadı. */
    static Boolean readDevModeActive(Context ctx) {
        ContentResolver cr = ctx.getContentResolver();
        try {
            int enabled = Settings.Global.getInt(cr, KEY_MODE_ENABLED, 0);
            String name = Settings.Global.getString(cr, KEY_MODE_NAME);
            return enabled == 1 && modeName(ctx).equals(name);
        } catch (SecurityException e) {
            Log.w("DevModeHelper", "Mod durumu okunamadı", e);
            return null;
        }
    }

    /**
     * Auto Blocker durumu. Samsung bu anahtarı signature izniyle koruyor; Android 12+ normal uygulamalara
     * okutmuyor ("From S+ ... not readable"). Bu yüzden telefonun kendi sinyallerinden çıkarılır:
     *  1) Auto Blocker açıkken Samsung USB/kablosuz hata ayıklamayı zorla kapatır
     *     -> hata ayıklama açıksa Auto Blocker KESİN kapalıdır.
     *  2) Anahtar değiştiğinde gelen bildirim (onAutoBlockerChanged) durumu çevirir.
     * null: bilinmiyor (yanlış bilgi göstermemek için "kapalı" varsayılmaz).
     */
    static Boolean readAutoBlocker(Context ctx) {
        try {
            String v = Settings.Secure.getString(ctx.getContentResolver(), KEY_AUTO_BLOCKER);
            if (v != null) return "1".equals(v);
        } catch (SecurityException expected) {
            // Beklenen durum; aşağıdaki çıkarıma geç.
        }
        if (isOn(ctx, Item.USB) || isOn(ctx, Item.WIFI)) {
            saveAutoBlocker(ctx, 0);
            return false;
        }
        int st = prefs(ctx).getInt(PREF_AB_STATE, -1);
        return st < 0 ? null : st == 1;
    }

    /** Auto Blocker anahtarı değişti (değeri okunamıyor, sadece değiştiği biliniyor). */
    static synchronized void onAutoBlockerChanged(Context ctx) {
        int prev = prefs(ctx).getInt(PREF_AB_STATE, -1);
        boolean debugOn = isOn(ctx, Item.USB) || isOn(ctx, Item.WIFI);
        int now;
        if (prev >= 0) now = 1 - prev;       // bilinen durumdan çevir
        else now = debugOn ? 0 : 1;          // bilinmiyorsa: açılınca Samsung hata ayıklamayı kapatır
        if (debugOn && now == 1) now = 0;    // hata ayıklama açıkken Auto Blocker açık olamaz
        saveAutoBlocker(ctx, now);
        log(ctx, "Auto Blocker " + (now == 1 ? "açıldı" : "kapandı")
                + " (algılandı; USB=" + isOn(ctx, Item.USB) + ", kablosuz=" + isOn(ctx, Item.WIFI) + ")");
    }

    private static void saveAutoBlocker(Context ctx, int state) {
        SharedPreferences p = prefs(ctx);
        if (p.getInt(PREF_AB_STATE, -1) != state) p.edit().putInt(PREF_AB_STATE, state).apply();
    }

    static boolean canWriteSecureSettings(Context ctx) {
        return ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }

    static Intent autoBlockerIntent() {
        return new Intent("com.samsung.android.rampart.action.MAIN_SETTING_ACTIVITY")
                .setPackage("com.samsung.android.rampart")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    // ---------------------------------------------------------------- Yazma

    private static void writeAll(Context ctx, boolean on) {
        Item[] items = Item.values();
        for (int n = 0; n < items.length; n++) {
            write(ctx, on ? items[n] : items[items.length - 1 - n], on);
        }
    }

    private static boolean write(Context ctx, Item item, boolean on) {
        int value = on ? item.onValue : 0;
        try {
            boolean ok = Settings.Global.putInt(ctx.getContentResolver(), item.key, value);
            if (!ok) log(ctx, "Yazılamadı: " + item.key + "=" + value);
            return ok;
        } catch (SecurityException ex) {
            log(ctx, "Yazılamadı " + item.key + "=" + value + ": " + ex.getMessage());
            return false;
        }
    }

    private static boolean checkPermission(Context ctx) {
        if (canWriteSecureSettings(ctx)) return true;
        log(ctx, "WRITE_SECURE_SETTINGS izni yok, hiçbir şey yapılmadı");
        notify(ctx, "DevMode Helper izni eksik", "adb ile WRITE_SECURE_SETTINGS izni verilmeli.", null);
        return false;
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void notify(Context ctx, String title, String text, Intent tap) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL, "Geliştirme modu", NotificationManager.IMPORTANCE_DEFAULT));
        Notification.Builder b = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_dev)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true);
        if (tap != null) {
            b.setContentIntent(PendingIntent.getActivity(ctx, 0, tap,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        }
        nm.notify(NOTIF_ID, b.build());
    }

    private static void cancelNotification(Context ctx) {
        ctx.getSystemService(NotificationManager.class).cancel(NOTIF_ID);
    }

    // ---------------------------------------------------------------- Log

    static File logFile(Context ctx) {
        return new File(ctx.getFilesDir(), "log.txt");
    }

    static List<String> readLog(Context ctx) {
        try {
            return Files.readAllLines(logFile(ctx).toPath());
        } catch (Exception e) {
            return List.of();
        }
    }

    static synchronized void log(Context ctx, String msg) {
        Log.i("DevModeHelper", msg);
        File f = logFile(ctx);
        String line = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.US).format(new Date()) + "  " + msg;
        try {
            if (f.exists()) {
                List<String> lines = Files.readAllLines(f.toPath());
                if (lines.size() >= MAX_LOG_LINES) {
                    Files.write(f.toPath(), lines.subList(lines.size() - MAX_LOG_LINES / 2, lines.size()));
                }
            }
            try (FileWriter w = new FileWriter(f, true)) {
                w.write(line + "\n");
            }
        } catch (Exception ignored) {
            // Log yazılamaması ana işi durdurmamalı.
        }
    }
}
