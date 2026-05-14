package com.phongnk5.evmonitor.data.repository

import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.domain.repository.ChargingStationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargingStationRepositoryImpl @Inject constructor() : ChargingStationRepository {
    override fun getChargingStations(): Flow<List<ChargingStation>> = flow {
        val stations = listOf(
            ChargingStation("1", "Trạm sạc VinFast Ocean Park", "Gia Lâm, Hà Nội", 21.002, 105.938, 2.5, 5),
            ChargingStation("2", "Trạm sạc VinFast Royal City", "Thanh Xuân, Hà Nội", 21.001, 105.815, 5.0, 3),
            ChargingStation("3", "Trạm sạc VinFast Times City", "Hai Bà Trưng, Hà Nội", 20.995, 105.867, 4.2, 8),
            ChargingStation("4", "Trạm sạc Long Biên", "Long Biên, Hà Nội", 21.045, 105.875, 7.1, 2)
        )
        emit(stations)
    }
}
