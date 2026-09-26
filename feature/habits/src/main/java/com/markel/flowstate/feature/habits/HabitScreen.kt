package com.markel.flowstate.feature.habits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.animateFloatingActionButton
import com.markel.flowstate.core.designsystem.ui.LocalBottomNavigationInset
import com.markel.flowstate.core.designsystem.ui.rememberFabVisibilityState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.markel.flowstate.core.domain.HabitType
import com.markel.flowstate.feature.habits.components.AddHabitSheet
import com.markel.flowstate.feature.habits.components.HabitCard
import com.markel.flowstate.feature.habits.components.HabitEmptyState
import com.markel.flowstate.feature.habits.components.HabitFabMenu
import com.markel.flowstate.feature.habits.components.NumericHabitCard
import com.markel.flowstate.feature.habits.details.components.HabitHeader
import com.markel.flowstate.feature.habits.details.components.MotivationalMessage
import java.time.LocalDate
import com.markel.flowstate.core.designsystem.R as DesignR
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HabitScreen(
    viewModel: HabitViewModel = hiltViewModel(),
    onNavigateToDetail: (habitId: Int) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bottomNavigationInset = LocalBottomNavigationInset.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
    ) {
        when (val state = uiState) {
            is HabitUiState.Loading -> Unit

            is HabitUiState.Success -> {
                val keywords = stringArrayResource(R.array.motivational_keywords)
                val rests = stringArrayResource(R.array.motivational_rests)
                val message = MotivationalMessage(
                    keyword = keywords[state.motivationalMessageIndex],
                    rest = rests[state.motivationalMessageIndex]
                )

                // ── Scroll-aware FAB visibility ────────────────────────
                // When there are no habits, there is no list to
                // scroll, so we force the FAB to stay visible.
                val listState = rememberLazyListState()
                val fabVisible by rememberFabVisibilityState(
                    lazyListState = listState,
                    forceVisible = state.habits.isEmpty()
                )

                // ── FAB menu state: type is chosen here, so the upsert sheet
                // renders a form specific to that type (no in-sheet selector).
                var fabMenuExpanded by remember { mutableStateOf(false) }
                var addSheetType by remember { mutableStateOf(HabitType.BOOLEAN) }

                Column(modifier = Modifier.fillMaxSize()) {
                    HabitHeader(
                        completedToday = state.completedToday,
                        totalHabits = state.totalHabits,
                        motivationalMessage = message
                    )
                    if (state.habits.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            HabitEmptyState()
                        }
                    } else {
                        val reorderableState = rememberReorderableLazyListState(listState) { from, to ->
                            viewModel.onReorder(from.index, to.index)
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp)
                                .testTag("benchmark_habits_list"),
                            contentPadding = PaddingValues(
                                top = 16.dp,
                                bottom = bottomNavigationInset + 100.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(
                                items = state.habitCards,
                                key = { _, card -> card.habitWithStatus.habit.id },
                                contentType = { _, card -> card.habitWithStatus.habit.habitType },
                            ) { visualIndex, card ->
                                val habitWithStatus = card.habitWithStatus
                                ReorderableItem(reorderableState, key = habitWithStatus.habit.id) { isDragging ->

                                    val scale by animateFloatAsState(
                                        targetValue = if (isDragging) 1.05f else 1.0f,
                                        label = "drag_scale"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("benchmark_habit_reorder_handle")
                                            .longPressDraggableHandle(
                                                interactionSource = remember { MutableInteractionSource() },
                                                onDragStopped = { viewModel.onReorderStopped() },
                                            )
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = if (isDragging) 0.9f else 1.0f
                                            }
                                            .zIndex(if (isDragging) 1f else 0f)
                                    ) {
                                        // A stable per-row sentinel lets the
                                        // benchmark prove that a drag really
                                        // traversed all 30 fixture rows.
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .testTag(
                                                    "benchmark_habit_reorder_handle_" +
                                                        habitWithStatus.habit.id +
                                                        "_position_" + visualIndex
                                                )
                                        )

                                        when (habitWithStatus.habit.habitType) {
                                            HabitType.BOOLEAN -> {
                                                HabitCard(
                                                    habitWithStatus = habitWithStatus,
                                                    weekEntries = card.weekEntries,
                                                    onToggleDay = { date ->
                                                        viewModel.toggleBooleanHabitOnDate(
                                                            habitWithStatus.habit.id,
                                                            date
                                                        )
                                                    },
                                                    onDelete = {
                                                        viewModel.deleteHabit(
                                                            habitWithStatus.habit
                                                        )
                                                    },
                                                    onEdit = { name, icon, colorArgb, scheduledDays ->
                                                        viewModel.editHabit(
                                                            habit = habitWithStatus.habit,
                                                            newName = name,
                                                            newIcon = icon,
                                                            newColorArgb = colorArgb,
                                                            newScheduledDays = scheduledDays
                                                        )
                                                    },
                                                    onNavigateToDetail = {
                                                        onNavigateToDetail(
                                                            habitWithStatus.habit.id
                                                        )
                                                    }
                                                )
                                            }

                                            HabitType.NUMERIC -> {
                                                NumericHabitCard(
                                                    habitWithStatus = habitWithStatus,
                                                    allEntries = card.numericEntries,
                                                    onIncrement = { date ->
                                                        viewModel.incrementNumericHabit(
                                                            habitId = habitWithStatus.habit.id,
                                                            date = date,
                                                            step = habitWithStatus.habit.step
                                                        )
                                                    },
                                                    onDecrement = { date ->
                                                        viewModel.decrementNumericHabit(
                                                            habitId = habitWithStatus.habit.id,
                                                            date = date,
                                                            step = habitWithStatus.habit.step
                                                        )
                                                    },
                                                    onSetValue = { date, value ->
                                                        if (value != null) {
                                                            viewModel.setNumericValue(
                                                                habitId = habitWithStatus.habit.id,
                                                                date = date,
                                                                value = value
                                                            )
                                                        } else {
                                                            viewModel.deleteNumericEntry(
                                                                habitId = habitWithStatus.habit.id,
                                                                date = date
                                                            )
                                                        }
                                                    },
                                                    onDelete = {
                                                        viewModel.deleteHabit(
                                                            habitWithStatus.habit
                                                        )
                                                    },
                                                    onEdit = { name, icon, colorArgb, unit, targetValue, step, scheduledDays ->
                                                        viewModel.editHabit(
                                                            habit = habitWithStatus.habit,
                                                            newName = name,
                                                            newIcon = icon,
                                                            newColorArgb = colorArgb,
                                                            newUnit = unit,
                                                            newTargetValue = targetValue,
                                                            newStep = step,
                                                            newScheduledDays = scheduledDays
                                                        )
                                                    },
                                                    onNavigateToDetail = {
                                                        onNavigateToDetail(
                                                            habitWithStatus.habit.id
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.showAddDialog) {
                    AddHabitSheet(
                        initialHabitType = addSheetType,
                        onDismiss = { viewModel.hideAddDialog() },
                        onConfirm = { name, icon, color, habitType, unit, targetValue, step, scheduledDays ->
                            viewModel.addHabit(
                                name = name,
                                iconName = icon,
                                colorArgb = color,
                                habitType = habitType,
                                unit = unit,
                                targetValue = targetValue,
                                step = step,
                                scheduledDays = scheduledDays
                            )
                        }
                    )
                }

                HabitFabMenu(
                    expanded = fabMenuExpanded,
                    onToggle = { fabMenuExpanded = !fabMenuExpanded },
                    onBooleanHabitClick = {
                        addSheetType = HabitType.BOOLEAN
                        fabMenuExpanded = false
                        viewModel.showAddDialog()
                    },
                    onNumericHabitClick = {
                        addSheetType = HabitType.NUMERIC
                        fabMenuExpanded = false
                        viewModel.showAddDialog()
                    },
                    visible = fabVisible,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = bottomNavigationInset)
                        .zIndex(1f)
                )
            }
        }
    }
}
