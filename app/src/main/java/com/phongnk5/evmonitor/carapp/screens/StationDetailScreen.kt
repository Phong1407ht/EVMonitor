package com.phongnk5.evmonitor.carapp.screens

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.lifecycle.lifecycleScope
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import kotlinx.coroutines.launch

class StationDetailScreen(
    carContext: CarContext,
    private val placeId: String,
    private val viewModel: EvViewModel
) : Screen(carContext) {

    init {
        viewModel.fetchPlaceDetail(placeId)
        lifecycleScope.launch { 
            viewModel.selectedStationDetail.collect { invalidate() } 
        }
    }

    override fun onGetTemplate(): Template {
        val detail = viewModel.selectedStationDetail.value
        val status = viewModel.apiStatus.value

        if (detail == null) {
            val paneBuilder = Pane.Builder()
                .setLoading(true)

            if (status?.contains("thất bại") == true || status?.contains("Lỗi") == true) {
                paneBuilder.setLoading(false)
                paneBuilder.addRow(Row.Builder().setTitle("Lỗi kết nối").addText(status).build())
            }

            return PaneTemplate.Builder(paneBuilder.build())
                .setTitle("Chi tiết trạm")
                .setHeaderAction(Action.BACK)
                .build()
        }

        val paneBuilder = Pane.Builder()
            .addRow(Row.Builder().setTitle("Địa chỉ").addText(detail.formatted_address).build())
            .addRow(Row.Builder().setTitle("Vị trí").addText("${detail.geometry.location.lat}, ${detail.geometry.location.lng}").build())

        paneBuilder.addAction(
            Action.Builder()
                .setTitle("Dẫn đường")
                .setOnClickListener {

                    val destLat = detail.geometry.location.lat
                    val destLng = detail.geometry.location.lng
                    viewModel.getDirection(21.0138, 105.5269, destLat, destLng)
                    screenManager.pop()
                }
                .build()
        )

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(detail.name)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
