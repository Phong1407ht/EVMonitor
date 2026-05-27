package com.phongnk5.evmonitor.data.repository

import android.util.Log
import com.phongnk5.evmonitor.data.apiservice.GoongApiService
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.data.DTOs.GoongDirectionResponse
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.repository.EvRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import retrofit2.HttpException

class EvRepositoryImpl(private val api: GoongApiService) : EvRepository {
    override suspend fun getNearbyStations(lat: Double, lng: Double): Result<List<ChargingStation>> = coroutineScope {
        Log.d("EvRepository", ">>> [API CALL] Autocomplete with: $lat,$lng")
        try {
            val autocompleteResponse = api.autocomplete(
                input = "trạm sạc ô tô điện",
                location = "$lat,$lng"
            )
            
            if (autocompleteResponse.predictions.isEmpty()) {
                return@coroutineScope Result.success(emptyList())
            }

            val detailDeferreds = autocompleteResponse.predictions.mapIndexed { index, prediction ->
                async { 
                    try {
                        if (index > 0) delay(index * 100L)
                        retryApiCall { api.getPlaceDetail(prediction.place_id).result }
                    } catch (e: Exception) {
                        Log.e("EvRepository", "Failed to get detail for ${prediction.place_id}", e)
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
                retryApiCall { api.getDistanceMatrix(origins, destinations) }
            } catch (e: Exception) {
                Log.e("EvRepository", "Distance Matrix failed", e)
                null
            }

            val stations = details.mapIndexed { index, detail ->
                val element = distanceMatrixResponse?.rows?.firstOrNull()?.elements?.getOrNull(index)
                val distanceKm = element?.distance?.value?.div(1000.0) ?: 0.0
                val durationText = element?.duration?.text ?: ""
                
                ChargingStation(
                    id = detail.place_id,
                    name = detail.name,
                    address = detail.formatted_address,
                    latitude = detail.geometry.location.lat,
                    longitude = detail.geometry.location.lng,
                    distance = distanceKm,
                    duration = durationText
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
            val response = retryApiCall { api.getPlaceDetail(placeId) }
            if (response.status == "OK") {
                Result.success(response.result)
            } else {
                Result.failure(Exception("API Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDirection(origin: String, destination: String): Result<GoongDirectionResponse> {
        return try {
            val response = retryApiCall { api.getDirection(origin, destination) }
            if (response.status == "OK") {
                Result.success(response)
            } else {
                Result.failure(Exception("API Error: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun <T> retryApiCall(
        times: Int = 3,
        initialDelay: Long = 1000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelay
        repeat(times - 1) {
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code() != 429) throw e
                Log.w("EvRepository", "HTTP 429 detected, retrying in $currentDelay ms...")
            } catch (e: Exception) {
                throw e
            }
            delay(currentDelay)
            currentDelay *= 2
        }
        return block()
    }
}
