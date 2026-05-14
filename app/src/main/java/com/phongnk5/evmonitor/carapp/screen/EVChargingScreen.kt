package com.phongnk5.evmonitor.carapp.screen

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.car.app.model.Distance
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.phongnk5.evmonitor.carapp.viewmodel.EVChargingUiState
import com.phongnk5.evmonitor.carapp.viewmodel.EVChargingViewModel
import com.phongnk5.evmonitor.domain.model.ChargingStation
import kotlinx.coroutines.launch

class EVChargingScreen(
    carContext: CarContext,
    private val viewModel: EVChargingViewModel
) : Screen(carContext) {

    private var uiState: EVChargingUiState = EVChargingUiState.Loading

    init {
        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    uiState = state
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        return when (val state = uiState) {
            is EVChargingUiState.Loading -> {
                PlaceListMapTemplate.Builder()
                    .setTitle("Tìm trạm sạc")
                    .setLoading(true)
                    .setHeaderAction(Action.APP_ICON)
                    .build()
            }
            is EVChargingUiState.Success -> {
                buildSuccessTemplate(state.stations)
            }
            is EVChargingUiState.Error -> {
                MessageTemplate.Builder(state.message)
                    .setTitle("Lỗi")
                    .setHeaderAction(Action.BACK)
                    .build()
            }
        }
    }

    private fun buildSuccessTemplate(stations: List<ChargingStation>): Template {
        val listBuilder = ItemList.Builder()

        if (stations.isEmpty()) {
            return MessageTemplate.Builder("Không tìm thấy trạm sạc nào")
                .setTitle("Trạm sạc")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }

        stations.forEach { station ->
            val displayTitle = station.name.ifEmpty { "Trạm sạc" }
            val titleWithDistance = SpannableString(displayTitle).apply {
                setSpan(
                    DistanceSpan.create(
                        Distance.create(maxOf(0.0, station.distanceKm), Distance.UNIT_KILOMETERS)
                    ),
                    0,
                    displayTitle.length,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
            }

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(titleWithDistance)
                    .addText("${String.format("%.1f", station.distanceKm)} km · ${station.address}")
                    .addText("${station.availableConnectors} cổng trống")
                    .setMetadata(
                        Metadata.Builder()
                            .setPlace(
                                Place.Builder(CarLocation.create(station.latitude, station.longitude))
                                    .setMarker(PlaceMarker.Builder().build())
                                    .build()
                            )
                            .build()
                    )
                    .setOnClickListener { 
                        // Xử lý khi nhấn vào trạm sạc (ví dụ: dẫn đường)
                    }
                    .build()
            )
        }

        return PlaceListMapTemplate.Builder()
            .setTitle("Trạm sạc gần đây")
            .setItemList(listBuilder.build())
            .setCurrentLocationEnabled(true)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
