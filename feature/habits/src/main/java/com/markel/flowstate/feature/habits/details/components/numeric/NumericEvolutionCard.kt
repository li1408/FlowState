package com.markel.flowstate.feature.habits.details.components.numeric

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import com.markel.flowstate.feature.habits.R
import com.markel.flowstate.feature.habits.util.formatFloat

@Composable
fun NumericEvolutionCard(
    dailyValues: List<Pair<LocalDate, Float>>,
    targetValue: Float?,
    habitColor: Color,
    unit: String?,
    modifier: Modifier = Modifier
) {
    val locale = LocalLocale.current.platformLocale
    val shortDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, "MMMd"),
            locale
        )
    }
    val maxValue = dailyValues.maxOfOrNull { it.second } ?: targetValue ?: 10f
    val scaleMax = maxOf(maxValue, targetValue ?: 0f) * 1.2f

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedDay = selectedIndex?.let { dailyValues.getOrNull(it) }

    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "line_animation"
    )

    val stats = remember(dailyValues) {
        val values = dailyValues.map { it.second }
        val avg = if (values.isEmpty()) 0f else values.average().toFloat()

        val todayVal = values.lastOrNull() ?: 0f
        val yesterdayVal = if (values.size >= 2) values[values.size - 2] else 0f

        val diffPercent = if (yesterdayVal != 0f) {
            ((todayVal - yesterdayVal) / yesterdayVal * 100).toInt()
        } else 0

        Pair(avg, diffPercent)
    }
    val (average, dailyChange) = stats

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.habit_detail_average).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 1.sp
                )

                val formattedAvg = formatFloat(average)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formattedAvg,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = " ${unit ?: ""}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                // Improvement vs yesterday
                if (dailyValues.size >= 2) {
                    val isPositive = dailyChange >= 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isPositive) "↑ $dailyChange%" else "↓ ${abs(dailyChange)}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.habit_detail_vs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val displayValue = selectedDay?.second ?: dailyValues.lastOrNull()?.second ?: 0f
                val displayDate = selectedDay?.first ?: dailyValues.lastOrNull()?.first
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val formattedValue = formatFloat(displayValue)
                    Text(
                        text = formattedValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = habitColor
                    )
                    if (unit != null) {
                        Text(
                            text = " $unit",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = habitColor,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Text(
                    text = displayDate?.let {
                        if (selectedDay != null) {
                            it.format(shortDateFormatter)
                        } else stringResource(R.string.habit_detail_today)
                    } ?: stringResource(R.string.habit_detail_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (targetValue != null) {
                    val formattedTarget = remember(targetValue) {
                        formatFloat(targetValue)
                    }
                    Text(
                        text = stringResource(R.string.habit_target_preview, formattedTarget, unit?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Interactive graph
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            var tapPositions by remember { mutableStateOf<List<Pair<Offset, Int>>>(emptyList()) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(dailyValues) {
                        detectTapGestures { offset ->
                            val closest = tapPositions.minByOrNull { (pos, _) ->
                                val dx = pos.x - offset.x
                                val dy = pos.y - offset.y
                                dx * dx + dy * dy
                            }

                            if (closest != null) {
                                val distance = abs(closest.first.x - offset.x)
                                if (distance < 50f) {
                                    selectedIndex = closest.second
                                } else {
                                    selectedIndex = null
                                }
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val paddingX = 15f
                val paddingBottom = 20f

                val graphWidth = width - paddingX * 2
                val graphHeight = height - paddingBottom * 2

                if (dailyValues.size < 2) return@Canvas
                val baseLineY = height - paddingBottom

                // Goal (if exists)
                targetValue?.let { target ->
                    val targetY = baseLineY - (target / scaleMax * graphHeight)
                    drawLine(
                        color = habitColor.copy(alpha = 0.25f),
                        start = Offset(paddingX, targetY),
                        end = Offset(width - paddingX, targetY),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                    )
                }

                // Crear puntos
                val points = dailyValues.mapIndexed { index, (_, value) ->
                    val x = paddingX + (index.toFloat() / (dailyValues.size - 1)) * graphWidth
                    val y = baseLineY - (value / scaleMax * graphHeight)
                    Offset(x, y)
                }

                tapPositions = points.mapIndexed { index, offset -> offset to index }

                val smoothPath = Path()
                val gradientPath = Path()

                if (points.isNotEmpty()) {
                    smoothPath.moveTo(points[0].x, points[0].y)
                    gradientPath.moveTo(points[0].x, baseLineY)
                    gradientPath.lineTo(points[0].x, points[0].y)

                    for (i in 0 until points.size - 1) {
                        val p0 = points.getOrNull(i - 1) ?: points[i]
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        val p3 = points.getOrNull(i + 2) ?: points[i + 1]

                        // Catmull-Rom to Bezier
                        val cp1x = p1.x + (p2.x - p0.x) / 6f
                        var cp1y = p1.y + (p2.y - p0.y) / 6f
                        val cp2x = p2.x - (p3.x - p1.x) / 6f
                        var cp2y = p2.y - (p3.y - p1.y) / 6f

                        cp1y = cp1y.coerceAtMost(baseLineY)
                        cp2y = cp2y.coerceAtMost(baseLineY)

                        smoothPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        gradientPath.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                    }

                    gradientPath.lineTo(points.last().x, baseLineY)
                    gradientPath.close()
                }

                val fillGradient = Brush.verticalGradient(
                    colors = listOf(
                        habitColor.copy(alpha = 0.35f * animationProgress),
                        Color.Transparent
                    ),
                    startY = points.minOfOrNull { it.y } ?: 0f,
                    endY = baseLineY
                )

                drawPath(
                    path = gradientPath,
                    brush = fillGradient
                )

                drawPath(
                    path = smoothPath,
                    color = habitColor,
                    style = Stroke(
                        width = 5.0f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    alpha = animationProgress
                )

                points.forEachIndexed { index, point ->
                    val value = dailyValues[index].second
                    if (value > 0) {
                        val isSelected = selectedIndex == index
                        val radius = if (isSelected) 14f else 10f

                        fun createPoint(centerX: Float, centerY: Float, r: Float): Path {
                            return Path().apply {
                                moveTo(centerX, centerY - r)
                                quadraticTo(centerX + r, centerY - r, centerX + r, centerY)
                                quadraticTo(centerX + r, centerY + r, centerX, centerY + r)
                                quadraticTo(centerX - r, centerY + r, centerX - r, centerY)
                                quadraticTo(centerX - r, centerY - r, centerX, centerY - r)
                                close()
                            }
                        }

                        val outerClover = createPoint(point.x, point.y, radius)
                        drawPath(
                            path = outerClover,
                            color = habitColor.copy(alpha = 0.4f),
                            alpha = animationProgress
                        )

                        val innerRadius = radius * 0.65f
                        val innerClover = createPoint(point.x, point.y, innerRadius)

                        drawPath(
                            path = innerClover,
                            color = if (isSelected) habitColor else Color.White,
                            alpha = animationProgress
                        )
                    }
                }
            }
        }
        // Time labels
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            dailyValues.firstOrNull()?.first?.let { date ->
                Text(
                    text = date.format(shortDateFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = stringResource(R.string.habit_detail_today),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
