package casa.peterson.dhmo.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import casa.peterson.dhmo.core.designsystem.R

/** The default content of an integration slot whose feature ships in a later phase. */
@Composable
fun ComingSoon(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(R.string.core_designsystem_coming_soon), style = MaterialTheme.typography.titleMedium)
    }
}
