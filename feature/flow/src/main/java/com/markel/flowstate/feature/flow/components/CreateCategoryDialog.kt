package com.markel.flowstate.feature.flow.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.markel.flowstate.core.domain.Category
import com.markel.flowstate.feature.tasks.R

/**
 * Inline dialog used by the FlowScreen "+ New category" tab.
 * Rejects blank names and the localized name of the built-in General category.
 */
@Composable
fun CreateCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    val trimmedName = categoryName.trim()
    val generalName = stringResource(R.string.category_general)
    val isReservedName = Category.isReservedGeneralName(trimmedName, generalName)
    val isValid = trimmedName.isNotBlank() && !isReservedName

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.categories_create)) },
        text = {
            OutlinedTextField(
                value = categoryName,
                onValueChange = { categoryName = it },
                label = { Text(stringResource(R.string.categories_name_label)) },
                singleLine = true,
                isError = isReservedName,
                supportingText = if (isReservedName) {
                    { Text(stringResource(R.string.categories_name_reserved)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName) },
                enabled = isValid
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
