package works.mora.devmode;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Olay günlüğü: olay türüne göre filtre çipleri, güne göre gruplanmış kartlar, her olay ayrı satırda.
 * Filtre çubuğu kaydırırken araç çubuğunun altında sabit kalır.
 */
public class LogActivity extends Activity {

    enum Type {
        ALL("Tümü"), MODE("Mod"), MANUAL("Elle"), AUTO_BLOCKER("Auto Blocker"), ERROR("Hata"), OTHER("Diğer");

        final String label;

        Type(String label) {
            this.label = label;
        }
    }

    static final class Event {
        final String day, time, message;
        final Type type;

        Event(String day, String time, String message) {
            this.day = day;
            this.time = time;
            this.message = message;
            this.type = classify(message);
        }
    }

    private static final String[] MONTHS = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz",
            "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
    // Güncel biçim "02.10 13:25:56  mesaj", eski biçim "2026-09-29 09:41:03  mesaj".
    private static final Pattern NEW_LINE = Pattern.compile("^(\\d{2})\\.(\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\s+(.*)$");
    private static final Pattern OLD_LINE = Pattern.compile("^\\d{4}-(\\d{2})-(\\d{2}) (\\d{2}:\\d{2}:\\d{2})\\s+(.*)$");

    private OneUi ui;
    private LinearLayout list;
    private final List<Event> events = new ArrayList<>();
    private Type filter = Type.ALL;
    private LinearLayout inlineChips, pinnedChips;
    private HorizontalScrollView inlineBar, pinnedBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new OneUi(this);
        LinearLayout content = ui.scaffold("Olay günlüğü", this::finish);

        inlineChips = new LinearLayout(this);
        inlineBar = chipBar(inlineChips);
        content.addView(inlineBar);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        pinnedChips = new LinearLayout(this);
        pinnedBar = chipBar(pinnedChips);
        pinnedBar.setBackgroundColor(ui.pageBg);
        pinnedBar.setVisibility(View.GONE);
        ui.pinBelowToolbar(pinnedBar);
        ui.onScroll(y -> pinnedBar.setVisibility(ui.isUnderToolbar(inlineBar, y) ? View.VISIBLE : View.GONE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        events.clear();
        for (String line : Reconciler.readLog(this)) {
            Event e = parse(line);
            if (e != null) events.add(e);
        }
        ui.setSubtitle(events.size() + " olay");
        render();
    }

    private void render() {
        Map<Type, Integer> counts = new EnumMap<>(Type.class);
        for (Event e : events) counts.merge(e.type, 1, Integer::sum);
        counts.put(Type.ALL, events.size());
        fillChips(inlineChips, counts);
        fillChips(pinnedChips, counts);

        list.removeAllViews();
        String day = null;
        LinearLayout card = null;
        List<OneUi.Row> rows = new ArrayList<>();
        int shown = 0;
        for (int i = events.size() - 1; i >= 0; i--) {   // en yenisi en üstte
            Event e = events.get(i);
            if (filter != Type.ALL && e.type != filter) continue;
            if (!e.day.equals(day)) {
                if (card != null) flush(card, rows);
                day = e.day;
                list.addView(ui.category(day));
                card = ui.card();
                rows = new ArrayList<>();
            }
            rows.add(row(e));
            shown++;
        }
        if (card != null) flush(card, rows);
        if (shown == 0) {
            TextView empty = ui.description(events.isEmpty() ? "Henüz olay yok" : "Bu türde olay yok");
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(ui.dp(28), ui.dp(48), ui.dp(28), ui.dp(48));
            list.addView(empty);
        }
    }

    private void flush(LinearLayout card, List<OneUi.Row> rows) {
        ui.addRows(card, rows.toArray(new OneUi.Row[0]));
        list.addView(card, ui.cardParams(0));
    }

    private OneUi.Row row(Event e) {
        OneUi.Row r = ui.row(e.message, false, false);
        r.view.setMinimumHeight(ui.dp(60));
        r.title.setTextSize(15);
        r.title.setMaxLines(4);
        r.summary.setText(e.time + " · " + e.type.label);
        r.summary.setTextColor(color(e.type));
        return r;
    }

    // ---------------------------------------------------------------- Filtre çipleri

    private HorizontalScrollView chipBar(LinearLayout chips) {
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
        HorizontalScrollView bar = new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        bar.addView(chips);
        return bar;
    }

    private void fillChips(LinearLayout chips, Map<Type, Integer> counts) {
        chips.removeAllViews();
        for (Type t : Type.values()) {
            int n = counts.getOrDefault(t, 0);
            if (n == 0 && t != Type.ALL && t != filter) continue;
            TextView chip = ui.text(t.label + "  " + n, 14, t == filter ? 0xFFFFFFFF : ui.textPrimary, t == filter);
            chip.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(ui.dp(18));
            bg.setColor(t == filter ? ui.accent : ui.cardBg);
            chip.setBackground(bg);
            chip.setOnClickListener(v -> {
                filter = t;
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(ui.dp(8));
            chips.addView(chip, lp);
        }
    }

    // ---------------------------------------------------------------- Ayrıştırma

    static Event parse(String line) {
        Matcher m = NEW_LINE.matcher(line);
        if (m.matches()) return new Event(day(m.group(1), m.group(2)), m.group(3), tidy(m.group(4)));
        m = OLD_LINE.matcher(line);
        if (m.matches()) return new Event(day(m.group(2), m.group(1)), m.group(3), tidy(m.group(4)));
        return null;
    }

    private static String day(String dd, String mm) {
        int month = Integer.parseInt(mm);
        return Integer.parseInt(dd) + " " + (month >= 1 && month <= 12 ? MONTHS[month - 1] : mm);
    }

    private static String tidy(String msg) {
        return msg.isEmpty() ? msg : Character.toUpperCase(msg.charAt(0)) + msg.substring(1);
    }

    static Type classify(String msg) {
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        if (m.startsWith("yazılamadı") || m.contains("izni yok") || m.contains("tutmadı") || m.contains("okunamadı")) {
            return Type.ERROR;
        }
        if (m.contains("auto blocker")) return Type.AUTO_BLOCKER;
        if (m.startsWith("samsung") || m.startsWith("mod:") || m.startsWith("izlenen samsung modu")) return Type.MODE;
        if (m.startsWith("uygulamadan") || m.startsWith("bildirimden")) return Type.MANUAL;
        return Type.OTHER;
    }

    private int color(Type t) {
        switch (t) {
            case MODE: return ui.accent;
            case MANUAL: return ui.night ? 0xFF4CC38A : 0xFF1E8E5A;
            case AUTO_BLOCKER: return ui.night ? 0xFFFFB25B : 0xFFC8650C;
            case ERROR: return ui.warn;
            default: return ui.textSecondary;
        }
    }
}
