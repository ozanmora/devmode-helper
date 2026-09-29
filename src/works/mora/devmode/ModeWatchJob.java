package works.mora.devmode;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

/**
 * Mod, Auto Blocker veya geliştirici ayarları değişince sistem bu işi tetikler; uygulamanın arka planda sürekli çalışması gerekmez.
 * İçerik tetiklemeli işler tek seferliktir, bu yüzden her çalışmadan sonra yeniden kurulur.
 */
public class ModeWatchJob extends JobService {

    private static final int JOB_ID = 1001;

    static void schedule(Context ctx) {
        JobInfo.Builder b = new JobInfo.Builder(JOB_ID, new ComponentName(ctx, ModeWatchJob.class))
                .setTriggerContentUpdateDelay(200)
                .setTriggerContentMaxDelay(1000);
        for (String key : new String[]{Reconciler.KEY_MODE_ENABLED, Reconciler.KEY_MODE_NAME}) {
            b.addTriggerContentUri(new JobInfo.TriggerContentUri(Settings.Global.getUriFor(key), 0));
        }
        for (Reconciler.Item i : Reconciler.Item.values()) {
            b.addTriggerContentUri(new JobInfo.TriggerContentUri(Settings.Global.getUriFor(i.key), 0));
        }
        b.addTriggerContentUri(new JobInfo.TriggerContentUri(
                Settings.Secure.getUriFor(Reconciler.KEY_AUTO_BLOCKER), 0));
        JobInfo job = b.build();
        ctx.getSystemService(JobScheduler.class).schedule(job);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        android.net.Uri ab = Settings.Secure.getUriFor(Reconciler.KEY_AUTO_BLOCKER);
        android.net.Uri[] uris = params.getTriggeredContentUris();
        if (uris != null) {
            for (android.net.Uri u : uris) {
                if (ab.equals(u)) Reconciler.onAutoBlockerChanged(this);
            }
        }
        Reconciler.onSystemChange(this, "tetik");
        Reconciler.updateStatusNotification(this);
        schedule(this);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
