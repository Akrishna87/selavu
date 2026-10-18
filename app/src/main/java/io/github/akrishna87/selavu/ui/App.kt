package io.github.akrishna87.selavu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen

@Composable
fun SelavuApp(vm: AppViewModel, onExit: () -> Unit) {
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Recurring items that came due while the app was in the background.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshRecurring() }
    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.message = null
        }
    }
    BackHandler { if (!vm.back()) onExit() }

    val screen = vm.screen
    val tab = vm.stack.first()
    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (screen == Screen.Home || screen == Screen.Reports || screen == Screen.Settings) {
                TabBar(tab, vm::open)
            }
        },
    ) { padding ->
        val l = ledger
        if (l == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        when (val s = screen) {
            Screen.Home -> HomeScreen(vm, l, padding)
            Screen.Reports -> ReportsScreen(vm, l, padding)
            Screen.Settings -> SettingsScreen(vm, l, padding)
            Screen.Categories -> CategoriesScreen(vm, l, padding)
            Screen.Repeating -> RepeatingScreen(vm, l, padding)
            is Screen.Edit -> androidx.compose.runtime.key(s) { EditScreen(vm, l, s.type, s.txId, padding) }
            is Screen.CategoryDetail -> CategoryDetailScreen(vm, l, s.categoryId, padding)
        }
    }
}

/** iOS tab bar: a hairline on top, icons over small labels, the current tab in the tint colour. */
@Composable
private fun TabBar(current: Screen, onOpen: (Screen) -> Unit) {
    Column(Modifier.fillMaxWidth().background(palette.bar)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp)) {
            Tab("Home", Icons.Rounded.Home, current == Screen.Home) { onOpen(Screen.Home) }
            Tab("Reports", Icons.Rounded.PieChart, current == Screen.Reports) { onOpen(Screen.Reports) }
            Tab("Settings", Icons.Rounded.Settings, current == Screen.Settings) { onOpen(Screen.Settings) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) palette.tint else palette.gray
    Column(
        Modifier.weight(1f).fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
