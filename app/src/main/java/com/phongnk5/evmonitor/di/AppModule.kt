package com.phongnk5.evmonitor.di

import com.phongnk5.evmonitor.data.repository.ChargingStationRepositoryImpl
import com.phongnk5.evmonitor.domain.repository.ChargingStationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindChargingStationRepository(
        chargingStationRepositoryImpl: ChargingStationRepositoryImpl
    ): ChargingStationRepository
}
