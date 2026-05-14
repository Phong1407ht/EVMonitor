package com.phongnk5.evmonitor.domain.repository

import com.phongnk5.evmonitor.domain.model.ChargingStation
import kotlinx.coroutines.flow.Flow

interface ChargingStationRepository {
    fun getChargingStations(): Flow<List<ChargingStation>>
}
