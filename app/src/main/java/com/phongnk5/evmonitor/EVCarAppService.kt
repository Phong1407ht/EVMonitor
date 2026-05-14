package com.phongnk5.evmonitor

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.phongnk5.evmonitor.carapp.screen.EVChargingScreen
import com.phongnk5.evmonitor.carapp.viewmodel.EVChargingViewModel
import com.phongnk5.evmonitor.domain.usecase.GetChargingStationsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@AndroidEntryPoint
class EVCarAppService : CarAppService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EVCarAppServiceEntryPoint {
        fun getGetChargingStationsUseCase(): GetChargingStationsUseCase
    }

    override fun createHostValidator(): HostValidator {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(): Session {
        return object : Session(), ViewModelStoreOwner {
            private val mViewModelStore = ViewModelStore()

            override val viewModelStore: ViewModelStore
                get() = mViewModelStore

            init {
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        mViewModelStore.clear()
                    }
                })
            }

            override fun onCreateScreen(intent: Intent): Screen {
                val entryPoint = EntryPointAccessors.fromApplication(
                    applicationContext,
                    EVCarAppServiceEntryPoint::class.java
                )

                val factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        if (modelClass.isAssignableFrom(EVChargingViewModel::class.java)) {
                            return EVChargingViewModel(entryPoint.getGetChargingStationsUseCase()) as T
                        }
                        throw IllegalArgumentException("Unknown ViewModel class")
                    }
                }

                val viewModel = ViewModelProvider(this, factory)[EVChargingViewModel::class.java]
                
                return EVChargingScreen(carContext, viewModel)
            }
        }
    }
}
