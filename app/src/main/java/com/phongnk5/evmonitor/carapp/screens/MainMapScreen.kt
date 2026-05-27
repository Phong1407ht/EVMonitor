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
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.model.*
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import kotlinx.coroutines.launch

class MainMapScreen(carContext: CarContext, private val viewModel: EvViewModel) : Screen(carContext) {

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
        lifecycleScope.launch {
            viewModel.currentRoutePolyline.collect { invalidate() }
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
                    viewModel.setCameraTarget(currentLat, currentLng)
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
                    viewModel.setCameraTarget(currentLat, currentLng)
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

    private fun getListLimit(): Int {
        return carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }

    override fun onGetTemplate(): Template {
        val permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val isPermissionGranted = permissions.all { carContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

        if (!isPermissionGranted) {
            return MessageTemplate.Builder("Ứng dụng cần quyền vị trí.")
                .setHeader(
                    Header.Builder()
                        .setStartHeaderAction(Action.APP_ICON)
                        .build()
                )
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

        val hasActiveRoute = viewModel.currentRoutePolyline.value != null
        val itemListBuilder = ItemList.Builder()
        val visibleStations = viewModel.stations.value.take(getListLimit())

        if (visibleStations.isEmpty()) {
            itemListBuilder.setNoItemsMessage(viewModel.apiStatus.value ?: "Đang tìm trạm sạc...")
        } else {
            visibleStations.forEachIndexed { index, station ->
                val distanceSpan = DistanceSpan.create(
                    Distance.create(station.distance, Distance.UNIT_KILOMETERS)
                )

                val title = SpannableString("${index + 1}. ${station.name}  ")
                title.setSpan(
                    distanceSpan,
                    title.length - 1,
                    title.length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val secondaryText = if (station.duration.isNotEmpty()) {
                    "Thời gian dự kiến: ${station.duration}"
                } else {
                    station.address
                }

                val rowBuilder = Row.Builder()
                    .setTitle(title)
                    .addText(secondaryText)

                if (station.duration.isNotEmpty()) {
                    rowBuilder.addText(station.address)
                }

                itemListBuilder.addItem(
                    rowBuilder
                        .setOnClickListener {
                            viewModel.setCameraTarget(currentLat, currentLng)
                            viewModel.getDirection(
                                currentLat, currentLng,
                                station.latitude, station.longitude
                            )
                        }
                        .build()
                )
            }

            itemListBuilder.setOnItemsVisibilityChangedListener { startIndex, endIndex ->
                viewModel.setVisibleRange(startIndex, endIndex)
            }
        }

        val headerBuilder = Header.Builder()
            .setTitle(
                when {
                    hasActiveRoute -> "Đang dẫn đường"
                    isLowBattery -> "Trạm sạc khẩn cấp"
                    else -> "Trạm sạc lân cận"
                }
            )
            .setStartHeaderAction(Action.APP_ICON)

        if (hasActiveRoute) {
            headerBuilder.addEndHeaderAction(
                Action.Builder()
                    .setTitle("Hủy")
                    .setOnClickListener { viewModel.clearRoute() }
                    .build()
            )
        }

        val listTemplate = ListTemplate.Builder()
            .setHeader(headerBuilder.build())
            .setSingleList(itemListBuilder.build())
            .build()

        return MapWithContentTemplate.Builder()
            .setContentTemplate(listTemplate)
            .build()
    }
}