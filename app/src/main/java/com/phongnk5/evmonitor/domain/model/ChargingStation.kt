package com.phongnk5.evmonitor.domain.model

data class ChargingStation(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Double
)
