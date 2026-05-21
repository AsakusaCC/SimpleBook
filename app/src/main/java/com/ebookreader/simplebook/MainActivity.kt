package com.ebookreader.simplebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.ui.components.AdaptiveScaffold
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import dagger.hilt.android.AndroidEntryPoint

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleBookTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                    val navController = rememberNavController()
                    AdaptiveScaffold(
                        windowSizeClass = windowSizeClass,
                        navController = navController
                    ) {
                        SimpleBookNavHost(
                            navController = navController,
                            windowSizeClass = windowSizeClass
                        )
                    }
                }
            }
        }
    }
}
