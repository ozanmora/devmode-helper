package works.mora.devmode;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Durum bildiriminin "Kapat" düğmesi ve silinince yeniden gösterilmesi. Dışa kapalı (exported=false). */
public class StatusReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Reconciler.ACTION_TURN_OFF.equals(intent.getAction())) {
            Reconciler.setAll(ctx, false, "Bildirimden");
        } else {
            Reconciler.updateStatusNotification(ctx);
        }
    }
}
