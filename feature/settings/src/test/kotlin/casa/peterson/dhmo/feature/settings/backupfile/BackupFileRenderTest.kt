package casa.peterson.dhmo.feature.settings.backupfile

import androidx.test.ext.junit.runners.AndroidJUnit4
import casa.peterson.dhmo.core.testing.renderScreenVariants
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFileRenderTest {
    @Test
    fun withUndo() = renderScreenVariants("SettingsBackupFile") {
        BackupFileContent(BackupFileUiState(canUndo = true), {}, {}, {}, {}, {}, {})
    }
}
