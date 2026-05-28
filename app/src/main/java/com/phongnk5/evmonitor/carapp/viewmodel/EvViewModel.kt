package com.phongnk5.evmonitor.carapp.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phongnk5.evmonitor.data.DTOs.GoongPlaceDetailResult
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.usecase.GetNearbyStationsUseCase
import com.phongnk5.evmonitor.domain.usecase.GetDirectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

class EvViewModel(
    private val getNearbyStationsUseCase: GetNearbyStationsUseCase,
    private val getDirectionUseCase: GetDirectionUseCase
) : ViewModel() {

    private val _stations = MutableStateFlow<List<ChargingStation>>(emptyList())
    val stations: StateFlow<List<ChargingStation>> = _stations.asStateFlow()

    private val _selectedStationDetail = MutableStateFlow<GoongPlaceDetailResult?>(null)
    val selectedStationDetail: StateFlow<GoongPlaceDetailResult?> = _selectedStationDetail.asStateFlow()

    private val _apiStatus = MutableStateFlow<String?>(null)
    val apiStatus: StateFlow<String?> = _apiStatus.asStateFlow()

    private val _cameraTarget = MutableStateFlow<LatLng?>(null)
    val cameraTarget: StateFlow<LatLng?> = _cameraTarget.asStateFlow()

    private val _currentRoutePolyline = MutableStateFlow<String?>(null)
    val currentRoutePolyline: StateFlow<String?> = _currentRoutePolyline.asStateFlow()

    private val _visibleRange = MutableStateFlow<IntRange?>(null)
    val visibleRange: StateFlow<IntRange?> = _visibleRange.asStateFlow()

    fun setCameraTarget(lat: Double, lng: Double) {
        _cameraTarget.value = LatLng(lat, lng)
    }

    fun setVisibleRange(startIndex: Int, endIndexExclusive: Int) {
        if (startIndex < 0 || endIndexExclusive <= startIndex) {
            _visibleRange.value = null
            return
        }
        _visibleRange.value = startIndex until endIndexExclusive
    }

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


    fun getDirection(originLat: Double, originLng: Double, destLat: Double, destLng: Double) {
        viewModelScope.launch {
            Log.d("EvViewModel", "Đang gọi API direction từ ($originLat,$originLng) đến ($destLat,$destLng)")
            _apiStatus.value = "Đang lấy chỉ đường..."
            val origin = "$originLat,$originLng"
            val destination = "$destLat,$destLng"
            getDirectionUseCase(origin, destination).onSuccess { response ->
                val polyline = response.routes?.firstOrNull()?.overview_polyline?.points
                _currentRoutePolyline.value = polyline
                _apiStatus.value = "Đã lấy được chỉ đường"
                Log.d("EvViewModel", "Lấy chỉ đường THÀNH CÔNG: polyline=${polyline?.take(20)}...")
            }.onFailure { e ->
                _apiStatus.value = "Lỗi lấy chỉ đường: ${e.message}"
                Log.e("EvViewModel", "Lấy chỉ đường THẤT BẠI: ${e.message}", e)
            }
        }
    }

    fun clearRoute() {
        _currentRoutePolyline.value = null
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
