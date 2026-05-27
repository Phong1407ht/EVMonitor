package com.phongnk5.evmonitor

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.phongnk5.evmonitor.carapp.screens.MainMapScreen
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import com.phongnk5.evmonitor.data.GoongConfig
import com.phongnk5.evmonitor.di.CarAppEntryPoint
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.utils.PolylineDecoder
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshot
import org.maplibre.android.snapshotter.MapSnapshotter

class EVCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    override fun onCreateSession(): Session = EvSession()
}

private data class IndexedStation(val originalIndex: Int, val station: ChargingStation)

class EvSession : Session(), SurfaceCallback {

    private var surface: Surface? = null
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var snapshotter: MapSnapshotter? = null
    private var isRendering = false

    private val defaultZoom = 14.0
    private val boundsPaddingFactor = 0.15

    private val viewModel: EvViewModel by lazy {
        val entryPoint = EntryPointAccessors.fromApplication(carContext, CarAppEntryPoint::class.java)
        EvViewModel(
            entryPoint.getStationsUseCase(),
            entryPoint.getPlaceDetailUseCase(),
            entryPoint.getDirectionUseCase()
        )
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                MapLibre.getInstance(carContext)
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@EvSession)
                observeViewModel()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                snapshotter?.cancel()
                snapshotter = null
            }
        })
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.cameraTarget.collect { renderMap() } }
                launch { viewModel.stations.collect { renderMap() } }
                launch { viewModel.currentRoutePolyline.collect { renderMap() } }
                launch { viewModel.visibleRange.collect { renderMap() } }
            }
        }
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        surface = container.surface
        surfaceWidth = container.width
        surfaceHeight = container.height
        Log.d(TAG, "Surface available: ${surfaceWidth}x${surfaceHeight}")
        renderMap()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        renderMap()
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        snapshotter?.cancel()
        snapshotter = null
        surface = null
    }

    private fun getListLimit(): Int {
        return carContext.getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
    }

    private fun getStationsInView(): List<IndexedStation> {
        val all = viewModel.stations.value.take(getListLimit())
        val range = viewModel.visibleRange.value

        return if (range == null) {
            all.mapIndexed { idx, st -> IndexedStation(idx, st) }
        } else {
            all.mapIndexedNotNull { idx, st ->
                if (idx in range) IndexedStation(idx, st) else null
            }
        }
    }

    private fun renderMap() {
        val s = surface ?: return
        if (!s.isValid || surfaceWidth == 0 || surfaceHeight == 0) return
        if (isRendering) return

        val target = viewModel.cameraTarget.value ?: LatLng(21.0138, 105.5269)
        val stationsInView = getStationsInView()
        val routePoints = viewModel.currentRoutePolyline.value
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { PolylineDecoder.decode(it) }.getOrNull() }
            ?.takeIf { it.size >= 2 }

        isRendering = true
        snapshotter?.cancel()

        val optionsBase = MapSnapshotter.Options(surfaceWidth, surfaceHeight)
            .withStyleBuilder(Style.Builder().fromUri(GoongConfig.getStyleUrl()))
            .withLogo(false)
            .withAttribution(false)

        val options = when {
            routePoints != null -> optionsBase.withRegion(buildBoundsFromPoints(routePoints))
            stationsInView.isNotEmpty() -> optionsBase.withRegion(buildBoundsForStations(target, stationsInView))
            else -> optionsBase.withCameraPosition(
                CameraPosition.Builder().target(target).zoom(defaultZoom).build()
            )
        }

        snapshotter = MapSnapshotter(carContext, options).apply {
            start(
                { snapshot -> onSnapshotReady(snapshot, stationsInView, routePoints) },
                { error ->
                    Log.e(TAG, "Snapshot error: $error")
                    isRendering = false
                }
            )
        }
    }

    private fun buildBoundsForStations(currentLoc: LatLng, stations: List<IndexedStation>): LatLngBounds {
        val builder = LatLngBounds.Builder()
        builder.include(currentLoc)
        stations.forEach { builder.include(LatLng(it.station.latitude, it.station.longitude)) }
        return padBounds(builder.build())
    }

    private fun buildBoundsFromPoints(points: List<LatLng>): LatLngBounds {
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        return padBounds(builder.build())
    }

    private fun padBounds(raw: LatLngBounds): LatLngBounds {
        val latSpan = (raw.latitudeNorth - raw.latitudeSouth).coerceAtLeast(0.005)
        val lngSpan = (raw.longitudeEast - raw.longitudeWest).coerceAtLeast(0.005)
        val latPad = latSpan * boundsPaddingFactor
        val lngPad = lngSpan * boundsPaddingFactor
        return LatLngBounds.from(
            (raw.latitudeNorth + latPad).coerceAtMost(85.0),
            raw.longitudeEast + lngPad,
            (raw.latitudeSouth - latPad).coerceAtLeast(-85.0),
            raw.longitudeWest - lngPad
        )
    }

    private fun onSnapshotReady(
        snapshot: MapSnapshot,
        stationsInView: List<IndexedStation>,
        routePoints: List<LatLng>?
    ) {
        val s = surface
        if (s == null || !s.isValid) {
            isRendering = false
            return
        }

        try {
            val canvas = s.lockCanvas(null)
            try {
                canvas.drawBitmap(snapshot.bitmap, 0f, 0f, null)
                routePoints?.let { drawRoute(canvas, snapshot, it) }
                drawStationMarkers(canvas, snapshot, stationsInView)
                drawCurrentLocation(canvas, snapshot)
            } finally {
                s.unlockCanvasAndPost(canvas)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi vẽ Surface", e)
        } finally {
            isRendering = false
        }
    }

    private fun drawRoute(canvas: Canvas, snapshot: MapSnapshot, points: List<LatLng>) {
        if (points.size < 2) return

        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 16f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        val first = snapshot.pixelForLatLng(points[0])
        path.moveTo(first.x, first.y)
        for (i in 1 until points.size) {
            val pt = snapshot.pixelForLatLng(points[i])
            path.lineTo(pt.x, pt.y)
        }

        canvas.drawPath(path, outlinePaint)
        canvas.drawPath(path, routePaint)
    }

    private fun drawStationMarkers(
        canvas: Canvas,
        snapshot: MapSnapshot,
        stationsInView: List<IndexedStation>
    ) {
        stationsInView.forEach { indexed ->
            val station = indexed.station
            val pt = snapshot.pixelForLatLng(LatLng(station.latitude, station.longitude))
            if (pt.x < -50 || pt.x > surfaceWidth + 50) return@forEach
            if (pt.y < -50 || pt.y > surfaceHeight + 50) return@forEach
            drawStationPin(canvas, pt.x, pt.y, indexed.originalIndex + 1)
        }
    }

    private fun drawStationPin(canvas: Canvas, x: Float, y: Float, number: Int) {
        val radius = 22f

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x + 2f, y + 3f, radius, shadow)

        val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius, outer)

        val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4CAF50")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, radius - 4f, inner)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = radius * 1.1f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = y - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), x, textY, textPaint)
    }

    private fun drawCurrentLocation(canvas: Canvas, snapshot: MapSnapshot) {
        val target = viewModel.cameraTarget.value ?: return
        val pt = snapshot.pixelForLatLng(target)

        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pt.x + 2f, pt.y + 3f, 18f, shadow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pt.x, pt.y, 18f, ring)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(pt.x, pt.y, 14f, dot)
    }

    override fun onCreateScreen(intent: Intent): Screen {
        return MainMapScreen(carContext, viewModel)
    }

    companion object {
        private const val TAG = "EvSession"
    }
}