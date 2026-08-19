package com.ebookreader.simplebook

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.R
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.domain.service.toImportSources
import com.ebookreader.simplebook.platform.toIntent
import com.ebookreader.simplebook.ui.components.AdaptiveScaffold
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.booklist.BookListViewModel
import com.ebookreader.simplebook.ui.settings.SettingsViewModel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import org.koin.compose.viewmodel.koinViewModel
import org.koin.mp.KoinPlatform

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for sync foreground service (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settings by settingsViewModel.settings.collectAsState()

            SimpleBookTheme(readerTheme = settings.theme) {
                val bookListViewModel: BookListViewModel = koinViewModel()
                val isDataReady by bookListViewModel.isDataReady.collectAsState()
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
                                "Simple Book v${AppVersion.NAME}",
                                color = Color(0xFF999999),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    LaunchedEffect(isDataReady) {
                        if (isDataReady) {
                            delay(300)
                            showSplash = false
                        }
                    }
                }

                if (!showSplash) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                        val navController = rememberNavController()
                        val syncViewModel: SyncViewModel = koinViewModel()
                        val authManager = remember {
                            KoinPlatform.getKoin().get<AuthManager>()
                        }
                        val coroutineScope = rememberCoroutineScope()
                        val strings = remember(settings.language) { getStrings(settings.language) }

                        // Google Sign-In: AuthProvider.signIn() can't drive this because the
                        // Android flow needs an Activity + Intent result, which commonMain can't
                        // reference. So the launcher lives here (UI layer), drives AuthManager
                        // directly, then tells SyncViewModel to refresh its observable state.
                        val googleSignInLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult()
                        ) { result ->
                            val data = result.data
                            if (data != null) {
                                coroutineScope.launch {
                                    val error = authManager.handleSignInResult(data)
                                    syncViewModel.refreshAuthState()
                                    if (error == null) {
                                        syncViewModel.clearSignInError()
                                        syncViewModel.syncNow()   // 登录成功后自动同步（含 reauth 重授权）
                                    } else {
                                        syncViewModel.setSignInError(error)
                                    }
                                }
                            } else {
                                syncViewModel.setSignInError("登录已取消 (code=${result.resultCode})")
                            }
                        }

                        // Android SAF import: OpenMultipleDocuments returns the picked Uris
                        // directly. Convert to ImportSource list and feed the shared
                        // Activity-scoped BookListViewModel (same instance used for the splash
                        // gate, declared at the SimpleBookTheme scope above). The shelf list
                        // refreshes via Room Flow once books land in the DB.
                        val context = LocalContext.current
                        val importLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenMultipleDocuments()
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                bookListViewModel.importSources(uris.toImportSources(context))
                            }
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
                        DisposableEffect(lifecycleOwner) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) {
                                    syncViewModel.autoSyncIfEnabled()
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
                                signInLauncher = { googleSignInLauncher.launch(authManager.signInIntent) },
                                onReauthClick = { req ->
                                    req.toIntent()?.let { googleSignInLauncher.launch(it) }
                                },
                                onImportClick = {
                                    importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf"))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
