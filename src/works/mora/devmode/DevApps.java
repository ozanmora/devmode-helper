package works.mora.devmode;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * adb (USB/Wi‑Fi hata ayıklama) ile yüklenen uygulamalar.
 * adb kurulumunda kurucu paket yoktur (InstallSourceInfo.getInstallingPackageName() == null);
 * mağaza veya dosya yöneticisiyle kurulanlarda doludur. Sistem uygulamaları ve bu uygulama hariç tutulur.
 */
final class DevApps {

    static final class App {
        final String pkg;
        final String label;
        final int iconRes;
        final long updated;

        App(String pkg, String label, int iconRes, long updated) {
            this.pkg = pkg;
            this.label = label;
            this.iconRes = iconRes;
            this.updated = updated;
        }
    }

    private DevApps() {}

    /** En son yüklenen/güncellenen en başta. */
    static List<App> list(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, App> apps = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(main, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || apps.containsKey(pkg)) continue;
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            if ((ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
            try {
                InstallSourceInfo src = pm.getInstallSourceInfo(pkg);
                if (src.getInstallingPackageName() != null) continue;
                long updated = pm.getPackageInfo(pkg, 0).lastUpdateTime;
                apps.put(pkg, new App(pkg, ri.loadLabel(pm).toString(), ai.icon, updated));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Bu arada kaldırılmış.
            }
        }
        List<App> out = new ArrayList<>(apps.values());
        out.sort((a, b) -> Long.compare(b.updated, a.updated));
        return out;
    }
}
