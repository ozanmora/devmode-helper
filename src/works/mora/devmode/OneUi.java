package works.mora.devmode;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

/**
 * One UI ayar ekranı yapı taşları: büyük başlık (kaydırınca araç çubuğuna küçülür), yuvarlak köşeli
 * kartlar, ana anahtar çubuğu, satırlar ve One UI tarzı anahtarlar.
 *
 * Renkler ve anahtar ölçüleri Samsung Ayarlar uygulamasının (One UI 8.5) kendi kaynaklarından alındı:
 * basic_token_* renk jetonları ve sesl_switch_* çizimleri (hap 32x20dp, topuz 16dp + 2dp halka).
 */
final class OneUi {

    final Activity a;
    final boolean night;
    final int pageBg, cardBg, textPrimary, textSecondary, accent, warn, masterOnBg, divider, ripple;
    private final int switchOffTrack, switchThumb;

    private ScrollView scroll;
    private TextView toolbarTitle, bigTitle, bigSubtitle;
    private int headerHeight;

    OneUi(Activity a) {
        this.a = a;
        night = (a.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            pageBg = Color.parseColor("#010102");        // background_dark = gray_100
            cardBg = Color.parseColor("#17171A");        // surface_dark = gray_95
            textPrimary = Color.parseColor("#FCFCFF");   // on_surface_container_highest_dark = gray_01
            textSecondary = Color.parseColor("#9A9A9F");
            accent = Color.parseColor("#578FFF");        // primary_40
            warn = Color.parseColor("#FF6B6B");
            masterOnBg = Color.parseColor("#3A3A3D");    // switchbar on = surface_container_dark = gray_80
            divider = Color.parseColor("#3A3A3D");
            ripple = Color.parseColor("#33FFFFFF");
            switchOffTrack = Color.parseColor("#636368"); // switch_track_off_dark = gray_60
        } else {
            pageBg = Color.parseColor("#F1F1F3");        // background_light = gray_05
            cardBg = Color.parseColor("#FCFCFF");        // surface_light = gray_01
            textPrimary = Color.parseColor("#010102");   // on_surface_container_highest_light = gray_100
            textSecondary = Color.parseColor("#737378");
            accent = Color.parseColor("#387AFF");        // primary_50
            warn = Color.parseColor("#D93025");
            masterOnBg = Color.parseColor("#E4E4E7");    // switchbar on = surface_container_light = gray_15
            divider = Color.parseColor("#E4E4E7");
            ripple = Color.parseColor("#1F000000");
            switchOffTrack = Color.parseColor("#A3A3A7"); // switch_track_off = gray_40
        }
        switchThumb = Color.parseColor("#FCFCFF");        // switch_thumb = gray_01 (iki temada da)
    }

    /** Sayfa iskeleti; içerik eklenecek dikey LinearLayout'u döndürür. back=null ise geri düğmesi olmaz. */
    LinearLayout scaffold(String title, Runnable back) {
        a.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(pageBg);

        LinearLayout content = new LinearLayout(a);
        content.setOrientation(LinearLayout.VERTICAL);

        headerHeight = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.32f);
        LinearLayout header = new LinearLayout(a);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(24), 0, dp(24), dp(8));
        bigTitle = text(title, 34, textPrimary, false);
        bigTitle.setGravity(Gravity.CENTER);
        bigTitle.setMaxLines(2);
        bigSubtitle = text("", 15, textSecondary, false);
        bigSubtitle.setGravity(Gravity.CENTER);
        bigSubtitle.setPadding(0, dp(6), 0, 0);
        header.addView(bigTitle);
        header.addView(bigSubtitle);
        content.addView(header, new LinearLayout.LayoutParams(-1, headerHeight));

        scroll = new ScrollView(a);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(a);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(pageBg);
        if (back != null) {
            ImageView b = new ImageView(a);
            b.setImageResource(R.drawable.ic_back);
            b.setImageTintList(ColorStateList.valueOf(textPrimary));
            b.setScaleType(ImageView.ScaleType.CENTER);
            b.setContentDescription("Geri");
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(cardBg);
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), circle, null));
            b.setElevation(dp(2));
            b.setOnClickListener(v -> back.run());
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(dp(44), dp(44));
            bp.setMarginEnd(dp(12));
            toolbar.addView(b, bp);
        }
        toolbarTitle = text(title, 20, textPrimary, true);
        toolbarTitle.setAlpha(0f);
        toolbar.addView(toolbarTitle, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(toolbar, new FrameLayout.LayoutParams(-1, dp(56)));

        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> {
            float p = Math.min(1f, y / (float) Math.max(1, headerHeight - dp(56)));
            bigTitle.setAlpha(Math.max(0f, 1f - p * 1.6f));
            bigSubtitle.setAlpha(Math.max(0f, 1f - p * 2f));
            toolbarTitle.setAlpha(Math.max(0f, (p - 0.65f) / 0.35f));
        });

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) toolbar.getLayoutParams();
            lp.height = dp(56) + bars.top;
            toolbar.setLayoutParams(lp);
            toolbar.setPadding(dp(back != null ? 16 : 24), bars.top, dp(24), 0);
            scroll.setPadding(0, bars.top, 0, bars.bottom + dp(32));
            return WindowInsets.CONSUMED;
        });

        a.setContentView(root);
        Window w = a.getWindow();
        w.setDecorFitsSystemWindows(false);
        WindowInsetsController c = w.getInsetsController();
        if (c != null) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(night ? 0 : light, light);
        }
        return content;
    }

    void setSubtitle(CharSequence s) {
        bigSubtitle.setText(s);
    }

    // ---------------------------------------------------------------- Satırlar

    /** Tek bir ayar satırı. */
    final class Row {
        final LinearLayout view;
        /** Detay sayfası olan satırlarda yazı kısmı ayrı tıklanır. */
        final LinearLayout textArea;
        final TextView title, summary;
        final Switch sw;

        Row(LinearLayout view, LinearLayout textArea, TextView title, TextView summary, Switch sw) {
            this.view = view;
            this.textArea = textArea;
            this.title = title;
            this.summary = summary;
            this.sw = sw;
        }

        void setEnabled(boolean enabled) {
            view.setEnabled(enabled);
            textArea.setEnabled(enabled);
            if (sw != null) {
                sw.setEnabled(enabled);
                sw.setAlpha(enabled ? 1f : 0.4f);
            }
            title.setAlpha(enabled ? 1f : 0.5f);
        }
    }

    /**
     * withSwitch + hasDetail: One UI'daki gibi yazı kısmı detay sayfasını açar, aradaki dikey çizgi bunu
     * belirtir, anahtar ayrı çalışır. Sadece aç/kapat olan satırda çizgi olmaz ve tüm satır anahtarı çevirir.
     */
    Row row(String title, boolean withSwitch, boolean hasDetail) {
        LinearLayout r = new LinearLayout(a);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(dp(72));

        LinearLayout texts = new LinearLayout(a);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setPadding(dp(24), dp(14), withSwitch ? dp(12) : dp(24), dp(14));
        TextView t = text(title, 17, textPrimary, false);
        t.setMaxLines(3);
        texts.addView(t);
        TextView summary = text("", 14, textSecondary, false);
        summary.setMaxLines(6);
        summary.setPadding(0, dp(2), 0, 0);
        summary.setVisibility(View.GONE);
        texts.addView(summary);
        r.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Switch sw = null;
        if (withSwitch) {
            if (hasDetail) {
                View sep = new View(a);
                sep.setBackgroundColor(divider);
                r.addView(sep, new LinearLayout.LayoutParams(dp(1), dp(32)));
            }
            sw = new Switch(a);
            style(sw);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
            sp.setMarginStart(dp(hasDetail ? 16 : 0));
            sp.setMarginEnd(dp(24));
            r.addView(sw, sp);
        }

        if (withSwitch && hasDetail) {
            texts.setBackground(rippleRect());
        } else {
            r.setBackground(rippleRect());
            if (sw != null) {
                Switch s = sw;
                r.setOnClickListener(v -> s.toggle());
            }
        }
        // Özet boş değilse görünür olsun.
        summary.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) {
                summary.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
            }
        });
        return new Row(r, texts, t, summary, sw);
    }

    /** Ana anahtar çubuğu ("Açık"/"Kapalı"). */
    Row masterBar() {
        LinearLayout bar = new LinearLayout(a);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(24), 0, dp(24), 0);
        bar.setMinimumHeight(dp(68));
        bar.setClipToOutline(true);
        TextView t = text("Kapalı", 18, textPrimary, true);
        LinearLayout texts = new LinearLayout(a);
        texts.addView(t);
        bar.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(a);
        style(sw);
        bar.addView(sw);
        bar.setOnClickListener(v -> sw.toggle());
        return new Row(bar, texts, t, new TextView(a), sw);
    }

    /** One UI ana anahtar çubuğu: açıkken nötr gri zemin ve mavi yazı, kapalıyken kart rengi. */
    void setMasterState(Row bar, boolean on, String label) {
        bar.title.setText(label);
        bar.title.setTextColor(on ? accent : textPrimary);
        GradientDrawable bg = rounded(on ? masterOnBg : cardBg);
        bar.view.setBackground(new RippleDrawable(ColorStateList.valueOf(ripple), bg, null));
    }

    /**
     * One UI anahtarı (sesl_switch): 32x20dp dolu hap, uçlardan 2dp içeride 16dp topuz.
     * Android Switch genişliği en az 2 x topuz genişliği + track padding'i alır; 32dp'yi tutturmak için
     * track'e 4dp yatay padding, topuza 12dp mantıksal genişlik verilir ve 16dp nokta iki yandan 2dp taşar.
     */
    void style(Switch sw) {
        int w = dp(32), h = dp(20), dot = dp(16), pad = dp(4), overhang = dp(2), gap = (h - dot) / 2;

        StateListDrawable track = new StateListDrawable();
        track.addState(new int[]{android.R.attr.state_checked}, pill(accent, w, h, pad));
        track.addState(new int[]{}, pill(switchOffTrack, w, h, pad));

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(switchThumb);
        circle.setSize(dot, dot);
        android.graphics.drawable.LayerDrawable thumb =
                new android.graphics.drawable.LayerDrawable(new Drawable[]{circle});
        thumb.setLayerInset(0, -overhang, gap, -overhang, gap);

        sw.setTrackDrawable(track);
        sw.setThumbDrawable(thumb);
        sw.setSwitchMinWidth(w);
        sw.setShowText(false);
        sw.setBackground(null);
    }

    private GradientDrawable pill(int fill, int w, int h, int hPad) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(h / 2f);
        g.setSize(w, h);
        g.setPadding(hPad, 0, hPad, 0);
        return g;
    }

    // ---------------------------------------------------------------- Kartlar, başlıklar

    TextView category(String title) {
        TextView t = text(title, 14, textSecondary, true);
        t.setPadding(dp(28), dp(24), dp(28), dp(8));
        return t;
    }

    TextView description(String s) {
        TextView t = text(s, 14, textSecondary, false);
        t.setMaxLines(20);
        t.setPadding(dp(28), dp(12), dp(28), dp(4));
        return t;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(a);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(cardBg));
        c.setClipToOutline(true);
        return c;
    }

    /** Karta satırları, aralarına ince çizgi koyarak ekler. */
    void addRows(LinearLayout card, Row... rows) {
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                View d = new View(a);
                d.setBackgroundColor(divider);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2));
                lp.setMargins(dp(24), 0, dp(24), 0);
                card.addView(d, lp);
            }
            card.addView(rows[i].view);
        }
    }

    LinearLayout.LayoutParams cardParams(int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(12), top, dp(12), 0);
        return lp;
    }

    GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(26));
        return g;
    }

    private Drawable rippleRect() {
        return new RippleDrawable(ColorStateList.valueOf(ripple), null, new ColorDrawable(Color.WHITE));
    }

    TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setEllipsize(TextUtils.TruncateAt.END);
        if (bold) t.setTypeface(Typeface.create(Typeface.DEFAULT, 600, false));
        return t;
    }

    int dp(int v) {
        return Math.round(v * a.getResources().getDisplayMetrics().density);
    }

    /** Dinleyiciyi tetiklemeden anahtarı güncelle. */
    static void setChecked(Switch sw, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        if (sw.isChecked() == checked) return;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
    }
}
