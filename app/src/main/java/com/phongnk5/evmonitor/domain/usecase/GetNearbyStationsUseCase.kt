package com.phongnk5.evmonitor.domain.usecase

import com.phongnk5.evmonitor.domain.repository.EvRepository
import javax.inject.Inject

class GetNearbyStationsUseCase @Inject constructor(private val repository: EvRepository) {
    suspend operator fun invoke(lat: Double, lng: Double) = repository.getNearbyStations(lat, lng)
}
