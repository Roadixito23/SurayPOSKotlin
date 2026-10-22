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
import cl.suray.posbus.ui.cargo.CargoScreen
import cl.suray.posbus.ui.home.Destination
import cl.suray.posbus.ui.home.HomeScreen
import cl.suray.posbus.ui.home.HomeViewModel
import cl.suray.posbus.ui.login.LoginScreen
import cl.suray.posbus.ui.backup.BackupScreen
import cl.suray.posbus.ui.backup.BackupViewModel
import cl.suray.posbus.ui.recovery.RecoveryScreen
import cl.suray.posbus.ui.stats.StatsScreen
import cl.suray.posbus.ui.stats.StatsViewModel
import cl.suray.posbus.ui.recovery.RecoveryViewModel
import cl.suray.posbus.ui.report.ReportScreen
import cl.suray.posbus.ui.report.ReportViewModel
import cl.suray.posbus.ui.settings.ButtonColorsScreen
import cl.suray.posbus.ui.settings.ButtonColorsViewModel
import cl.suray.posbus.ui.settings.PrinterDiagViewModel
import cl.suray.posbus.ui.settings.SettingsScreen
import cl.suray.posbus.ui.transfer.TransferScreen
import cl.suray.posbus.ui.settings.SettingsViewModel

private const val LOGIN = "login"
private const val HOME = "home"
private const val CARGO = "cargo"
private const val BUTTON_COLORS = "button_colors"

@Composable
fun SurayNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = LOGIN) {
        composable(LOGIN) {
            LoginScreen(
                verify = container.passwords::verifyLogin,
                lockout = container.lockout,
                onSuccess = { nav.navigate(HOME) { popUpTo(LOGIN) { inclusive = true } } },
            )
        }
        composable(HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
            HomeScreen(
                vm = vm,
                onNavigate = { nav.navigate(it.name) },
                onCargo = { nav.navigate(CARGO) },
                onLogout = { nav.navigate(LOGIN) { popUpTo(HOME) { inclusive = true } } },
            )
        }
        composable(BUTTON_COLORS) {
            val vm: ButtonColorsViewModel = viewModel(factory = ButtonColorsViewModel.Factory(container))
            ButtonColorsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(CARGO) {
            // Comparte el ViewModel de Home: la venta pasa por la misma canalización (cierre automático, comprobante, caja).
            val homeVm: HomeViewModel = viewModel(nav.getBackStackEntry(HOME), factory = HomeViewModel.Factory(container))
            CargoScreen(onSubmit = { homeVm.sellCargo(it); nav.popBackStack() }, onBack = { nav.popBackStack() })
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
            val printerVm: PrinterDiagViewModel = viewModel(factory = PrinterDiagViewModel.Factory(container))
            SettingsScreen(vm = vm, printerVm = printerVm, onBack = { nav.popBackStack() }, onButtonColors = { nav.navigate(BUTTON_COLORS) })
        }
        composable(Destination.Transfer.name) {
            TransferScreen(onBack = { nav.popBackStack() })
        }
        composable(Destination.Backup.name) {
            val vm: BackupViewModel = viewModel(factory = BackupViewModel.Factory(container))
            BackupScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Destination.Statistics.name) {
            val vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory(container))
            StatsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(Destination.Recovery.name) {
            val vm: RecoveryViewModel = viewModel(factory = RecoveryViewModel.Factory(container))
            RecoveryScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        Destination.entries.filter { it != Destination.Reports && it != Destination.Settings && it != Destination.Recovery && it != Destination.Statistics && it != Destination.Backup && it != Destination.Transfer }.forEach { d ->
            composable(d.name) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("${d.label}: pendiente de portar") }
            }
        }
    }
}
