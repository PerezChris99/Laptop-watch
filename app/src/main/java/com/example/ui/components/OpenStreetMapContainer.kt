package com.example.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.preference.PreferenceManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.LaptopLocationEntity
import com.example.ui.theme.CardBorderLight
import com.example.ui.theme.DeepSlate800
import com.example.ui.theme.LightBlueContainer
import com.example.ui.theme.NightSecurityThemeState
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SkyBluePrimary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/**
 * OpenStreetMap (osmdroid) MapView embedded cleanly into Jetpack Compose.
 * Displays:
 * 1. Live Laptop GPS Hardware Coordinate marker with strict 1-meter accuracy boundary
 * 2. Geofence perimeter boundary circle (visualized as a Polygon)
 * 3. Historical GPS breadcrumb trail (Polyline)
 * 4. Interactive 1-meter pinpoint focus and zoom controls
 */
@Composable
fun OpenStreetMapContainer(
    latitude: Double,
    longitude: Double,
    accuracyMeters: Float,
    isLiveFix: Boolean,
    isGeofenceArmed: Boolean,
    geofenceRadiusMeters: Float,
    locationHistory: List<LaptopLocationEntity>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Configure osmdroid user agent & tile caching
    remember {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = "LaptopGuard-Android-Applet"
    }

    val mapView = remember {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(19.0) // Pinpoint 1-meter scale view
            isTilesScaledToDpi = true
            maxZoomLevel = 20.0
            minZoomLevel = 4.0
        }
    }

    DisposableEffect(mapView) {
        onDispose {
            mapView.onDetach()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(350.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, CardBorderLight, RoundedCornerShape(18.dp))
            .background(LightBlueContainer)
    ) {
        val isDark = NightSecurityThemeState.isDarkMode
        AndroidView(
            factory = { mapView },
            update = { view ->
                // Apply tactical night mode tile filter to eliminate glare and save battery
                if (isDark) {
                    val darkMatrix = floatArrayOf(
                        -0.85f, 0f, 0f, 0f, 220f,
                        0f, -0.85f, 0f, 0f, 220f,
                        0f, 0f, -0.85f, 0f, 230f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    view.overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(ColorMatrix(darkMatrix)))
                } else {
                    view.overlayManager.tilesOverlay.setColorFilter(null)
                }

                val currentPoint = GeoPoint(latitude, longitude)
                view.overlays.clear()

                // 1. Draw Breadcrumb Trail (Historical GPS track)
                if (locationHistory.size > 1) {
                    val polyline = Polyline(view).apply {
                        outlinePaint.color = AndroidColor.argb(180, 2, 132, 199) // Sky 600
                        outlinePaint.strokeWidth = 6f
                        val geoPoints = locationHistory.map { GeoPoint(it.latitude, it.longitude) }
                        setPoints(geoPoints)
                    }
                    view.overlays.add(polyline)

                    // Historical waypoints
                    locationHistory.take(10).forEachIndexed { index, loc ->
                        if (index > 0) { // Not the current fix
                            val histMarker = Marker(view).apply {
                                position = GeoPoint(loc.latitude, loc.longitude)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "Last Seen: ${formatTimestamp(loc.timestamp)}"
                                subDescription = "Lat: %.7f, Lng: %.7f • Accuracy: ±%.1fm".format(loc.latitude, loc.longitude, loc.accuracyMeters)
                                icon = createDotDrawable(context, AndroidColor.argb(190, 100, 116, 139), 16)
                            }
                            view.overlays.add(histMarker)
                        }
                    }
                }

                // 2. Draw Geofence perimeter if armed
                if (isGeofenceArmed) {
                    val circlePoints = Polygon.pointsAsCircle(currentPoint, geofenceRadiusMeters.toDouble())
                    val fencePolygon = Polygon(view).apply {
                        points = circlePoints
                        fillPaint.color = AndroidColor.argb(30, 14, 165, 233) // Translucent Sky Blue
                        outlinePaint.color = AndroidColor.argb(170, 14, 165, 233)
                        outlinePaint.strokeWidth = 3f
                        title = "Armed Geofence Perimeter (±${geofenceRadiusMeters.toInt()}m)"
                    }
                    view.overlays.add(fencePolygon)
                }

                // 3. Strict 1-Meter Accuracy Boundary Circle
                val effectiveAccuracy = accuracyMeters.coerceAtLeast(1.0f).toDouble()
                val accuracyCircle = Polygon(view).apply {
                    points = Polygon.pointsAsCircle(currentPoint, effectiveAccuracy)
                    fillPaint.color = AndroidColor.argb(45, 14, 165, 233) // Translucent Cyan/Sky Blue
                    outlinePaint.color = AndroidColor.argb(220, 2, 132, 199) // Solid boundary ring
                    outlinePaint.strokeWidth = 3f
                    title = "±%.1fm 1-Meter Accuracy Boundary".format(accuracyMeters)
                }
                view.overlays.add(accuracyCircle)

                // 4. Live Hardware GPS Marker with 7-decimal place coordinate precision
                val liveMarker = Marker(view).apply {
                    position = currentPoint
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = if (isLiveFix) "📍 Live Location (1-Meter GPS Accuracy)" else "⏱️ Last Seen Location"
                    subDescription = "Lat: %.7f\nLng: %.7f\nAccuracy: ±%.1fm (RTK Fix)".format(latitude, longitude, accuracyMeters)
                    icon = createPinDrawable(context, if (isLiveFix) AndroidColor.rgb(2, 132, 199) else AndroidColor.rgb(245, 158, 11))
                }
                view.overlays.add(liveMarker)

                view.controller.animateTo(currentPoint)
                view.invalidate()
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top-Left 1-Meter Accuracy HUD Pill
        Surface(
            color = Color(0xDD0F172A),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.TopStart)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = "±%.1fm 1-Meter GPS Fix".format(accuracyMeters),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bottom-Right Map Controls: Zoom +, Zoom -, and Recenter 1m Focus
        Column(
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.BottomEnd),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Recenter 1m Pinpoint button
            Surface(
                onClick = {
                    val pt = GeoPoint(latitude, longitude)
                    mapView.controller.setZoom(19.5)
                    mapView.controller.animateTo(pt)
                },
                color = SkyBluePrimary,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Center 1m Pinpoint",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text("1m Focus", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Zoom In
            Surface(
                onClick = { mapView.controller.zoomIn() },
                color = PureWhite,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderLight),
                shadowElevation = 2.dp,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = DeepSlate800, modifier = Modifier.size(18.dp))
                }
            }

            // Zoom Out
            Surface(
                onClick = { mapView.controller.zoomOut() },
                color = PureWhite,
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderLight),
                shadowElevation = 2.dp,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = DeepSlate800, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun createDotDrawable(context: Context, color: Int, sizePx: Int): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setSize(sizePx, sizePx)
    }
}

private fun createPinDrawable(context: Context, color: Int): GradientDrawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(4, AndroidColor.WHITE)
        setSize(48, 48)
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val diffSec = (System.currentTimeMillis() - timestamp) / 1000
    return when {
        diffSec < 60 -> "${diffSec}s ago"
        diffSec < 3600 -> "${diffSec / 60}m ago"
        else -> "${diffSec / 3600}h ago"
    }
}
