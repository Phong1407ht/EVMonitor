package com.phongnk5.evmonitor

import android.content.Intent
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.phongnk5.evmonitor.carapp.screens.MainMapScreen
import com.phongnk5.evmonitor.data.GoongConfig
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.OnMapReadyCallback
import org.maplibre.android.maps.Style

class EVCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = EvSession()
}

class EvSession : Session(), SurfaceCallback, OnMapReadyCallback {
    private var mapLibreMap: MapLibreMap? = null

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@EvSession)
            }
        })
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        synchronized(this) { }
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        mapLibreMap = null
    }

    override fun onMapReady(map: MapLibreMap) {
        mapLibreMap = map
        map.setStyle(Style.Builder().fromUri(GoongConfig.getStyleUrl()))
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainMapScreen(carContext)
    }
}
