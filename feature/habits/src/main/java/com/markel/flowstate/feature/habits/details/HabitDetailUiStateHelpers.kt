package com.markel.flowstate.feature.habits.details

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.markel.flowstate.core.domain.HabitStatsCalculator
import com.markel.flowstate.feature.habits.R
import java.util.Locale

/**
 * Consistency over the visible range, measured against the habit's
 * scheduled days.
 */
fun HabitDetailUiState.completionPct(): String {
    val habit = habit ?: return "0%"
    val now = LocalDate.now()
    val start = when (viewMode) {
        CalendarViewMode.ONE_MONTH -> now.withDayOfMonth(1)
        CalendarViewMode.THREE_MONTHS -> now.withDayOfMonth(1).minusMonths(2)
        CalendarViewMode.ONE_YEAR -> now.withDayOfYear(1)
    }
    val pct = HabitStatsCalculator.consistencyPercent(
        completedDates = allEntries.map { LocalDate.ofEpochDay(it) },
        scheduledDays = habit.scheduledDays,
        start = start,
        end = now
    ) ?: return "0%"
    return "$pct%"
}

@Composable
fun HabitDetailUiState.pctLabel() = when (viewMode) {
    CalendarViewMode.ONE_MONTH -> stringResource(R.string.habit_detail_pct_month)
    CalendarViewMode.THREE_MONTHS -> stringResource(R.string.habit_detail_pct_3months)
    CalendarViewMode.ONE_YEAR -> stringResource(R.string.habit_detail_pct_year)
}

@Composable
fun CalendarViewMode.label() = when (this) {
    CalendarViewMode.ONE_MONTH -> stringResource(R.string.view_range_one_month)
    CalendarViewMode.THREE_MONTHS -> stringResource(R.string.view_range_three_months)
    CalendarViewMode.ONE_YEAR -> stringResource(R.string.view_range_one_year)
}

fun HabitDetailUiState.navigationLabel(locale: Locale): String {
    val month = LocalDate.of(displayYear, displayMonth + 1, 1)
    return when (viewMode) {
        CalendarViewMode.ONE_MONTH -> month.format(
            localizedFormatter(locale, "yMMMM")
        )
        CalendarViewMode.THREE_MONTHS -> {
            val startMonth = month.minusMonths(2)
            val formatter = localizedFormatter(locale, "yMMM")
            "${startMonth.format(formatter)} – ${month.format(formatter)}"
        }
        CalendarViewMode.ONE_YEAR -> month.format(localizedFormatter(locale, "y"))
    }
}

private fun localizedFormatter(locale: Locale, skeleton: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(
        DateFormat.getBestDateTimePattern(locale, skeleton),
        locale
    )
