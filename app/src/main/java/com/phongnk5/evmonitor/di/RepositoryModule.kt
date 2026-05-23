package com.phongnk5.evmonitor.di

import com.phongnk5.evmonitor.data.apiservice.GoongApiService
import com.phongnk5.evmonitor.data.repository.EvRepositoryImpl
import com.phongnk5.evmonitor.domain.repository.EvRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideEvRepository(api: GoongApiService): EvRepository = EvRepositoryImpl(api)
}