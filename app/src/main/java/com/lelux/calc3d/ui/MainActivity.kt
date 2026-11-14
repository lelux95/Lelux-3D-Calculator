package com.lelux.calc3d.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppTab(val label: String, val icon: ImageVector) {
    CALC("Calcolatore", Icons.Filled.Build),
    HISTORY("Storico", Icons.Filled.List),
    STOCK("Magazzino", Icons.Filled.ShoppingCart),
    SETTINGS("Impostazioni", Icons.Filled.Settings),
}

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val selectedTab = mutableStateOf(AppTab.CALC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val data by vm.data.collectAsState()
            LeluxTheme(data.settings.theme) {
                if (!data.settings.onboardingDone) {
                    Onboarding(vm) {
                        vm.pendingUri?.let { u -> vm.pendingUri = null; vm.importUri(u) }
                    }
                } else {
                    AppScaffold(vm, selectedTab.value) { selectedTab.value = it }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        }
        if (uri != null) {
            selectedTab.value = AppTab.CALC
            vm.importUri(uri)
        }
    }
}

@Composable
fun AppScaffold(vm: AppViewModel, tab: AppTab, onTab: (AppTab) -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val busy by vm.busy.collectAsState()
    val editing by vm.editing.collectAsState()
    var overlay by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    BackHandler(enabled = tab != AppTab.CALC && editing == null) { onTab(AppTab.CALC) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    AppTab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = t == tab,
                            onClick = { onTab(t) },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    AppTab.CALC -> CalculatorScreen(vm)
                    AppTab.HISTORY -> HistoryScreen(vm, openInCalculator = { onTab(AppTab.CALC) })
                    AppTab.STOCK -> StockScreen(vm, openInCalculator = { onTab(AppTab.CALC) })
                    AppTab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        editing?.let { (part, isNew) ->
            PartEditor(vm, part, isNew, onClose = { vm.editing.value = null })
        }

        if (busy) {
            Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Lettura del file…", color = Color.White)
                }
            }
        }
    }
}
