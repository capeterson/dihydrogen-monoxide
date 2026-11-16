package casa.peterson.dhmo.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.ContainerRepository
import casa.peterson.dhmo.core.data.repository.SettingsRepository
import casa.peterson.dhmo.core.model.Container
import casa.peterson.dhmo.core.model.ContainerIcon
import casa.peterson.dhmo.core.model.settings.VolumeUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContainersState(val containers: List<Container> = emptyList(), val units: VolumeUnit = VolumeUnit.ML)

@HiltViewModel
class ContainersViewModel @Inject constructor(
    private val repository: ContainerRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    val state = combine(repository.observeAll(), settings.settings) { containers, current -> ContainersState(containers, current.units) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContainersState())

    fun save(name: String, volumeMl: Int, icon: ContainerIcon, existing: Container? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || volumeMl !in 1..10_000) return false
        viewModelScope.launch {
            val container = existing?.copy(name = trimmed, volumeMl = volumeMl, icon = icon, updatedAt = clock.instant())
                ?: Container(UUID.randomUUID(), trimmed, volumeMl, icon, repository.getAll().size, clock.instant())
            repository.upsert(container)
        }
        return true
    }

    fun delete(id: UUID) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun move(id: UUID, direction: Int) {
        viewModelScope.launch {
            val ids = repository.getAll().map { it.id }.toMutableList()
            val index = ids.indexOf(id)
            val destination = index + direction
            if (index < 0 || destination !in ids.indices) return@launch
            java.util.Collections.swap(ids, index, destination)
            repository.reorder(ids)
        }
    }
}
