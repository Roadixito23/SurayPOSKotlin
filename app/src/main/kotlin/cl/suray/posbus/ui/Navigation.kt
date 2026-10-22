package cl.suray.posbus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cl.suray.posbus.AppContainer
import cl.suray.posbus.ui.home.Destination
import cl.suray.posbus.ui.home.HomeScreen
import cl.suray.posbus.ui.home.HomeViewModel
import cl.suray.posbus.ui.login.LoginScreen
import cl.suray.posbus.ui.report.ReportScreen
import cl.suray.posbus.ui.report.ReportViewModel
import cl.suray.posbus.ui.settings.SettingsScreen
import cl.suray.posbus.ui.settings.SettingsViewModel

private const val LOGIN = "login"
private const val HOME = "home"

@Composable
fun SurayNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LOGIN) {
        composable(LOGIN) {
            LoginScreen(
                verify = container.passwords::verifyLogin,
                onSuccess = { nav.navigate(HOME) { popUpTo(LOGIN) { inclusive = true } } },
            )
        }
        composable(HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
            HomeScreen(
                vm = vm,
                onNavigate = { nav.navigate(it.name) },
                onLogout = { nav.navigate(LOGIN) { popUpTo(HOME) { inclusive = true } } },
            )
        }
        composable(Destination.Reports.name) {
            val vm: ReportViewModel = viewModel(factory = ReportViewModel.Factory(container))
            ReportScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOldReports = { nav.navigate(Destination.Recovery.name) },
            )
        }
        composable(Destination.Settings.name) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(container))
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        Destination.entries.filter { it != Destination.Reports && it != Destination.Settings }.forEach { d ->
            composable(d.name) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("${d.label}: pendiente de portar") }
            }
        }
    }
}
