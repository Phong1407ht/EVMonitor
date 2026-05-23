package com.phongnk5.evmonitor.domain.usecase

import com.phongnk5.evmonitor.domain.repository.EvRepository
import javax.inject.Inject

class GetPlaceDetailUseCase @Inject constructor(private val repository: EvRepository) {
    suspend operator fun invoke(placeId: String) = repository.getPlaceDetail(placeId)
}
