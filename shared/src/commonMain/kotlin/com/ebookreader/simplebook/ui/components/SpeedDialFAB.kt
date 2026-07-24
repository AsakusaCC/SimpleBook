package com.ebookreader.simplebook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

data class SpeedDialItem(
    val icon: @Composable () -> Unit,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun SpeedDialFAB(
    items: List<SpeedDialItem>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.End
    ) {
        items.reversed().forEach { item ->
            AnimatedVisibility(
                visible = isExpanded,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
            ) {
                MiniFAB(item = item)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        FloatingActionButton(
            onClick = onToggle
        ) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    rotationZ = if (isExpanded) 45f else 0f
                }
            )
        }
    }
}

@Composable
private fun MiniFAB(item: SpeedDialItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium
        )
        SmallFloatingActionButton(
            onClick = item.onClick
        ) {
            item.icon()
        }
    }
}
