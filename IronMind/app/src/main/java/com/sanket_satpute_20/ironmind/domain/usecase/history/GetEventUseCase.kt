package com.sanket_satpute_20.ironmind.domain.usecase.history

import com.sanket_satpute_20.ironmind.domain.common.Result
import com.sanket_satpute_20.ironmind.domain.model.Event
import com.sanket_satpute_20.ironmind.domain.repository.EventRepository

class GetEventUseCase(
    private val repository: EventRepository
) {
    suspend operator fun invoke(id: String): Result<Event?, Exception> {
        return repository.getEvent(id)
    }
}
