package com.phongnk5.evmonitor.carapp.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.text.SpannableString
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.model.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import com.phongnk5.evmonitor.di.CarAppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

class MainMapScreen(carContext: CarContext) : Screen(carContext) {

    private val entryPoint = EntryPointAccessors.fromApplication(carContext, CarAppEntryPoint::class.java)
    private val viewModel = EvViewModel(
        entryPoint.getStationsUseCase(),
        entryPoint.getPlaceDetailUseCase()
    )

    private var isLowBattery = false
    private var currentLat = 21.0138 
    private var currentLng = 105.5269 
    private var hasUpdatedLocation = false

    init {
        monitorBatteryLevel()
        checkAndStartLocation()

        lifecycleScope.launch {
            viewModel.stations.collect { invalidate() }
        }
    }

    private fun checkAndStartLocation() {
        val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (permissions.all { carContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startLocationUpdates()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationManager = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLat = location.latitude
                currentLng = location.longitude
                if (!hasUpdatedLocation) {
                    hasUpdatedLocation = true
                    viewModel.fetchStations(currentLat, currentLng)
                }
                invalidate()
            }
        }

        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000L, 10f, locationListener)
            val lastLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            lastLocation?.let {
                currentLat = it.latitude
                currentLng = it.longitude
                if (!hasUpdatedLocation) {
                    viewModel.fetchStations(currentLat, currentLng)
                }
            }
        } catch (e: Exception) {
            Log.e("MainMapScreen", "Location error", e)
        }
    }

    private fun monitorBatteryLevel() {
        val hardwareManager = carContext.getCarService(CarContext.HARDWARE_SERVICE) as CarHardwareManager
        val exec = ContextCompat.getMainExecutor(carContext)

        hardwareManager.carInfo.addEnergyLevelListener(exec) { energyLevel ->
            val batteryPercent = energyLevel.batteryPercent.value
            if (batteryPercent != null && batteryPercent <= 10f && !isLowBattery) {
                isLowBattery = true
                invalidate()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val isPermissionGranted = permissions.all { carContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        if (!isPermissionGranted) {
            return MessageTemplate.Builder("Ứng dụng cần quyền vị trí.")
                .setHeaderAction(Action.APP_ICON)
                .addAction(Action.Builder().setTitle("Cấp quyền").setOnClickListener {
                    carContext.requestPermissions(permissions) { granted, _ ->
                        if (granted.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                            startLocationUpdates()
                            invalidate()
                        }
                    }
                }.build())
                .build()
        }

        val itemListBuilder = ItemList.Builder()
        val stations = viewModel.stations.value

        if (stations.isEmpty()) {
            itemListBuilder.setNoItemsMessage(viewModel.apiStatus.value ?: "Đang tìm trạm sạc...")
        } else {
            stations.forEach { station ->
                // Hiển thị khoảng cách bằng DistanceSpan ở đầu tiêu đề
                val distanceSpan = DistanceSpan.create(
                    Distance.create(station.distance, Distance.UNIT_KILOMETERS)
                )
                
                val title = SpannableString("  ${station.name}")
                title.setSpan(distanceSpan, 0, 1, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

                // Nội dung hiển thị thời gian dự kiến và địa chỉ
                val secondaryText = if (station.duration.isNotEmpty()) {
                    "Thời gian dự kiến: ${station.duration}"
                } else {
                    station.address
                }

                val rowBuilder = Row.Builder()
                    .setTitle(title)
                    .addText(secondaryText)
                
                // Nếu đã hiển thị duration ở dòng 1, hiển thị address ở dòng 2
                if (station.duration.isNotEmpty()) {
                    rowBuilder.addText(station.address)
                }

                itemListBuilder.addItem(
                    rowBuilder
                        .setOnClickListener {
                            screenManager.push(StationDetailScreen(carContext, station.id, viewModel))
                        }
                        .setMetadata(
                            Metadata.Builder()
                                .setPlace(Place.Builder(CarLocation.create(station.latitude, station.longitude))
                                    .setMarker(PlaceMarker.Builder().build())
                                    .build())
                                .build()
                        )
                        .build()
                )
            }
        }

        val anchor = Place.Builder(CarLocation.create(currentLat, currentLng))
            .setMarker(PlaceMarker.Builder().setColor(CarColor.BLUE).build())
            .build()

        return PlaceListMapTemplate.Builder()
            .setTitle(if (isLowBattery) "Trạm sạc khẩn cấp" else "Trạm sạc lân cận")
            .setItemList(itemListBuilder.build())
            .setAnchor(anchor)
            .setCurrentLocationEnabled(true)
            .setHeaderAction(Action.APP_ICON)
            .build()
    }
}
