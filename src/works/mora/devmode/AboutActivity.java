package works.mora.devmode;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Hakkında: sürüm, kaynak kodu, lisans ve destek bağlantıları (yalnızca dokununca tarayıcıda açılır). */
public class AboutActivity extends Activity {

    static final String REPO_URL = "https://github.com/ozanmora/devmode-helper";
    static final String SPONSORS_URL = "https://github.com/sponsors/ozanmora";
    static final String COFFEE_URL = "https://buymeacoffee.com/ozanmora";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OneUi ui = new OneUi(this);
        LinearLayout content = ui.scaffold("DevMode Helper", this::finish);
        ui.setHeaderIcon(R.mipmap.ic_launcher);
        ui.setSubtitle("Sürüm " + OneUi.versionName(this));

        content.addView(ui.description("USB ve kablosuz hata ayıklamayı, ekranı açık tutmayı tek dokunuşla "
                + "açıp kapatan, Samsung Modlar ve Rutinler ile çalışan açık kaynak geliştirici aracı."));

        LinearLayout infoCard = ui.card();
        OneUi.Row source = link(ui, "Kaynak kodu", "github.com/ozanmora/devmode-helper", REPO_URL);
        OneUi.Row notes = link(ui, "Sürüm notları", "GitHub Releases", REPO_URL + "/releases");
        OneUi.Row license = link(ui, "Lisans", "MIT", REPO_URL + "/blob/main/LICENSE");
        ui.addRows(infoCard, source, notes, license);
        content.addView(infoCard, ui.cardParams(ui.dp(8)));

        content.addView(ui.category("Destek ol"));
        content.addView(ui.description("DevMode Helper ücretsiz ve açık kaynak. İşine yaradıysa "
                + "geliştirilmesini destekleyebilirsin."));
        LinearLayout supportCard = ui.card();
        ui.addRows(supportCard,
                link(ui, "GitHub Sponsors", "github.com/sponsors/ozanmora", SPONSORS_URL),
                link(ui, "Buy Me a Coffee", "buymeacoffee.com/ozanmora", COFFEE_URL));
        content.addView(supportCard, ui.cardParams(ui.dp(8)));
    }

    private OneUi.Row link(OneUi ui, String title, String summary, String url) {
        OneUi.Row r = ui.row(title, false, false);
        r.summary.setText(summary);
        r.view.setOnClickListener(v -> OneUi.openUrl(this, url));
        return r;
    }
}
