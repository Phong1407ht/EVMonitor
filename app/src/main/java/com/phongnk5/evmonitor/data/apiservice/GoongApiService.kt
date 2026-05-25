package com.phongnk5.evmonitor.data.apiservice

import com.phongnk5.evmonitor.data.DTOs.GoongAutocompleteResponse
import com.phongnk5.evmonitor.data.DTOs.GoongDistanceMatrixResponse
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResponse
import com.phongnk5.evmonitor.data.GoongConfig
import retrofit2.http.GET
import retrofit2.http.Query

interface GoongApiService {
    @GET("Place/Autocomplete")
    suspend fun autocomplete(
        @Query("input") input: String,
        @Query("location") location: String,
        @Query("limit") limit: Int = 10,
        @Query("radius") radius: Int = 10,
        @Query("api_key") apiKey: String = GoongConfig.REST_API_KEY
    ): GoongAutocompleteResponse

    @GET("Place/Detail")
    suspend fun getPlaceDetail(
        @Query("place_id") placeId: String,
        @Query("api_key") apiKey: String = GoongConfig.REST_API_KEY
    ): GoongPlaceDetailResponse

    @GET("distancematrix")
    suspend fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("vehicle") vehicle: String = "car",
        @Query("api_key") apiKey: String = GoongConfig.REST_API_KEY
    ): GoongDistanceMatrixResponse
}
