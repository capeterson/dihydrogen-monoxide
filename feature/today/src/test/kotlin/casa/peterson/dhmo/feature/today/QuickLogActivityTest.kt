package casa.peterson.dhmo.feature.today

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.feature.today.quicklog.QuickLogActivity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickLogActivityTest {
    @Test
    fun quickLogIsUnexportedAndResolvesItsAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.packageManager
        val component = ComponentName(context, QuickLogActivity::class.java)
        val info = manager.getActivityInfo(component, 0)
        assertThat(info.exported).isFalse()
        val intent = Intent(QuickLogActivity.ACTION_QUICK_LOG).setPackage(context.packageName)
            .addCategory(Intent.CATEGORY_DEFAULT)
        val matches = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertThat(matches.map { it.activityInfo.name }).contains(QuickLogActivity::class.java.name)
    }
}
