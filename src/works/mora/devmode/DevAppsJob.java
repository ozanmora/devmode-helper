package works.mora.devmode;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ChangedPackages;

/**
 * Widget varken 15 dakikada bir paket değişikliklerine bakar. Android 8'den beri "paket eklendi"
 * yayını arka plandaki uygulamalara iletilmediği için anlık algılama yerine bu kullanılır.
 */
public class DevAppsJob extends JobService {

    private static final int JOB_ID = 1002;
    private static final String PREF_SEQ = "changed_packages_seq";

    static void schedule(Context ctx) {
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, DevAppsJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setPersisted(true)
                .build();
        ctx.getSystemService(JobScheduler.class).schedule(job);
    }

    static void cancel(Context ctx) {
        ctx.getSystemService(JobScheduler.class).cancel(JOB_ID);
    }

    /** Son bakıştan beri paket değiştiyse widget'ı yeniler. */
    static void refreshIfChanged(Context ctx) {
        if (!DevAppsWidget.hasWidgets(ctx)) return;
        SharedPreferences p = Reconciler.prefs(ctx);
        ChangedPackages changed = ctx.getPackageManager().getChangedPackages(p.getInt(PREF_SEQ, 0));
        if (changed == null) return;
        p.edit().putInt(PREF_SEQ, changed.getSequenceNumber()).apply();
        DevAppsWidget.refresh(ctx);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        refreshIfChanged(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
