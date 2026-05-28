package com.ebookreader.simplebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.ui.components.AdaptiveScaffold
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.settings.SettingsViewModel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrDefault("0.8")
            }

            SimpleBookTheme(readerTheme = settings.theme) {
                var showSplash by remember { mutableStateOf(true) }

                if (showSplash) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painter = painterResource(R.drawable.mizuki_logo),
                                contentDescription = null,
                                modifier = Modifier.size(240.dp)
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Simple Book v${versionName}",
                                color = Color(0xFF999999),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    LaunchedEffect(Unit) {
                        delay(1500)
                        showSplash = false
                    }
                }

                if (!showSplash) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                        val navController = rememberNavController()
                        val syncViewModel: SyncViewModel = hiltViewModel()
                        val strings = remember(settings.language) { getStrings(settings.language) }

                        val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            val data = result.data
                            if (data != null) {
                                syncViewModel.handleSignInResult(data)
                            } else {
                                syncViewModel.setSignInError("Sign-in cancelled or returned no data (code=${result.resultCode})")
                            }
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
                        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) {
                                    if (syncViewModel.isSignedIn) {
                                        syncViewModel.syncNow()
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }

                        AdaptiveScaffold(
                            windowSizeClass = windowSizeClass,
                            navController = navController,
                            strings = strings
                        ) {
                            SimpleBookNavHost(
                                navController = navController,
                                windowSizeClass = windowSizeClass,
                                syncViewModel = syncViewModel,
                                signInLauncher = { signInLauncher.launch(syncViewModel.getSignInIntent()) }
                            )
                        }
                    }
                }
            }
        }
    }
}
