package com.markel.flowstate.feature.calendar.components.taskslist

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.domain.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import com.markel.flowstate.core.designsystem.ui.LocalBottomNavigationInset
import java.time.temporal.WeekFields

private const val DAYS_AHEAD = 180
private val DAY_COLUMN_WIDTH = 56.dp

private data class WeekBlock(
    val weekKey: String,
    val label: String,
    val days: List<DayBlock>
)

private data class DayBlock(
    val date: LocalDate,
    val tasks: List<Task>,
    val isFirstOfMonth: Boolean = false
)

@Composable
fun DailyTasksSection(
    startDate: LocalDate,
    tasksByDate: Map<LocalDate, List<Task>>,
    listState: LazyListState,
    onTaskToggle: (Task) -> Unit
) {
    val bottomNavigationInset = LocalBottomNavigationInset.current
    val locale = LocalLocale.current.platformLocale
    val firstDayOfWeek = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val weeks = remember(startDate, tasksByDate, locale, firstDayOfWeek) {
        buildWeekBlocks(startDate, tasksByDate, locale, firstDayOfWeek)
    }

    LaunchedEffect(startDate, firstDayOfWeek) {
        val targetKey = weekKeyFor(startDate, firstDayOfWeek)
        val targetIndex = weeks.indexOfFirst { it.weekKey == targetKey }.takeIf { it >= 0 } ?: 0
        listState.animateScrollToItem(targetIndex)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.testTag("benchmark_calendar_list"),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 60.dp + bottomNavigationInset,
        )
    ) {
        weeks.forEach { week ->
            item(key = "week_${week.weekKey}") {
                WeekSection(week = week, onTaskToggle = onTaskToggle)
            }
        }
    }
}

// ── Week section ──────────────────────────────────────────────────────────────

@Composable
private fun WeekSection(
    week: WeekBlock,
    onTaskToggle: (Task) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column spacer — keeps week label aligned with task cards
            Spacer(modifier = Modifier.width(DAY_COLUMN_WIDTH))
            // Week label sits directly above its day rows, in the same column as the cards
            Text(
                text = week.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        week.days.forEach { day ->
            if (day.isFirstOfMonth) {
                MonthBanner(day.date)
            }

            if (day.tasks.isNotEmpty()) {
                DayRow(day = day, onTaskToggle = onTaskToggle)
            }
        }
        Spacer(modifier = Modifier.height(4.5.dp))
    }
}

// ── Day row ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DayRow(
    day: DayBlock,
    onTaskToggle: (Task) -> Unit
) {
    val isToday = day.date == LocalDate.now()

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // Left column: abbrev + number
        Column(
            modifier = Modifier.width(DAY_COLUMN_WIDTH),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = day.date.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, LocalLocale.current.platformLocale)
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = if (isToday) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = if (isToday) MaterialTheme.colorScheme.primary
                                else androidx.compose.ui.graphics.Color.Transparent,
                        shape = MaterialShapes.Pill.toShape()
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (isToday) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Right column: task cards
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            day.tasks.forEach { task ->
                InteractiveTaskRow(
                    task = task,
                    onToggle = { onTaskToggle(task) }
                )
            }
        }
    }
}

// ── Data building ─────────────────────────────────────────────────────────────

private fun buildWeekBlocks(
    startDate: LocalDate,
    tasksByDate: Map<LocalDate, List<Task>>,
    locale: Locale,
    firstDayOfWeek: DayOfWeek,
): List<WeekBlock> {
    val endDate = startDate.plusDays(DAYS_AHEAD.toLong())
    val blocks = mutableListOf<WeekBlock>()
    var weekStart = startDate.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))

    while (weekStart <= endDate) {
        val days = (0L..6L).map { offset ->
            val dayDate = weekStart.plusDays(offset)
            val tasks = tasksByDate[dayDate] ?: emptyList()

            DayBlock(
                date = dayDate,
                tasks = tasks,
                isFirstOfMonth = dayDate.dayOfMonth == 1
            )
        }.filter { it.date in startDate..endDate }

        if (days.isNotEmpty()) {
            blocks.add(
                WeekBlock(
                    weekKey = weekKeyFor(weekStart, firstDayOfWeek),
                    label = buildWeekLabel(weekStart, weekStart.plusDays(6), locale),
                    days = days
                )
            )
        }
        weekStart = weekStart.plusWeeks(1)
    }
    return blocks
}

private fun buildWeekLabel(weekStart: LocalDate, weekEnd: LocalDate, locale: Locale): String {
    val formatter = DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, "MMMd"),
        locale
    )
    return "${weekStart.format(formatter)} – ${weekEnd.format(formatter)}"
}

private fun weekKeyFor(date: LocalDate, firstDayOfWeek: DayOfWeek): String =
    date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).toString()
