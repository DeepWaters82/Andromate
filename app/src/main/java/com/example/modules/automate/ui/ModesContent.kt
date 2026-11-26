package com.example.modules.automate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.modules.automate.AutomationRoutine
import com.example.modules.automate.RoutineStyle

/** Grille des modes : un appui active ou arrête, un appui long ouvre Modifier / Supprimer. */
fun LazyListScope.modesContent(
    modes: List<AutomationRoutine>,
    activeModeId: String?,
    onToggle: (String) -> Unit,
    onEdit: (AutomationRoutine) -> Unit,
    onDelete: (AutomationRoutine) -> Unit
) {
    if (modes.isEmpty()) {
        item {
            Text(
                text = "Aucun mode. Appuyez sur + pour en créer un.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
        return
    }
    modes.chunked(2).forEach { pair ->
        item(key = "modes_" + pair.joinToString("_") { it.id }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                pair.forEach { mode ->
                    ModeTile(
                        mode = mode,
                        active = mode.id == activeModeId,
                        onToggle = { onToggle(mode.id) },
                        onEdit = { onEdit(mode) },
                        onDelete = { onDelete(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeTile(
    mode: AutomationRoutine,
    active: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = RoutineStyle.colorFor(mode.colorKey) ?: MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    val container by animateColorAsState(
        if (active) accent else MaterialTheme.colorScheme.surfaceContainerLow,
        label = "modeContainer"
    )
    val content = if (active) Color.White else MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = container,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    onClick = onToggle,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuOpen = true
                    }
                )
                .testTag("mode_tile_${mode.id}")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (active) Color.White.copy(alpha = 0.22f) else accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RoutineStyle.iconFor(mode),
                        contentDescription = null,
                        tint = if (active) Color.White else accent
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = mode.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    // « Manuel » n’apprend rien : la ligne reste vide (même hauteur de carte).
                    text = when {
                        active -> "Actif"
                        mode.hasAutoTrigger -> mode.triggerSummary()
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Modifier") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("Supprimer") },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}
