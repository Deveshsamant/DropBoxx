package com.dropnest.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dropnest.model.PlatformFile

/** Android receives content through the share sheet; OS drag and drop is a later addition. */
@Composable
actual fun Modifier.externalDropTarget(
    onFiles: (List<PlatformFile>) -> Unit,
    onText: (String) -> Unit,
    onHover: (Boolean) -> Unit,
): Modifier = this
