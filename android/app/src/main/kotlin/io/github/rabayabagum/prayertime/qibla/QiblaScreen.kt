package io.github.rabayabagum.prayertime.qibla

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.rabayabagum.prayertime.PrayerTimeApplication
import io.github.rabayabagum.prayertime.core.PrayerCalculator
import io.github.rabayabagum.prayertime.data.AppState
import io.github.rabayabagum.prayertime.ui.LabeledRow
import java.time.Instant
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Qibla compass, matching the iOS QiblaView: the dial turns with the phone so
 * north stays north, the Kaaba marker sits at the Qibla bearing, and a haptic plus
 * a spoken "Facing the Qibla" mark alignment. Works without location permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QiblaScreen(state: AppState) {
    val context = LocalContext.current
    val app = context.applicationContext as PrayerTimeApplication
    val place = state.location.place
    val qibla = remember(place) { PrayerCalculator.qiblaBearing(place.latitude, place.longitude) }
    val model = app.magneticModel
    val declination = remember(place) { model.declination(place.latitude, place.longitude, Instant.now()) }

    // The activity context, so the sensor can read the display's rotation.
    val sensor = remember { HeadingSensor(context) }
    LifecycleResumeEffect(sensor) {
        sensor.start()
        onPauseOrDispose { sensor.stop() }
    }

    val trueHeading = sensor.magneticHeading?.let { (it + declination + 360) % 360 }
    var aligned by remember { mutableStateOf(false) }
    val guidance = trueHeading?.let { QiblaGuidance.of(qibla, it, aligned) }

    // Unwrapped heading for the dial, so 359° → 1° turns 2°, not back round 358°.
    var unwrapped by remember { mutableStateOf<Double?>(null) }
    LaunchedEffect(trueHeading) {
        trueHeading?.let { value ->
            val current = unwrapped ?: value
            unwrapped = current + QiblaGuidance.normalized(value - current)
        }
    }
    val dialHeading by animateFloatAsState((unwrapped ?: 0.0).toFloat(), tween(200), label = "dial")

    val view = LocalView.current
    LaunchedEffect(guidance?.isAligned) {
        val now = guidance?.isAligned ?: return@LaunchedEffect
        if (now != aligned) {
            aligned = now
            if (now) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
                )
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Qibla") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (sensor.isAvailable) {
                CompassDial(
                    heading = dialHeading,
                    qibla = qibla.toFloat(),
                    aligned = guidance?.isAligned == true,
                    modifier = Modifier
                        .alpha(if (guidance == null) 0.4f else 1f)
                        .clearAndSetSemantics {
                            contentDescription = "Qibla compass"
                            stateDescription = guidance?.instruction ?: "Calibrating"
                        },
                )
                Text(
                    guidance?.instruction ?: "Calibrating…",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (guidance?.isAligned == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    // Polite live region: TalkBack announces "Facing the Qibla" as it happens.
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (sensor.quality.needsCalibration) {
                    CalibrationWarning()
                } else {
                    Text(
                        "Hold your phone flat, top edge pointing ahead.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                CompassDial(
                    heading = 0f, qibla = qibla.toFloat(), aligned = false,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "Qibla direction, ${qibla.roundToInt()} degrees clockwise from true north"
                    },
                )
                Text(
                    "This device has no compass. The dial shows true north at the top; face ${qibla.roundToInt()}° clockwise from north.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
            ) {
                LabeledRow("Location", "${place.name}, ${state.location.subtitle}")
                HorizontalDivider()
                LabeledRow("Qibla", "%.1f° from true north".format(qibla))
                HorizontalDivider()
                LabeledRow("Magnetic declination", declination.declinationText)
                if (sensor.isAvailable) {
                    HorizontalDivider()
                    LabeledRow("Compass accuracy", sensor.quality.summary)
                }
                if (MagneticModel.decimalYear(Instant.now()) !in model.validYears) {
                    HorizontalDivider()
                    LabeledRow("Magnetic model", "${model.name} has expired; update the app")
                }
            }
        }
    }
}

@Composable
private fun CalibrationWarning() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0x33FFC107), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Icon(Icons.Default.Warning, contentDescription = null)
        Text(
            "Compass accuracy is low. Move away from metal, magnets and electronics, then wave your phone in a figure-eight.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** The dial turns with the phone so north stays north. Never mirrored for RTL. */
@Composable
private fun CompassDial(heading: Float, qibla: Float, aligned: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val face = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary
    val ring = if (aligned) accent else MaterialTheme.colorScheme.outlineVariant
    val labelStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(modifier.widthIn(max = 340.dp).fillMaxWidth().aspectRatio(1f).padding(top = 16.dp)) {
            val radius = size.minDimension / 2
            val c = center
            drawCircle(face, radius)
            drawCircle(ring, radius, style = Stroke(width = if (aligned) 4.dp.toPx() else 1.dp.toPx()))

            rotate(-heading, c) {
                for (degree in 0 until 360 step 5) {
                    val major = degree % 30 == 0
                    val outer = radius - 8.dp.toPx()
                    val inner = outer - (if (major) 14 else 7).dp.toPx()
                    drawLine(muted, polar(c, inner, degree.toFloat()), polar(c, outer, degree.toFloat()), if (major) 2.dp.toPx() else 1.dp.toPx())
                }
                listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f).forEach { (label, angle) ->
                    val at = polar(c, radius - 40.dp.toPx(), angle)
                    val layout = measurer.measure(label, labelStyle.copy(color = if (label == "N") Color.Red else ink))
                    // Counter-rotate so the letters stay upright as the dial turns.
                    rotate(heading, at) {
                        drawText(layout, topLeft = at - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                }
                kaaba(polar(c, radius - 40.dp.toPx(), qibla), heading)
            }

            // Needle toward the Qibla.
            val needle = polar(c, radius - 70.dp.toPx(), qibla - heading)
            drawLine(if (aligned) accent else ink.copy(alpha = 0.7f), c, needle, (if (aligned) 6 else 4).dp.toPx(), StrokeCap.Round)
            drawCircle(ink, 6.dp.toPx(), c)

            // Where the phone points.
            val tip = Offset(c.x, c.y - radius - 2.dp.toPx())
            val w = 9.dp.toPx()
            val h = 14.dp.toPx()
            drawPath(
                Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x - w, tip.y - h)
                    lineTo(tip.x + w, tip.y - h)
                    close()
                },
                if (aligned) accent else ink,
            )
        }
    }
}

/** A small black Kaaba with its gold band, kept upright while the dial turns. */
private fun DrawScope.kaaba(at: Offset, heading: Float) {
    val side = 22.dp.toPx()
    rotate(heading, at) {
        val topLeft = at - Offset(side / 2, side / 2)
        drawRoundRect(Color.Black, topLeft, Size(side, side), CornerRadius(3.dp.toPx()))
        drawRect(Color(0xFFF2C46E), topLeft + Offset(0f, 5.dp.toPx()), Size(side, 3.dp.toPx()))
    }
}

/** A point at [distance] from [center], [degrees] clockwise from straight up. */
private fun polar(center: Offset, distance: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble() - 90)
    return Offset(center.x + distance * cos(radians).toFloat(), center.y + distance * sin(radians).toFloat())
}
