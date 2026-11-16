package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The root of every screen. The app is always edge-to-edge (target SDK 35+), and `NavigationSuiteScaffold` does not
 * pass insets to its content, so each screen handles its own: this scaffold uses [WindowInsets.safeDrawing] (system
 * bars, display cutout and IME), minus whatever an ancestor already consumed (the bottom bar or rail).
 *
 * Apply the `innerPadding` passed to [content] exactly once: as `contentPadding` on a lazy list (so items scroll
 * behind the system bars), or as `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)` otherwise. Because
 * the IME is included, do not add `imePadding()` below it.
 */
@Composable
fun DhmoScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (innerPadding: PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content,
    )
}
