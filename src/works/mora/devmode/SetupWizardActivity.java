package works.mora.devmode;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Format sonrası veya ilk kurulum sihirbazı.
 * Geliştirici seçeneklerinin açılmasından adb WRITE_SECURE_SETTINGS iznine kadar
 * adımları anlık olarak denetler ve ilgili sistem ekranlarına yönlendirir.
 */
public class SetupWizardActivity extends Activity {

    static final String ADB_GRANT_COMMAND =
            "adb shell pm grant works.mora.devmode android.permission.WRITE_SECURE_SETTINGS";

    private OneUi ui;
    private LinearLayout contentContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            render();
        }
    };

    private final Runnable autoCheckRunnable = new Runnable() {
        @Override
        public void run() {
            render();
            // İzin henüz verilmediyse arka planda adb çalıştırıldığında anında yakalamak için 1.5 saniyede bir yokla
            if (!Reconciler.canWriteSecureSettings(SetupWizardActivity.this)) {
                handler.postDelayed(this, 1500);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new OneUi(this);
        LinearLayout root = ui.scaffold("Kurulum Rehberi", this::finish);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(contentContainer, new LinearLayout.LayoutParams(-1, -2));
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED), false, observer);
        getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, observer);
        if (Reconciler.hasAutoBlocker(this)) {
            getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(Reconciler.KEY_AUTO_BLOCKER), false, observer);
        }
        handler.removeCallbacks(autoCheckRunnable);
        handler.post(autoCheckRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(observer);
        handler.removeCallbacks(autoCheckRunnable);
    }

    private void render() {
        contentContainer.removeAllViews();

        boolean devOptionsOn = Reconciler.isDevOptionsEnabled(this);
        boolean usbDebugOn = Reconciler.isUsbDebuggingEnabled(this);
        boolean hasAb = Reconciler.hasAutoBlocker(this);
        Boolean abState = hasAb ? Reconciler.readAutoBlocker(this) : null;
        boolean abBlocking = Boolean.TRUE.equals(abState);
        boolean permGranted = Reconciler.canWriteSecureSettings(this);

        if (permGranted) {
            ui.setSubtitle("Kurulum başarıyla tamamlandı");
            contentContainer.addView(buildSuccessCard());
        } else {
            ui.setSubtitle("Geliştirici modu etkinleştirme adımları");
            contentContainer.addView(ui.description("Telefonunuza format attıktan veya uygulamayı yeni "
                    + "yükledikten sonra DevMode Helper'ı tam yetkiyle kullanabilmek için aşağıdaki adımları "
                    + "sırasıyla tamamlayın."));
        }

        // 1. Adım: Geliştirici Seçenekleri
        contentContainer.addView(buildStepCard(
                "1. Geliştirici Seçeneklerini Açın",
                devOptionsOn ? "Geliştirici seçenekleri etkinleştirildi."
                        : "Ayarlar > Telefon hakkında > Yazılım bilgileri bölümüne gidin ve \"Yapım numarası\" "
                        + "(Build number) seçeneğine arka arkaya 7 kez hızlıca dokunun.",
                devOptionsOn ? "✓ Tamamlandı" : "Bekleniyor",
                devOptionsOn ? 1 : 0,
                devOptionsOn ? null : "Yazılım Bilgilerine Git →",
                devOptionsOn ? null : v -> openSoftwareInfo()
        ));

        // 2. Adım: USB Hata Ayıklama
        contentContainer.addView(buildStepCard(
                "2. USB Hata Ayıklamayı Açın",
                usbDebugOn ? "USB hata ayıklama etkin."
                        : "Geliştirici seçenekleri menüsünden \"USB hata ayıklama\" anahtarını açık konuma getirin.",
                usbDebugOn ? "✓ Açık" : "Gerekli",
                usbDebugOn ? 1 : 0,
                usbDebugOn ? null : "Geliştirici Seçeneklerini Aç →",
                usbDebugOn ? null : v -> openDeveloperSettings()
        ));

        // 3. Adım: Samsung Auto Blocker (Samsung cihazlarda varsa)
        if (hasAb) {
            contentContainer.addView(buildStepCard(
                    "3. Samsung Auto Blocker",
                    abBlocking ? "Auto Blocker açık! Bu koruma USB üzerinden hata ayıklama komutlarını engeller. "
                            + "ADB bağlantısı yapabilmek için geçici olarak kapatın."
                            : (abState == null ? "Auto Blocker durumu bilinmiyor."
                            : "Auto Blocker kapalı veya hata ayıklamayı engellemiyor."),
                    abBlocking ? "⚠️ Açık (Engelliyor)" : "✓ Hazır",
                    abBlocking ? -1 : 1,
                    abBlocking ? "Auto Blocker Ayarlarına Git →" : null,
                    abBlocking ? v -> openAutoBlocker() : null
            ));
        }

        // 4. Adım: Bilgisayara Bağlantı ve RSA İzni
        contentContainer.addView(buildStepCard(
                "4. Telefonu Bilgisayara Bağlayın",
                "Telefonu USB kablosuyla bilgisayara bağlayın. Telefonda çıkacak \"USB hata ayıklamasına "
                        + "izin verilsin mi?\" penceresinde \"Bu bilgisayardan her zaman izin ver\"i işaretleyip "
                        + "\"İzin ver\"e dokunun.\n\n"
                        + "İpucu: Bildirim çıkmazsa bilgisayarınızda 'adb devices' komutunu çalıştırabilirsiniz.",
                permGranted ? "✓ Bağlandı" : "Bilgi",
                permGranted ? 1 : 0,
                null,
                null
        ));

        // 5. Adım: ADB WRITE_SECURE_SETTINGS İzni
        contentContainer.addView(buildPermissionStepCard(permGranted));

        // Alt boşluk
        View bottomSpace = new View(this);
        contentContainer.addView(bottomSpace, new LinearLayout.LayoutParams(-1, ui.dp(36)));
    }

    private View buildSuccessCard() {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(24), ui.dp(20), ui.dp(24), ui.dp(20));

        TextView title = ui.text("🎉 Tebrikler! Kurulum Tamamlandı", 18, ui.success, true);
        title.setPadding(0, 0, 0, ui.dp(6));
        card.addView(title);

        TextView desc = ui.text("WRITE_SECURE_SETTINGS izni verildi. Artık USB ve kablosuz hata ayıklamayı, "
                + "ekranı açık tutma ayarlarını tek dokunuşla veya Samsung modlarıyla otomatik olarak yönetebilirsiniz.",
                14, ui.textPrimary, false);
        desc.setLineSpacing(ui.dp(3), 1f);
        desc.setPadding(0, 0, 0, ui.dp(16));
        card.addView(desc);

        TextView startBtn = ui.button("Kullanmaya Başla", true, v -> finish());
        card.addView(startBtn);

        return cardWrap(card, ui.dp(4));
    }

    private View buildStepCard(String titleText, String descText, String badgeText, int badgeState,
                              String buttonText, View.OnClickListener buttonClick) {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16));

        // Üst satır: Başlık ve Rozet
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = ui.text(titleText, 16, ui.textPrimary, true);
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = ui.badge(badgeText, badgeState);
        headerRow.addView(badge);
        card.addView(headerRow);

        // Açıklama
        TextView desc = ui.text(descText, 14, ui.textSecondary, false);
        desc.setLineSpacing(ui.dp(3), 1f);
        desc.setPadding(0, ui.dp(10), 0, buttonText != null ? ui.dp(14) : 0);
        card.addView(desc);

        // Aksiyon butonu (varsa)
        if (buttonText != null) {
            TextView btn = ui.button(buttonText, false, buttonClick);
            card.addView(btn);
        }

        return cardWrap(card, ui.dp(12));
    }

    private View buildPermissionStepCard(boolean permGranted) {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16));

        // Başlık satırı
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = ui.text("5. ADB Ayar Yazma İznini Verin", 16, ui.textPrimary, true);
        headerRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView badge = ui.badge(permGranted ? "✓ İzin Verildi" : "Gerekli", permGranted ? 1 : -1);
        headerRow.addView(badge);
        card.addView(headerRow);

        // Açıklama
        TextView desc = ui.text(permGranted
                ? "Sistem ayarlarını yazma izni aktif durumda."
                : "Bilgisayarınızdaki terminal / komut satırında şu komutu çalıştırın:",
                14, ui.textSecondary, false);
        desc.setPadding(0, ui.dp(10), 0, ui.dp(8));
        card.addView(desc);

        // Komut Kutusu
        LinearLayout codeBox = new LinearLayout(this);
        codeBox.setOrientation(LinearLayout.VERTICAL);
        codeBox.setBackground(ui.rounded(ui.night ? 0xFF242428 : 0xFFE9E9EE));
        codeBox.setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12));

        TextView codeText = ui.text(ADB_GRANT_COMMAND, 13, ui.textPrimary, false);
        codeText.setTypeface(Typeface.MONOSPACE);
        codeText.setTextIsSelectable(true);
        codeText.setLineSpacing(ui.dp(3), 1f);
        codeBox.addView(codeText);

        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(-1, -2);
        cbParams.setMargins(0, 0, 0, ui.dp(14));
        card.addView(codeBox, cbParams);

        // Düğmeler: Kopyala & Paylaş
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView copyBtn = ui.button("Komutu Kopyala 📋", false, v -> copyCommand());
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(0, -2, 1);
        cpLp.setMarginEnd(ui.dp(8));
        actionsRow.addView(copyBtn, cpLp);

        TextView shareBtn = ui.button("Paylaş ↗", false, v -> shareCommand());
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(0, -2, 1);
        actionsRow.addView(shareBtn, spLp);

        card.addView(actionsRow);

        return cardWrap(card, ui.dp(12));
    }

    private View cardWrap(View v, int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(ui.dp(12), topMargin, ui.dp(12), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private void copyCommand() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("ADB Command", ADB_GRANT_COMMAND));
            Toast.makeText(this, "ADB komutu panoya kopyalandı", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCommand() {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, ADB_GRANT_COMMAND);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "ADB Komutunu Paylaş");
        startActivity(shareIntent);
    }

    private void openSoftwareInfo() {
        // Samsung One UI özel Yazılım Bilgileri sayfası
        try {
            Intent intent = new Intent();
            intent.setClassName("com.android.settings", "com.samsung.android.settings.deviceinfo.SoftwareInfoActivity");
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // Genel Android Telefon Hakkında
        try {
            startActivity(new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openDeveloperSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
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
