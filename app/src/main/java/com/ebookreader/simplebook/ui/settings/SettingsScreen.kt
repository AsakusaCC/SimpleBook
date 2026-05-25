package com.ebookreader.simplebook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.domain.model.getStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    if (navController != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Language
            Text(strings.language, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("zh" to strings.chinese, "en" to strings.english).forEach { (code, name) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = if (settings.language == code) 3.dp else 1.dp,
                                    color = if (settings.language == code) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { viewModel.updateLanguage(code) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Font Size
            Text(strings.fontSize, style = MaterialTheme.typography.titleMedium)
            Text("${settings.fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.fontSize,
                onValueChange = { viewModel.updateFontSize(it) },
                valueRange = 12f..28f,
                modifier = Modifier.fillMaxWidth()
            )

            // Line Height
            Text(strings.lineHeight, style = MaterialTheme.typography.titleMedium)
            Text(String.format("%.1fx", settings.lineHeight), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.lineHeight,
                onValueChange = { viewModel.updateLineHeight(it) },
                valueRange = 1.0f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )

            // Background Color
            Text(strings.background, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val colorPresets = listOf(
                    strings.white to 0xFFFFFFFF,
                    strings.sepia to 0xFFF5F0E1,
                    strings.dark to 0xFF2B2B2B,
                    strings.black to 0xFF000000,
                )
                colorPresets.forEach { (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(color), CircleShape)
                                .border(
                                    width = if (settings.backgroundColor == color) 3.dp else 1.dp,
                                    color = if (settings.backgroundColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.updateBackgroundColor(color) }
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // About
            Text(strings.about, style = MaterialTheme.typography.titleMedium)
            Text("SimpleBook v1.0", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
