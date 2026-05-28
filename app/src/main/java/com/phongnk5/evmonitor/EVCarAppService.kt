package com.phongnk5.evmonitor

import android.content.Intent
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.util.Log
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
import com.phongnk5.evmonitor.carapp.screens.MapPresentation
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import com.phongnk5.evmonitor.di.CarAppEntryPoint
import dagger.hilt.android.EntryPointAccessors
import org.maplibre.android.MapLibre

class EVCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = EvSession()
}

class EvSession : Session(), SurfaceCallback {

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MapPresentation? = null

    private val viewModel: EvViewModel by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(carContext, CarAppEntryPoint::class.java)
        EvViewModel(
            entryPoint.getStationsUseCase(),
            entryPoint.getDirectionUseCase()
        )
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                MapLibre.getInstance(carContext)
                carContext.getCarService(AppManager::class.java)
                    .setSurfaceCallback(this@EvSession)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                releaseMap()
            }
        })
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        Log.d(TAG, "Surface available: ${container.width}x${container.height} dpi=${container.dpi}")
        createVirtualDisplay(container)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        releaseMap()
    }

    private fun createVirtualDisplay(container: SurfaceContainer) {
        releaseMap()

        val surface = container.surface ?: return
        val width = container.width
        val height = container.height
        val dpi = container.dpi.takeIf { it > 0 } ?: 160

        val displayManager = carContext.getSystemService(DisplayManager::class.java) ?: return

        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION

        virtualDisplay = displayManager.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width,
            height,
            dpi,
            surface,
            flags
        )

        val display = virtualDisplay?.display ?: run {
            Log.e(TAG, "Không tạo được VirtualDisplay")
            return
        }

        presentation = MapPresentation(carContext, display, viewModel).also {
            it.show()
        }
    }

    private fun releaseMap() {
        try {
            presentation?.release()
            presentation?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi dismiss presentation", e)
        }
        presentation = null

        virtualDisplay?.release()
        virtualDisplay = null
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainMapScreen(carContext, viewModel)
    }

    companion object {
        private const val TAG = "EvSession"
        private const val VIRTUAL_DISPLAY_NAME = "EvMapDisplay"
    }
}