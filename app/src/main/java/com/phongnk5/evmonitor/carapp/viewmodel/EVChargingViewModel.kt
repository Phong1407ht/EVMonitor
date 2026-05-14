package com.phongnk5.evmonitor.carapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.usecase.GetChargingStationsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

class EVChargingViewModel @Inject constructor(
    private val getChargingStationsUseCase: GetChargingStationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<EVChargingUiState>(EVChargingUiState.Loading)
    val uiState: StateFlow<EVChargingUiState> = _uiState.asStateFlow()

    init {
        loadStations()
    }

    fun loadStations() {
        viewModelScope.launch {
            _uiState.value = EVChargingUiState.Loading
            try {
                getChargingStationsUseCase().collect { stations ->
                    _uiState.value = EVChargingUiState.Success(stations)
                }
            } catch (e: Exception) {
                _uiState.value = EVChargingUiState.Error(e.message ?: "Unknown Error")
            }
        }
    }
}

sealed class EVChargingUiState {
    object Loading : EVChargingUiState()
    data class Success(val stations: List<ChargingStation>) : EVChargingUiState()
    data class Error(val message: String) : EVChargingUiState()
}
