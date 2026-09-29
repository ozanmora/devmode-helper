package works.mora.devmode;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** Widget'ta dokunulan uygulamayı açar ve kapanır. Dışa kapalı; sadece kendi PendingIntent'imizle açılır. */
public class LaunchActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getIntent().getStringExtra(DevAppsWidget.EXTRA_PKG);
        Intent launch = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch != null) {
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } else {
            DevAppsWidget.refresh(this); // Uygulama kaldırılmışsa listeden düşsün.
        }
        finish();
    }
}
