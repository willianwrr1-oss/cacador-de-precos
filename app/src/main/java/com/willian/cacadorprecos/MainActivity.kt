package com.willian.cacadorprecos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.ui.AddScreen
import com.willian.cacadorprecos.ui.AlertsScreen
import com.willian.cacadorprecos.ui.DetailScreen
import com.willian.cacadorprecos.ui.HomeScreen
import com.willian.cacadorprecos.ui.Pal
import com.willian.cacadorprecos.ui.PrecosTheme
import com.willian.cacadorprecos.ui.SettingsScreen
import com.willian.cacadorprecos.ui.SourcesScreen
import com.willian.cacadorprecos.work.Scheduler

sealed class Route {
    object Home : Route()
    object Alerts : Route()
    object Sources : Route()
    object Settings : Route()
    object Add : Route()
    data class Detail(val id: String) : Route()
}

class MainActivity : ComponentActivity() {

    private val pedirNotificacao =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.start(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirNotificacao.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            PrecosTheme { AppRoot() }
        }
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val state by Repo.state.collectAsState()
    var route by remember { mutableStateOf<Route>(Route.Home) }
    val naoLidos = state.alerts.count { it.unread }

    BackHandler(enabled = route != Route.Home) { route = Route.Home }

    val cores = NavigationBarItemDefaults.colors(
        selectedIconColor = Pal.Teal,
        selectedTextColor = Pal.Teal,
        indicatorColor = Pal.GoodBg,
        unselectedIconColor = Pal.Muted,
        unselectedTextColor = Pal.Muted
    )

    Scaffold(
        containerColor = Pal.Bg,
        bottomBar = {
            if (route != Route.Add) {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = route == Route.Home || route is Route.Detail,
                        onClick = { route = Route.Home },
                        icon = { Icon(Icons.Filled.LocalOffer, contentDescription = null) },
                        label = { Text("Produtos") },
                        colors = cores
                    )
                    NavigationBarItem(
                        selected = route == Route.Alerts,
                        onClick = { route = Route.Alerts },
                        icon = {
                            BadgedBox(badge = { if (naoLidos > 0) Badge { Text(naoLidos.toString()) } }) {
                                Icon(Icons.Filled.Notifications, contentDescription = null)
                            }
                        },
                        label = { Text("Alertas") },
                        colors = cores
                    )
                    NavigationBarItem(
                        selected = route == Route.Sources,
                        onClick = { route = Route.Sources },
                        icon = { Icon(Icons.Filled.Public, contentDescription = null) },
                        label = { Text("Fontes") },
                        colors = cores
                    )
                    NavigationBarItem(
                        selected = route == Route.Settings,
                        onClick = { route = Route.Settings },
                        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        label = { Text("Ajustes") },
                        colors = cores
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val r = route) {
                Route.Home -> HomeScreen(
                    state = state,
                    onOpen = { route = Route.Detail(it) },
                    onAdd = { route = Route.Add },
                    onCheckAll = {
                        Scheduler.runNow(ctx)
                        Toast.makeText(ctx, "Verificando todos os produtos…", Toast.LENGTH_SHORT).show()
                    },
                    onRefreshOne = { id ->
                        Scheduler.runOne(ctx, id)
                        Toast.makeText(ctx, "Atualizando o preço deste produto…", Toast.LENGTH_SHORT).show()
                    }
                )
                is Route.Detail -> DetailScreen(state, r.id, onBack = { route = Route.Home })
                Route.Alerts -> AlertsScreen(state, onOpen = { route = Route.Detail(it) })
                Route.Sources -> SourcesScreen(state)
                Route.Settings -> SettingsScreen(state)
                Route.Add -> AddScreen(state, onClose = { route = Route.Home }, onSaved = { route = Route.Home })
            }
        }
    }
}
