package com.markel.flowstate.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class FlowStateMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            pressHome()
        },
    ) {
        startActivityAndWait()
    }

    @Test
    fun primaryTabNavigationFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.FLOW_TAB)
        },
    ) {
        tapTag(BenchmarkTags.CALENDAR_TAB)
        waitForTag(BenchmarkTags.CALENDAR_LIST)
        tapTag(BenchmarkTags.HABITS_TAB)
        tapTag(BenchmarkTags.FLOW_TAB)
    }

    @Test
    fun calendarListScrollFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.CALENDAR_TAB)
            waitForTag(BenchmarkTags.CALENDAR_LIST)
        },
    ) {
        flingTag(BenchmarkTags.CALENDAR_LIST)
    }

    @Test
    fun flowListScrollFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.FLOW_TAB)
            waitForTag(BenchmarkTags.FLOW_LIST)
        },
    ) {
        flingTag(BenchmarkTags.FLOW_LIST)
    }

    @Test
    fun habitListScrollFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.HABITS_TAB)
            waitForTag(BenchmarkTags.HABITS_LIST)
        },
    ) {
        flingTag(BenchmarkTags.HABITS_LIST)
    }

    @Test
    fun booleanHabitToggleFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.HABITS_TAB)
            waitForEnabledTag(
                tag = BenchmarkTags.HABIT_BOOLEAN_TOGGLE,
                fixtureRequirement = "at least one Boolean habit scheduled for today",
            )
        },
    ) {
        tapEnabledTag(
            tag = BenchmarkTags.HABIT_BOOLEAN_TOGGLE,
            fixtureRequirement = "at least one Boolean habit scheduled for today",
        )
        tapEnabledTag(
            tag = BenchmarkTags.HABIT_BOOLEAN_TOGGLE,
            fixtureRequirement = "at least one Boolean habit scheduled for today",
        )
    }

    @Test
    fun numericHabitRapidIncrementFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.HABITS_TAB)
            waitForEnabledTag(
                tag = BenchmarkTags.HABIT_NUMERIC_INCREMENT,
                fixtureRequirement = "at least one numeric habit scheduled for today",
            )
        },
    ) {
        val incrementCenter = tapEnabledTagRapidly(
            tag = BenchmarkTags.HABIT_NUMERIC_INCREMENT,
            count = 10,
            fixtureRequirement = "at least one numeric habit scheduled for today",
        )
        tapEnabledTagRapidly(
            tag = BenchmarkTags.HABIT_NUMERIC_DECREMENT,
            count = 10,
            fixtureRequirement = "the numeric habit must allow decrementing the ten test increments",
            preferredY = incrementCenter.y,
        )
    }

    @Test
    fun habitFirstToLastReorderFrames() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            resetBenchmarkFixtures()
            tapTag(BenchmarkTags.HABITS_TAB)
            waitForTag(BenchmarkTags.HABIT_REORDER_HANDLE)
        },
    ) {
        longPressDragFirstToLast(
            tag = BenchmarkTags.HABIT_REORDER_HANDLE,
            listTag = BenchmarkTags.HABITS_LIST,
        )
        longPressDragLastToFirst(
            tag = BenchmarkTags.HABIT_REORDER_HANDLE,
            listTag = BenchmarkTags.HABITS_LIST,
        )
    }
}
