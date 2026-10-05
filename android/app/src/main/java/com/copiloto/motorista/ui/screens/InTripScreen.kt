package com.copiloto.motorista.ui.screens

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.copiloto.motorista.data.repository.AlertOrigin
import com.copiloto.motorista.ui.MainViewModel
import com.copiloto.motorista.ui.UiFormat
import com.copiloto.motorista.ui.demo.MuzambinhoRoute
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * In-trip demo screen (Module G demonstration): after accepting a simulated ride,
 * shows an OpenStreetMap map of Muzambinho with an animated route, the trip header,
 * and the panic demonstration — voice and physical-button triggers stay live, plus
 * on-screen "simulate" fallbacks. A banner confirms whenever any alert is fired.
 */
@Composable
fun InTripScreen(viewModel: MainViewModel, onExit: () -> Unit) {
    val trip by viewModel.demoTrip.collectAsStateWithLifecycle()

    // If there is no trip (e.g. process restart), leave the screen.
    LaunchedEffect(trip == null) { if (trip == null) onExit() }
    val currentTrip = trip ?: return

    // Keep the screen awake during the demonstration.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var banner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        viewModel.alertEvents.collect { origin -> banner = originLabel(origin) }
    }
    LaunchedEffect(banner) {
        if (banner != null) { delay(6000); banner = null }
    }

    Box(Modifier.fillMaxSize()) {
        TripMap(Modifier.fillMaxSize())

        // Top: back + trip header.
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onExit,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(50),
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Encerrar demonstração")
                }
            }
            Spacer(Modifier.size(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Corrida em andamento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        "${currentTrip.pickup}  →  ${currentTrip.dropoff}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${currentTrip.sourceLabel} · ${UiFormat.money(currentTrip.grossPrice)} · " +
                            "${UiFormat.oneDecimal(currentTrip.distanceKm)} km · ${currentTrip.timeMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Bottom: panic demonstration controls.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            banner?.let { text ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)),
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White)
                        Text(text, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Acionamento de pânico", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Dispare pela frase de voz ou pelo botão físico. Os botões abaixo " +
                            "são uma reserva para a apresentação.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.firePanic(AlertOrigin.VOZ) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Simular voz")
                        }
                        OutlinedButton(
                            onClick = { viewModel.firePanic(AlertOrigin.BOTAO_PANICO) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Simular botão")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TripMap(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember {
        MapView(context).apply {
            // Clean, low-clutter basemap: Esri World Light Gray Base — a light grey
            // canvas with essentially no labels/POIs. Keyless. Note the z/y/x URL order.
            setTileSource(
                object : OnlineTileSourceBase(
                    "EsriLightGray", 0, 16, 256, "",
                    arrayOf(
                        "https://services.arcgisonline.com/arcgis/rest/services/Canvas/World_Light_Gray_Base/MapServer/tile/",
                    ),
                    "Esri, © OpenStreetMap contributors",
                ) {
                    override fun getTileURLString(pMapTileIndex: Long): String =
                        baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                            MapTileIndex.getY(pMapTileIndex) + "/" +
                            MapTileIndex.getX(pMapTileIndex)
                },
            )
            setMultiTouchControls(true)
            setUseDataConnection(true)
        }
    }

    // osmdroid MapView follows the host lifecycle.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(modifier = modifier, factory = { mapView })

    val density = context.resources.displayMetrics.density

    // Build overlays once, then animate the car along the route.
    LaunchedEffect(Unit) {
        val points = MuzambinhoRoute.POINTS.map { GeoPoint(it.first, it.second) }

        val route = Polyline(mapView).apply {
            setPoints(points)
            outlinePaint.color = AndroidColor.parseColor("#1E7E51")
            outlinePaint.strokeWidth = 7f * density
            outlinePaint.strokeCap = Paint.Cap.ROUND
            outlinePaint.strokeJoin = Paint.Join.ROUND
            outlinePaint.isAntiAlias = true
        }
        mapView.overlays.add(route)
        mapView.overlays.add(CopyrightOverlay(context)) // small, required attribution

        fun dotMarker(p: GeoPoint, fill: Int, sizeDp: Int) = Marker(mapView).apply {
            position = p
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = circleDot(fill, sizeDp, density)
            setInfoWindow(null) // no label bubbles — keep it clean
            isDraggable = false
        }
        // Origin = soft dark dot; destination = brand green; car = green "puck".
        mapView.overlays.add(dotMarker(points.first(), AndroidColor.parseColor("#334155"), 14))
        mapView.overlays.add(dotMarker(points.last(), AndroidColor.parseColor("#1E7E51"), 16))
        val car = dotMarker(points.first(), AndroidColor.parseColor("#16A34A"), 20)
        mapView.overlays.add(car)

        // Frame the route tightly so distant labels stay off-screen.
        val bbox = BoundingBox.fromGeoPointsSafe(points)
        mapView.post { mapView.zoomToBoundingBox(bbox.increaseByScale(1.35f), false, 90) }

        val path = densify(points, stepsPerSegment = 18)
        while (isActive) {
            for (p in path) {
                car.position = p
                mapView.invalidate()
                delay(140)
            }
            delay(1500) // pause at destination, then loop the demo
            car.position = points.first()
        }
    }
}

/** A small filled circle with a white ring, for clean map markers (origin/car/etc.). */
private fun circleDot(fill: Int, sizeDp: Int, density: Float): Drawable {
    val size = (sizeDp * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke((2.5f * density).toInt(), AndroidColor.WHITE)
        setSize(size, size)
        setBounds(0, 0, size, size)
    }
}

/** Linearly interpolates extra points between each pair, for smooth movement. */
private fun densify(points: List<GeoPoint>, stepsPerSegment: Int): List<GeoPoint> {
    if (points.size < 2) return points
    val out = ArrayList<GeoPoint>(points.size * stepsPerSegment)
    for (i in 0 until points.size - 1) {
        val a = points[i]
        val b = points[i + 1]
        for (s in 0 until stepsPerSegment) {
            val t = s.toDouble() / stepsPerSegment
            out.add(
                GeoPoint(
                    a.latitude + (b.latitude - a.latitude) * t,
                    a.longitude + (b.longitude - a.longitude) * t,
                ),
            )
        }
    }
    out.add(points.last())
    return out
}

private fun originLabel(origin: String): String = when (origin) {
    AlertOrigin.VOZ -> "🔴 Alerta enviado (voz)"
    AlertOrigin.BOTAO_PANICO -> "🔴 Alerta enviado (botão físico)"
    AlertOrigin.TESTE -> "🔴 Alerta de teste enviado"
    else -> "🔴 Alerta enviado à Central"
}
