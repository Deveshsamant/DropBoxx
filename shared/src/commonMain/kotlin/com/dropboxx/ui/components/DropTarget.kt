package com.dropboxx.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dropboxx.model.PlatformFile

/**
 * Accepts files / text dragged from the OS. Desktop implements it with AWT drag and drop;
 * Android returns the modifier unchanged (the share sheet is the entry point there).
 */
@Composable
expect fun Modifier.externalDropTarget(
    onFiles: (List<PlatformFile>) -> Unit,
    onText: (String) -> Unit,
    onHover: (Boolean) -> Unit,
): Modifier
