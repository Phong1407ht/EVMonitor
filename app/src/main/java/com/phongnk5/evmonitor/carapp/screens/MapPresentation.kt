package com.phongnk5.evmonitor.carapp.screens

import android.app.Presentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import com.phongnk5.evmonitor.carapp.viewmodel.EvViewModel
import com.phongnk5.evmonitor.data.GoongConfig
import com.phongnk5.evmonitor.domain.model.ChargingStation
import com.phongnk5.evmonitor.utils.PolylineDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

class MapPresentation(
    context: Context,
    display: Display,
    private val viewModel: EvViewModel
) : Presentation(context, display) {

    private lateinit var mapView: MapView
    private var mapLibreMap: MapLibreMap? = null
    private var styleRef: Style? = null

    private var stationsSource: GeoJsonSource? = null
    private var currentLocSource: GeoJsonSource? = null
    private var routeSource: GeoJsonSource? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    private val maxPreloadedPins = 20

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(context)
        mapView = MapView(context)
        root.addView(
            mapView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.apply {
                isAttributionEnabled = false
                isLogoEnabled = false
                isCompassEnabled = false
            }
            map.setStyle(Style.Builder().fromUri(GoongConfig.getStyleUrl())) { style ->
                styleRef = style
                setupLayers(style)
                observeViewModel()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (::mapView.isInitialized) mapView.onStop()
    }

    fun release() {
        observeJob?.cancel()
        scope.cancel()
        if (::mapView.isInitialized) {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    private fun setupLayers(style: Style) {
        for (i in 1..maxPreloadedPins) {
            style.addImage("pin_$i", createPinBitmap(i))
        }
        style.addImage("current_loc", createCurrentLocationBitmap())

        routeSource = GeoJsonSource(SRC_ROUTE).also { style.addSource(it) }

        style.addLayer(
            LineLayer(LYR_ROUTE_OUTLINE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.WHITE),
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineCap("round")
            )
        )
        style.addLayer(
            LineLayer(LYR_ROUTE, SRC_ROUTE).withProperties(
                PropertyFactory.lineColor(Color.parseColor("#2196F3")),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineJoin("round"),
                PropertyFactory.lineCap("round"),
                PropertyFactory.lineOpacity(0.95f)
            )
        )

        stationsSource = GeoJsonSource(SRC_STATIONS).also { style.addSource(it) }
        style.addLayer(
            SymbolLayer(LYR_STATIONS, SRC_STATIONS).withProperties(
                PropertyFactory.iconImage(Expression.get("pin")),
                PropertyFactory.iconSize(0.6f),
                PropertyFactory.iconAnchor("center"),
                PropertyFactory.iconAllowOverlap(true)
            )
        )

        currentLocSource = GeoJsonSource(SRC_CURRENT_LOC).also { style.addSource(it) }
        style.addLayer(
            SymbolLayer(LYR_CURRENT_LOC, SRC_CURRENT_LOC).withProperties(
                PropertyFactory.iconImage("current_loc"),
                PropertyFactory.iconSize(0.6f),
                PropertyFactory.iconAnchor("center"),
                PropertyFactory.iconAllowOverlap(true)
            )
        )
    }

    private fun observeViewModel() {
        observeJob = scope.launch {
            combine(
                viewModel.stations,
                viewModel.visibleRange,
                viewModel.cameraTarget,
                viewModel.currentRoutePolyline
            ) { stations, range, target, polyline ->
                MapState(stations, range, target, polyline)
            }.collect { state -> applyState(state) }
        }
    }

    private data class MapState(
        val stations: List<ChargingStation>,
        val visibleRange: IntRange?,
        val cameraTarget: LatLng?,
        val routePolylineEncoded: String?
    )

    private fun applyState(state: MapState) {
        val map = mapLibreMap ?: return

        val all = state.stations.take(maxPreloadedPins)
        val indexedStations: List<Pair<Int, ChargingStation>> =
            if (state.visibleRange == null) {
                all.mapIndexed { idx, st -> idx to st }
            } else {
                all.mapIndexedNotNull { idx, st ->
                    if (idx in state.visibleRange) idx to st else null
                }
            }

        val routePoints = state.routePolylineEncoded
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                Log.d("MapPresentation", "Nhận được polyline từ ViewModel, đang giải mã...")
                runCatching { PolylineDecoder.decode(it) }
                    .onFailure { Log.e("MapPresentation", "Giải mã polyline lỗi: ${it.message}") }
                    .getOrNull()
            }
            ?.takeIf { it.size >= 2 }

        val stationFeatures = indexedStations.map { (idx, st) ->
            Feature.fromGeometry(Point.fromLngLat(st.longitude, st.latitude)).apply {
                addStringProperty("pin", "pin_${idx + 1}")
            }
        }
        stationsSource?.setGeoJson(FeatureCollection.fromFeatures(stationFeatures))

        val locFeatures = state.cameraTarget?.let {
            listOf(Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude)))
        } ?: emptyList()
        currentLocSource?.setGeoJson(FeatureCollection.fromFeatures(locFeatures))

        val routeFeatures = if (routePoints != null) {
            Log.d("MapPresentation", "Đang vẽ đường đi với ${routePoints.size} điểm")
            val coords = routePoints.map { Point.fromLngLat(it.longitude, it.latitude) }
            listOf(Feature.fromGeometry(LineString.fromLngLats(coords)))
        } else {
            if (state.routePolylineEncoded != null) {
                Log.w("MapPresentation", "Có dữ liệu polyline nhưng không thể giải mã hoặc quá ít điểm")
            }
            emptyList()
        }
        routeSource?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

        val bounds: LatLngBounds? = when {
            routePoints != null -> boundsFromPoints(routePoints)
            indexedStations.isNotEmpty() -> {
                val pts = indexedStations.map { LatLng(it.second.latitude, it.second.longitude) }
                    .toMutableList()
                state.cameraTarget?.let { pts.add(it) }
                boundsFromPoints(pts)
            }
            else -> null
        }

        if (bounds != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 400)
        } else if (state.cameraTarget != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(state.cameraTarget, 10.0), 400)
        }
    }

    private fun boundsFromPoints(points: List<LatLng>): LatLngBounds? {
        if (points.isEmpty()) return null
        val builder = LatLngBounds.Builder()
        points.forEach { builder.include(it) }
        return builder.build()
    }

    private fun createPinBitmap(number: Int): Bitmap {
        val size = 88
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 4f

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }.also { canvas.drawCircle(cx + 2f, cy + 4f, radius, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }.also { canvas.drawCircle(cx, cy, radius, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4CAF50")
        }.also { canvas.drawCircle(cx, cy, radius - 8f, it) }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = radius * 1.0f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(number.toString(), cx, textY, textPaint)
        return bitmap
    }

    private fun createCurrentLocationBitmap(): Bitmap {
        val size = 72
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val r = size / 2f - 4f

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }.also { canvas.drawCircle(cx + 2f, cy + 4f, r, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }.also { canvas.drawCircle(cx, cy, r, it) }

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3")
        }.also { canvas.drawCircle(cx, cy, r - 8f, it) }
        return bitmap
    }

    companion object {
        private const val SRC_STATIONS = "stations-source"
        private const val LYR_STATIONS = "stations-layer"
        private const val SRC_CURRENT_LOC = "current-loc-source"
        private const val LYR_CURRENT_LOC = "current-loc-layer"
        private const val SRC_ROUTE = "route-source"
        private const val LYR_ROUTE = "route-layer"
        private const val LYR_ROUTE_OUTLINE = "route-outline-layer"
    }
}
