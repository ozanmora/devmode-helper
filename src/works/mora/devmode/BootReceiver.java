package works.mora.devmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Yeniden başlatma ve güncelleme sonrası izleyiciyi yeniden kurar, durumu eşitler. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        ModeWatchJob.schedule(ctx);
        Reconciler.onSystemChange(ctx, "açılış: " + intent.getAction());
        Reconciler.updateStatusNotification(ctx);
    }
}
