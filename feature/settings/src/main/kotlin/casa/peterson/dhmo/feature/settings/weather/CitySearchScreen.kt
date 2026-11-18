package casa.peterson.dhmo.feature.settings.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.data.repository.GeoPlace
import casa.peterson.dhmo.core.designsystem.component.DhmoScreen
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.feature.settings.R

@Composable
internal fun CitySearchScreen(onBack: () -> Unit, viewModel: CitySearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CitySearchContent(query, state, onBack, viewModel::onQueryChange) { viewModel.choose(it, onBack) }
}

@Composable
internal fun CitySearchContent(
    query: String,
    state: CitySearchState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onChoose: (GeoPlace) -> Unit,
) {
    val title = stringResource(R.string.feature_settings_city_search_title)
    DhmoScreen(topBar = { DhmoTopBar(title, onNavigateBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).consumeWindowInsets(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.feature_settings_city_search_label)) },
                supportingText = { Text(stringResource(R.string.feature_settings_city_search_hint)) },
                singleLine = true,
            )
            when (state) {
                CitySearchState.Idle -> Unit

                CitySearchState.Searching -> CircularProgressIndicator(Modifier.padding(16.dp))

                CitySearchState.Failed -> Text(
                    stringResource(R.string.feature_settings_city_search_error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 16.dp),
                )

                is CitySearchState.Results -> if (state.places.isEmpty()) {
                    Text(stringResource(R.string.feature_settings_city_search_none), modifier = Modifier.padding(top = 16.dp))
                } else {
                    LazyColumn {
                        items(state.places) { place ->
                            ListItem(
                                headlineContent = { Text(place.name) },
                                supportingContent = { Text(listOfNotNull(place.admin1, place.country).joinToString(", ")) },
                                modifier = Modifier.fillMaxWidth().clickable { onChoose(place) },
                            )
                        }
                    }
                }
            }
        }
    }
}
