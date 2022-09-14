package io.github.droidkaigi.confsched2022.feature.sessions

import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.cash.molecule.AndroidUiDispatcher
import app.cash.molecule.RecompositionClock.ContextClock
import co.touchlab.kermit.Logger
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.droidkaigi.confsched2022.model.SessionsRepository
import io.github.droidkaigi.confsched2022.ui.UiLoadState
import io.github.droidkaigi.confsched2022.ui.asLoadState
import io.github.droidkaigi.confsched2022.ui.moleculeComposeState
import io.github.droidkaigi.confsched2022.zipline.SessionsZipline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    sessionsRepository: SessionsRepository,
    sessionsZipline: SessionsZipline
) : ViewModel() {
    private val moleculeScope =
        CoroutineScope(viewModelScope.coroutineContext + AndroidUiDispatcher.Main)

    val uiModel: State<SearchUiModel>

    init {
        val ziplineScheduleModifierFlow =
            sessionsZipline.timetableModifier(coroutineScope = viewModelScope)
        val sessionScheduleFlow = sessionsRepository.droidKaigiScheduleFlow()

        val scheduleFlow = combine(
            ziplineScheduleModifierFlow,
            sessionScheduleFlow,
            ::Pair
        ).map { (modifier, schedule) ->
            try {
                withTimeout(100) {
                    modifier(schedule)
                }
            } catch (e: Exception) {
                Logger.d(throwable = e) { "Zipline modifier error" }
                schedule
            }
        }.asLoadState()

        uiModel = moleculeScope.moleculeComposeState(clock = ContextClock) {
            val schedule by scheduleFlow.collectAsState(initial = UiLoadState.Loading)

            SearchUiModel(state = schedule)
        }
    }
}
