package casa.peterson.dhmo.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import casa.peterson.dhmo.core.designsystem.component.DhmoTopBar
import casa.peterson.dhmo.core.designsystem.component.VolumeText
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon

@Composable
internal fun ContainersScreen(onBack: () -> Unit, viewModel: ContainersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Container?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    Scaffold(topBar = { DhmoTopBar(stringResource(R.string.feature_today_containers_title), onNavigateBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Button(onClick = {
                    editing = null
                    showEditor = true
                }, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.feature_today_container_add))
                }
            }
            if (state.containers.isEmpty()) item { Text(stringResource(R.string.feature_today_container_empty), Modifier.padding(16.dp)) }
            items(state.containers, key = { it.id }) { container ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(container.name)
                        VolumeText(container.volumeMl, state.units)
                    }
                    Row {
                        TextButton(onClick = {
                            editing = container
                            showEditor = true
                        }) { Text(stringResource(R.string.feature_today_edit)) }
                        TextButton(onClick = { viewModel.delete(container.id) }) { Text(stringResource(R.string.feature_today_delete)) }
                    }
                    Row {
                        TextButton(onClick = { viewModel.move(container.id, -1) }) { Text(stringResource(R.string.feature_today_move_up)) }
                        TextButton(onClick = { viewModel.move(container.id, 1) }) { Text(stringResource(R.string.feature_today_move_down)) }
                    }
                }
            }
        }
    }
    if (showEditor) {
        ContainerDialog(editing, onDismiss = { showEditor = false }) { name, amount, icon ->
            if (viewModel.save(name, amount, icon, editing)) {
                showEditor = false
                true
            } else {
                false
            }
        }
    }
}

@Composable
private fun ContainerDialog(existing: Container?, onDismiss: () -> Unit, onSave: (String, Int, ContainerIcon) -> Boolean) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var amount by remember(existing?.id) { mutableStateOf(existing?.volumeMl?.toString().orEmpty()) }
    var invalid by remember(existing?.id) { mutableStateOf(false) }
    var icon by remember(existing?.id) { mutableStateOf(existing?.icon ?: ContainerIcon.GLASS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.feature_today_container_add else R.string.feature_today_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.feature_today_name)) })
                OutlinedTextField(amount, { amount = it }, label = { Text(stringResource(R.string.feature_today_volume)) })
                listOf(
                    ContainerIcon.GLASS to R.string.feature_today_icon_glass,
                    ContainerIcon.CUP to R.string.feature_today_icon_cup,
                    ContainerIcon.MUG to R.string.feature_today_icon_mug,
                    ContainerIcon.BOTTLE to R.string.feature_today_icon_bottle,
                    ContainerIcon.LARGE_BOTTLE to R.string.feature_today_icon_large_bottle,
                    ContainerIcon.FLASK to R.string.feature_today_icon_flask,
                ).forEach { (option, label) ->
                    Row {
                        RadioButton(icon == option, onClick = { icon = option })
                        TextButton(onClick = { icon = option }) { Text(stringResource(label)) }
                    }
                }
                if (invalid) Text(stringResource(R.string.feature_today_container_invalid))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                invalid = !onSave(name, amount.toIntOrNull() ?: 0, icon)
            }) { Text(stringResource(R.string.feature_today_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.feature_today_cancel)) } },
    )
}
