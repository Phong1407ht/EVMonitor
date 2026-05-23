package com.phongnk5.evmonitor.di

import com.phongnk5.evmonitor.domain.usecase.GetNearbyStationsUseCase
import com.phongnk5.evmonitor.domain.usecase.GetPlaceDetailUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CarAppEntryPoint {
    fun getStationsUseCase(): GetNearbyStationsUseCase
    fun getPlaceDetailUseCase(): GetPlaceDetailUseCase
}
