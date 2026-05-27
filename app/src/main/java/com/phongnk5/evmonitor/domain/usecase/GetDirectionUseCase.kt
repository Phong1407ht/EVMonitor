package com.phongnk5.evmonitor.domain.usecase

import com.phongnk5.evmonitor.data.DTOs.GoongDirectionResponse
import com.phongnk5.evmonitor.domain.repository.EvRepository
import javax.inject.Inject

class GetDirectionUseCase @Inject constructor(
    private val repository: EvRepository
) {
    suspend operator fun invoke(origin: String, destination: String): Result<GoongDirectionResponse> {
        return repository.getDirection(origin, destination)
    }
}
