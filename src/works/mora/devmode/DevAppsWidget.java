package works.mora.devmode;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.widget.RemoteViews;

import java.util.List;

/**
 * "Test uygulamaları" ana ekran widget'ı: adb ile yüklenen uygulamaları ikonlarıyla listeler.
 * Android bir uygulamanın ana ekranda belirli bir sayfaya ikon koymasına izin vermediği için, widget
 * kullanıcının seçtiği sayfada (ör. 3. sayfa) durur ve yeni kurulan uygulamalar onun içinde görünür.
 *
 * Yenileme: başlıktaki düğme, 15 dakikalık DevAppsJob, uygulama açılışı ve bilgisayardan
 *   adb shell am broadcast -n works.mora.devmode/.DevAppsWidget -a works.mora.devmode.REFRESH_DEV_APPS
 */
public class DevAppsWidget extends AppWidgetProvider {

    static final String ACTION_REFRESH = "works.mora.devmode.REFRESH_DEV_APPS";
    static final String EXTRA_PKG = "pkg";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ACTION_REFRESH.equals(intent.getAction())) {
            refresh(ctx);
        } else {
            super.onReceive(ctx, intent);
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        render(ctx, mgr, ids);
    }

    @Override
    public void onEnabled(Context ctx) {
        DevAppsJob.schedule(ctx);
    }

    @Override
    public void onDisabled(Context ctx) {
        DevAppsJob.cancel(ctx);
    }

    static boolean hasWidgets(Context ctx) {
        return ids(ctx).length > 0;
    }

    static void refresh(Context ctx) {
        int[] ids = ids(ctx);
        if (ids.length > 0) render(ctx, AppWidgetManager.getInstance(ctx), ids);
    }

    private static int[] ids(Context ctx) {
        return AppWidgetManager.getInstance(ctx).getAppWidgetIds(new ComponentName(ctx, DevAppsWidget.class));
    }

    private static void render(Context ctx, AppWidgetManager mgr, int[] ids) {
        List<DevApps.App> apps = DevApps.list(ctx);

        // İkon resmi değil kaynak adresi gönderilir; launcher ikonu uygulamanın kendi paketinden yükler.
        RemoteViews.RemoteCollectionItems.Builder items = new RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1);
        for (DevApps.App app : apps) {
            RemoteViews item = new RemoteViews(ctx.getPackageName(), R.layout.widget_dev_app_item);
            item.setTextViewText(R.id.label, app.label);
            if (app.iconRes != 0) {
                item.setImageViewIcon(R.id.icon, Icon.createWithResource(app.pkg, app.iconRes));
            } else {
                item.setImageViewResource(R.id.icon, android.R.drawable.sym_def_app_icon);
            }
            item.setOnClickFillInIntent(R.id.item, new Intent().putExtra(EXTRA_PKG, app.pkg));
            items.addItem(app.pkg.hashCode(), item);
        }

        // Dokunulan uygulamayı açan ara ekran (dışa kapalı); fill-in için şablon mutable olmalı.
        PendingIntent launch = PendingIntent.getActivity(ctx, 10,
                new Intent(ctx, LaunchActivity.class),
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent refresh = PendingIntent.getBroadcast(ctx, 11,
                new Intent(ACTION_REFRESH).setClass(ctx, DevAppsWidget.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent open = PendingIntent.getActivity(ctx, 12,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_dev_apps);
            rv.setRemoteAdapter(R.id.grid, items.build());
            rv.setEmptyView(R.id.grid, R.id.empty);
            rv.setPendingIntentTemplate(R.id.grid, launch);
            rv.setOnClickPendingIntent(R.id.refresh, refresh);
            rv.setOnClickPendingIntent(R.id.header, open);
            rv.setTextViewText(R.id.count, apps.isEmpty() ? "" : String.valueOf(apps.size()));
            mgr.updateAppWidget(id, rv);
        }
    }
}
