package fr.conichonhaa.nakivo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.Tab
import fr.conichonhaa.nakivo.ui.UiState
import fr.conichonhaa.nakivo.ui.screens.DashboardScreen
import fr.conichonhaa.nakivo.ui.screens.DebugScreen
import fr.conichonhaa.nakivo.ui.screens.JobDetailScreen
import fr.conichonhaa.nakivo.ui.screens.JobsScreen
import fr.conichonhaa.nakivo.ui.screens.LoginScreen
import fr.conichonhaa.nakivo.ui.screens.StorageScreen
import fr.conichonhaa.nakivo.ui.theme.NakivoTheme
import fr.conichonhaa.nakivo.ui.web.WebDashboardScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NakivoTheme {
                Surface(Modifier.fillMaxSize()) {
                    val vm: AppViewModel = viewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    when {
                        state.webActive -> WebDashboardScreen(state, vm)
                        state.loggedIn -> MainScreen(state, vm)
                        else -> LoginScreen(state, vm)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(state: UiState, vm: AppViewModel) {
    // Actualisation : immédiate au retour dans l'app, puis périodique tant qu'elle est au premier plan.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, state.refreshSeconds) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            vm.refresh(silent = state.lastUpdate != null)
            if (state.refreshSeconds > 0) {
                while (true) {
                    delay(state.refreshSeconds * 1000L)
                    vm.refresh(silent = true)
                }
            }
        }
    }

    val selectedJob = state.selectedJobId?.let { id -> state.jobs.firstOrNull { it.id == id } }
    BackHandler(enabled = state.selectedJobId != null) { vm.closeJob() }
    BackHandler(enabled = state.showDebug && state.selectedJobId == null) { vm.closeDebug() }

    var menuOpen by remember { mutableStateOf(false) }
    val inSubScreen = selectedJob != null || state.showDebug

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selectedJob != null -> selectedJob.name
                            state.showDebug -> "Paramètres & diagnostic"
                            state.tab == Tab.JOBS -> "Jobs"
                            state.tab == Tab.STORAGE -> "Stockage"
                            else -> "Tableau de bord"
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (inSubScreen) {
                        IconButton(onClick = { if (selectedJob != null) vm.closeJob() else vm.closeDebug() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Actualiser")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Paramètres & diagnostic") },
                            onClick = { menuOpen = false; vm.openDebug() },
                        )
                        DropdownMenuItem(
                            text = { Text("Se déconnecter") },
                            onClick = { menuOpen = false; vm.logout() },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (!inSubScreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.tab == Tab.DASHBOARD,
                        onClick = { vm.selectTab(Tab.DASHBOARD) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Accueil") },
                    )
                    NavigationBarItem(
                        selected = state.tab == Tab.JOBS,
                        onClick = { vm.selectTab(Tab.JOBS) },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Jobs") },
                    )
                    NavigationBarItem(
                        selected = state.tab == Tab.STORAGE,
                        onClick = { vm.selectTab(Tab.STORAGE) },
                        icon = { Icon(painterResource(R.drawable.ic_storage), contentDescription = null) },
                        label = { Text("Stockage") },
                    )
                }
            }
        },
    ) { padding ->
        when {
            selectedJob != null -> JobDetailScreen(selectedJob, padding)
            state.showDebug -> DebugScreen(state, vm, padding)
            else -> {
                // L'indicateur de rafraîchissement doit apparaître sous la barre du haut.
                val listPadding = PaddingValues(bottom = padding.calculateBottomPadding())
                PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = padding.calculateTopPadding()),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        when (state.tab) {
                            Tab.DASHBOARD -> DashboardScreen(state, vm, listPadding)
                            Tab.JOBS -> JobsScreen(state, vm, listPadding)
                            Tab.STORAGE -> StorageScreen(state, listPadding)
                        }
                    }
                }
            }
        }
    }
}
