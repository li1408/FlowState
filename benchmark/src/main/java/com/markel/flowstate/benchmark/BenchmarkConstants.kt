package com.markel.flowstate.benchmark

internal const val TARGET_PACKAGE = "com.markel.flowstate.benchmark"
internal const val RESET_FIXTURES_EXTRA =
    "com.markel.flowstate.extra.RESET_BENCHMARK_FIXTURES"

internal object BenchmarkTags {
    const val FLOW_TAB = "benchmark_bottom_tab_flow"
    const val CALENDAR_TAB = "benchmark_bottom_tab_calendar"
    const val HABITS_TAB = "benchmark_bottom_tab_habits"

    const val FLOW_LIST = "benchmark_flow_list"
    const val CALENDAR_LIST = "benchmark_calendar_list"
    const val HABITS_LIST = "benchmark_habits_list"
    const val HABIT_BOOLEAN_TOGGLE = "benchmark_habit_boolean_toggle"
    const val HABIT_NUMERIC_INCREMENT = "benchmark_habit_numeric_increment"
    const val HABIT_NUMERIC_DECREMENT = "benchmark_habit_numeric_decrement"
    const val HABIT_REORDER_HANDLE = "benchmark_habit_reorder_handle"
    const val HABIT_REORDER_FIRST_AT_START =
        "benchmark_habit_reorder_handle_10001_position_0"
    const val HABIT_REORDER_FIRST_AT_END =
        "benchmark_habit_reorder_handle_10001_position_29"
}
