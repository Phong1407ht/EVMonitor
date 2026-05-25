package com.phongnk5.evmonitor.data.repository

import android.util.Log
import com.phongnk5.evmonitor.data.apiservice.GoongApiService
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.repository.EvRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class EvRepositoryImpl(private val api: GoongApiService) : EvRepository {
    override suspend fun getNearbyStations(lat: Double, lng: Double): Result<List<ChargingStation>> = coroutineScope {
        Log.d("EvRepository", ">>> [API CALL] Autocomplete with: $lat,$lng")
        try {
            val autocompleteResponse = api.autocomplete(
                input = "trạm sạc",
                location = "$lat,$lng"
            )
            
            if (autocompleteResponse.predictions.isEmpty()) {
                return@coroutineScope Result.success(emptyList())
            }

            val detailDeferreds = autocompleteResponse.predictions.map { prediction ->
                async { 
                    try {
                        api.getPlaceDetail(prediction.place_id).result
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            val details = detailDeferreds.awaitAll().filterNotNull()

            if (details.isEmpty()) {
                return@coroutineScope Result.success(emptyList())
            }

            val origins = "$lat,$lng"
            val destinations = details.joinToString("|") { 
                "${it.geometry.location.lat},${it.geometry.location.lng}" 
            }
            
            val distanceMatrixResponse = try {
                api.getDistanceMatrix(origins, destinations)
            } catch (e: Exception) {
                Log.e("EvRepository", "Distance Matrix failed", e)
                null
            }

            val stations = details.mapIndexed { index, detail ->
                val distanceKm = distanceMatrixResponse?.rows?.firstOrNull()?.elements?.getOrNull(index)?.distance?.value?.div(1000.0) ?: 0.0
                
                ChargingStation(
                    id = detail.place_id,
                    name = detail.name,
                    address = detail.formatted_address,
                    latitude = detail.geometry.location.lat,
                    longitude = detail.geometry.location.lng,
                    distance = distanceKm
                )
            }

            Result.success(stations.sortedBy { it.distance })
        } catch (e: Exception) {
            Log.e("EvRepository", ">>> [ERROR] getNearbyStations failed", e)
            Result.failure(e)
        }
    }

    override suspend fun getPlaceDetail(placeId: String): Result<GoongPlaceDetailResult> {
        return try {
            val response = api.getPlaceDetail(placeId)
            if (response.status == "OK") {
                Result.success(response.result)
            } else {
                Result.failure(Exception("API Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
