package ma.techmare.recall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import ma.techmare.recall.ui.AppViewModel
import ma.techmare.recall.ui.BottomNav
import ma.techmare.recall.ui.C
import ma.techmare.recall.ui.CardEditorScreen
import ma.techmare.recall.ui.DeckScreen
import ma.techmare.recall.ui.DecksScreen
import ma.techmare.recall.ui.HomeScreen
import ma.techmare.recall.ui.NameDialog
import ma.techmare.recall.ui.RecallTheme
import ma.techmare.recall.ui.ReviewScreen
import ma.techmare.recall.ui.SettingsScreen
import ma.techmare.recall.ui.StatsScreen
import ma.techmare.recall.ui.Tab
import ma.techmare.recall.ui.body

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(C.Ground.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(C.Nav.toArgb()),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            RecallTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route
                val message by vm.message.collectAsState()
                val importing by vm.importing.collectAsState()
                var newDeck by remember { mutableStateOf(false) }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    uri?.let { vm.import(it) }
                }
                val import = { picker.launch(arrayOf("application/octet-stream", "application/zip", "application/apkg", "*/*")) }
                fun review(deck: Long?) { vm.startSession(deck); nav.navigate("review") }

                Box(Modifier.fillMaxSize().background(C.Ground)) {
                    Column(Modifier.fillMaxSize()) {
                        NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f)) {
                            composable("home") {
                                HomeScreen(vm, ::review, { nav.navigate("deck/$it") }, import) { newDeck = true }
                            }
                            composable("decks") { DecksScreen(vm, { nav.navigate("deck/$it") }, import) { newDeck = true } }
                            composable("stats") { StatsScreen(vm) }
                            composable("settings") { SettingsScreen(vm) }
                            composable("review") { ReviewScreen(vm) { nav.popBackStack() } }
                            composable("deck/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                                DeckScreen(vm, e.arguments!!.getLong("id"), { nav.popBackStack() }, ::review) { d, c -> nav.navigate("card/$d/$c") }
                            }
                            composable("card/{deck}/{card}", listOf(
                                navArgument("deck") { type = NavType.LongType }, navArgument("card") { type = NavType.LongType },
                            )) { e ->
                                CardEditorScreen(vm, e.arguments!!.getLong("deck"), e.arguments!!.getLong("card")) { nav.popBackStack() }
                            }
                        }
                        if (route in Tab.entries.map { it.route }) BottomNav(route) { tab ->
                            nav.navigate(tab.route) {
                                popUpTo("home") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    if (importing) Box(Modifier.fillMaxSize().background(C.Ground.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = C.Accent)
                            Text("Importing deck…", style = body(15, 600), modifier = Modifier.padding(top = 16.dp))
                        }
                    }
                    message?.let { m ->
                        LaunchedEffect(m) { delay(6000); if (vm.message.value == m) vm.message.value = null }
                        Snackbar(
                            Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 80.dp).fillMaxWidth(),
                            containerColor = C.Surface, contentColor = C.Text,
                            action = { TextButton({ vm.message.value = null }) { Text("OK", color = C.AccentSoft) } },
                        ) { Text(m, style = body(14, 600)) }
                    }
                    if (newDeck) NameDialog("New deck", "", onDismiss = { newDeck = false }) { vm.createDeck(it); newDeck = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        } ?: return
        vm.import(uri)
    }
}
