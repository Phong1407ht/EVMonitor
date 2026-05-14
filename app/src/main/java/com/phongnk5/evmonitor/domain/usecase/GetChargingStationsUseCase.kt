package com.phongnk5.evmonitor.domain.usecase

import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.repository.ChargingStationRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChargingStationsUseCase @Inject constructor(
    private val repository: ChargingStationRepository
) {
    operator fun invoke(): Flow<List<ChargingStation>> = repository.getChargingStations()
}
