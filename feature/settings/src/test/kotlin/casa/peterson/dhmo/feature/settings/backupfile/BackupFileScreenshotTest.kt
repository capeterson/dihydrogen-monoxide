package casa.peterson.dhmo.feature.settings.backupfile

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.captureScreenVariants
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupFileScreenshotTest {
    @Test
    fun withUndo() = captureScreenVariants("SettingsBackupFile") {
        BackupFileContent(BackupFileUiState(canUndo = true), {}, {}, {}, {}, {}, {})
    }
}
