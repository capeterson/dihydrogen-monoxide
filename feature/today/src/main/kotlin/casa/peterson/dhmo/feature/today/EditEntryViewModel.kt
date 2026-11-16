package casa.peterson.dhmo.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import casa.peterson.dhmo.core.data.repository.IntakeRepository
import casa.peterson.dhmo.core.data.usecase.intake.EditIntakeUseCase
import casa.peterson.dhmo.core.model.IntakeEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface EditEntryState {
    data object Loading : EditEntryState
    data object Missing : EditEntryState
    data class Ready(val entry: IntakeEntry, val error: Int? = null) : EditEntryState
}

@HiltViewModel
class EditEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repository: IntakeRepository,
    private val edit: EditIntakeUseCase,
) : ViewModel() {
    private val entryId = savedState.get<String>("entryId")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    private val mutableState = MutableStateFlow<EditEntryState>(EditEntryState.Loading)
    val state: StateFlow<EditEntryState> = mutableState.asStateFlow()
    private val completed = Channel<Unit>(Channel.BUFFERED)
    val completedEvents = completed.receiveAsFlow()

    init {
        viewModelScope.launch {
            val entry = entryId?.let { repository.get(it) }
            mutableState.value = if (entry == null || entry.deletedAt != null) EditEntryState.Missing else EditEntryState.Ready(entry)
        }
    }

    fun save(volumeMl: Int, at: Instant, note: String?) {
        val ready = state.value as? EditEntryState.Ready ?: return
        viewModelScope.launch {
            try {
                edit(ready.entry.id, volumeMl, at, note)
                completed.send(Unit)
            } catch (_: IllegalArgumentException) {
                mutableState.value = ready.copy(error = R.string.feature_today_invalid_amount)
            } catch (_: IllegalStateException) {
                mutableState.value = ready.copy(error = R.string.feature_today_entry_not_editable)
            } catch (_: NoSuchElementException) {
                mutableState.value = EditEntryState.Missing
            }
        }
    }
}
