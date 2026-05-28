package com.phongnk5.evmonitor.domain.repository

import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult

interface ChargingStationRepository {
    suspend fun getNearbyStations(lat: Double, lng: Double): Result<List<ChargingStation>>
}
