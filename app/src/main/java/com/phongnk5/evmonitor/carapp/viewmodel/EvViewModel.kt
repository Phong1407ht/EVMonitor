package com.phongnk5.evmonitor.carapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.usecase.GetNearbyStationsUseCase
import com.phongnk5.evmonitor.domain.usecase.GetPlaceDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EvViewModel(
    private val getNearbyStationsUseCase: GetNearbyStationsUseCase,
    private val getPlaceDetailUseCase: GetPlaceDetailUseCase
) : ViewModel() {

    private val _stations = MutableStateFlow<List<ChargingStation>>(emptyList())
    val stations: StateFlow<List<ChargingStation>> = _stations.asStateFlow()

    private val _selectedStationDetail = MutableStateFlow<GoongPlaceDetailResult?>(null)
    val selectedStationDetail: StateFlow<GoongPlaceDetailResult?> = _selectedStationDetail.asStateFlow()

    private val _apiStatus = MutableStateFlow<String?>(null)
    val apiStatus: StateFlow<String?> = _apiStatus.asStateFlow()

    fun fetchStations(lat: Double, lng: Double) {
        viewModelScope.launch {
            _apiStatus.value = "Đang tìm danh sách trạm..."
            getNearbyStationsUseCase(lat, lng).onSuccess { data ->
                _stations.value = data
                _apiStatus.value = "Tìm thấy ${data.size} trạm"
            }.onFailure { e ->
                _apiStatus.value = "Lỗi tải danh sách: ${e.message}"
            }
        }
    }

    fun fetchPlaceDetail(placeId: String) {
        viewModelScope.launch {
            _selectedStationDetail.value = null
            _apiStatus.value = "Đang gọi API Place Detail (ID: $placeId)..."
            
            getPlaceDetailUseCase(placeId).onSuccess { detail ->
                _selectedStationDetail.value = detail
                _apiStatus.value = "API Place Detail thành công: ${detail.name}"
                updateStationInList(detail)
            }.onFailure { e ->
                _apiStatus.value = "API Place Detail thất bại: ${e.message}"
                Log.e("EvViewModel", "Error fetching detail", e)
            }
        }
    }

    private fun updateStationInList(detail: GoongPlaceDetailResult) {
        _stations.update { currentList ->
            currentList.map { station ->
                if (station.id == detail.place_id) {
                    station.copy(
                        latitude = detail.geometry.location.lat,
                        longitude = detail.geometry.location.lng,
                        address = detail.formatted_address
                    )
                } else {
                    station
                }
            }
        }
    }
}
