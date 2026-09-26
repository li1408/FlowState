package com.markel.flowstate.feature.flow.completion

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.markel.flowstate.core.data.CompletionPhotoDraft
import com.markel.flowstate.feature.tasks.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCompletionSheet(
    state: TaskCompletionUiState.Draft,
    onNoteChange: (String) -> Unit,
    onTakePhoto: () -> Unit,
    onChoosePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onSubmit: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { !state.isSubmitting },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("completion_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.check_24px),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.completion_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.completion_sheet_subtitle, state.task.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    enabled = !state.isSubmitting,
                ) {
                    Icon(
                        imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.completion_close),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text(stringResource(R.string.completion_note_label)) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("completion_note"),
                shape = RoundedCornerShape(18.dp),
            )

            Spacer(Modifier.height(16.dp))
            if (state.photo != null) {
                CompletionPhotoPreview(state.photo)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onChoosePhoto,
                        enabled = !state.isSubmitting && !state.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.completion_replace_photo))
                    }
                    TextButton(
                        onClick = onRemovePhoto,
                        enabled = !state.isSubmitting && !state.isImporting,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.completion_remove_photo))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = onTakePhoto,
                        enabled = !state.isSubmitting && !state.isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("completion_camera"),
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.photo_camera_24px),
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.completion_take_photo),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    FilledTonalButton(
                        onClick = onChoosePhoto,
                        enabled = !state.isSubmitting && !state.isImporting,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("completion_gallery"),
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(R.drawable.photo_library_24px),
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.completion_choose_photo),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            if (state.isImporting) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(R.string.completion_processing_photo),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            val errorText = when (state.error) {
                TaskCompletionError.PhotoImportFailed -> R.string.completion_error_photo
                TaskCompletionError.SaveFailed -> R.string.completion_error_save
                TaskCompletionError.TaskUnavailable -> R.string.completion_error_missing_task
                TaskCompletionError.AlreadyCompleted -> R.string.completion_error_already_completed
                null -> null
            }
            if (errorText != null) {
                Text(
                    text = stringResource(errorText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }

            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onSubmit,
                enabled = !state.isSubmitting && !state.isImporting &&
                    (state.note.isNotBlank() || state.photo != null),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("completion_submit"),
                shape = RoundedCornerShape(18.dp),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Text(
                        text = stringResource(R.string.completion_saving),
                        modifier = Modifier.padding(start = 10.dp),
                    )
                } else {
                    Text(stringResource(R.string.completion_save))
                }
            }
            TextButton(
                onClick = onSkip,
                enabled = !state.isSubmitting && !state.isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("completion_skip"),
            ) {
                Text(stringResource(R.string.completion_skip))
            }
        }
    }
}

@Composable
internal fun CompletionPhotoPreview(photo: CompletionPhotoDraft) {
    val image by produceState<ImageBitmap?>(initialValue = null, photo.file.absolutePath) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(photo.file.absolutePath)?.asImageBitmap()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(20.dp))
            .testTag("completion_photo_preview"),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = requireNotNull(image),
                contentDescription = stringResource(R.string.completion_photo_preview),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
