package com.phongnk5.evmonitor.data.repository

import android.util.Log
import com.phongnk5.evmonitor.data.apiservice.GoongApiService
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.repository.EvRepository

class EvRepositoryImpl(private val api: GoongApiService) : EvRepository {
    override suspend fun getNearbyStations(lat: Double, lng: Double): Result<List<ChargingStation>> {
        Log.d("EvRepository", ">>> [API CALL] Autocomplete with: $lat,$lng")
        return try {
            val response = api.autocomplete(
                input = "trạm sạc",
                location = "$lat,$lng"
            )
            Log.d("EvRepository", ">>> [RESPONSE] Found ${response.predictions.size} stations")
            
            val stations = response.predictions.map { prediction ->
                ChargingStation(
                    id = prediction.place_id,
                    name = prediction.structured_formatting.main_text,
                    address = prediction.description,
                    latitude = lat, 
                    longitude = lng,
                    distance = 0.0
                )
            }
            Result.success(stations)
        } catch (e: Exception) {
            Log.e("EvRepository", ">>> [ERROR] Autocomplete failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getPlaceDetail(placeId: String): Result<GoongPlaceDetailResult> {
        Log.d("EvRepository", ">>> [API CALL] Fetching PlaceDetail for ID: $placeId")
        return try {
            val response = api.getPlaceDetail(placeId)
            Log.d("EvRepository", ">>> [RESPONSE] Status: ${response.status}")
            
            if (response.status == "OK") {
                Log.d("EvRepository", ">>> [SUCCESS] Station: ${response.result.name}")
                Result.success(response.result)
            } else {
                Log.e("EvRepository", ">>> [API ERROR] status = ${response.status}")
                Result.failure(Exception("API Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Log.e("EvRepository", ">>> [EXCEPTION] Detail call failed", e)
            Result.failure(e)
        }
    }
}
