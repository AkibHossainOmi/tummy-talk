package com.omi.kickcounter.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omi.kickcounter.R
import com.omi.kickcounter.service.KickService
import com.omi.kickcounter.ui.components.ScreenBackground
import com.omi.kickcounter.ui.screens.HistoryScreen
import com.omi.kickcounter.ui.screens.HomeScreen
import com.omi.kickcounter.ui.screens.SettingsScreen
import com.omi.kickcounter.ui.theme.KickCounterTheme

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Granted or not, start the service. Without the permission the
            // notification is hidden but in-app logging still works.
            KickService.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            KickService.start(this)
        }

        setContent {
            KickCounterTheme {
                ScreenBackground {
                    KickCounterApp(onShare = ::shareCsv)
                }
            }
        }
    }

    private fun shareCsv(uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(share, getString(R.string.export_csv)))
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Today", Icons.Outlined.CalendarMonth),
    History("History", Icons.Outlined.Insights),
    Settings("Settings", Icons.Outlined.Tune),
}

@Composable
private fun KickCounterApp(onShare: (Uri) -> Unit) {
    val viewModel: KickViewModel = viewModel(factory = KickViewModel.Factory)
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Transparent,
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val current = snapshot
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "tab",
        ) { visible ->
            val content = Modifier.padding(padding)
            when (visible) {
                Tab.Home -> HomeScreen(
                    snapshot = current,
                    onKick = { viewModel.recordKick() },
                    onUndo = { viewModel.undoLast() },
                    onRedo = { viewModel.redoLast() },
                    onStartSession = { viewModel.startSession(current.settings.dailyGoal) },
                    modifier = content,
                )
                Tab.History -> HistoryScreen(snapshot = current, modifier = content)
                Tab.Settings -> SettingsScreen(
                    snapshot = current,
                    viewModel = viewModel,
                    onShare = onShare,
                    modifier = content,
                )
            }
        }
    }
}
