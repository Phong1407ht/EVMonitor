package com.phongnk5.evmonitor.domain.repository

import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.data.DTOs.GoongDirectionResponse

interface EvRepository {
    suspend fun getNearbyStations(lat: Double, lng: Double): Result<List<ChargingStation>>
    suspend fun getPlaceDetail(placeId: String): Result<GoongPlaceDetailResult>
    suspend fun getDirection(origin: String, destination: String): Result<GoongDirectionResponse>
}
