package com.newflytaxi.driver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newflytaxi.driver.location.DriverLocationService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NewFlyTaxiTheme { DriverApp() } }
    }
}

/** Screen routing for the driver app (mirrors the reference tab/order structure). */
private sealed interface Screen {
    data object Login : Screen
    data object Home : Screen
    data object Accept : Screen
    data object Active : Screen
    data object Done : Screen
}

@Composable
fun DriverApp(vm: DriverViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Login) }

    // keep the foreground location service in step with the driver state
    LaunchedEffect(s.online, s.current?.id) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        when {
            s.current != null -> DriverLocationService.setOrder(ctx, s.current?.id)
            s.online -> DriverLocationService.start(ctx)
            else -> DriverLocationService.stop(ctx)
        }
    }

    when (screen) {
        Screen.Login -> LoginScreen(
            onLogin = { tel, pwd -> vm.login(tel, pwd) },
            loading = s.loading, error = s.error,
        )
        Screen.Home -> HomeScreen(
            driverName = s.driver?.name ?: "",
            carNumber = s.driver?.carNumber ?: "",
            online = s.online,
            activeOrder = s.current,
            available = s.orders,
            onToggleOnline = { vm.setOnline(it) },
            onAccept = { o -> vm.accept(o); screen = Screen.Accept },
        )
        Screen.Accept -> s.current?.let { o ->
            AcceptOrderScreen(
                order = o,
                countdownSeconds = s.countdownSeconds,
                onArrive = { vm.arrive() },
                onSwipeAccept = { vm.accept(o); screen = Screen.Active },
            )
        }
        Screen.Active -> s.current?.let { o ->
            ActiveOrderScreen(
                order = o,
                onArrive = { vm.arrive() },
                onStartRide = { vm.startRide() },
                onArriveDropoff = { vm.arriveDropoff() },
                onComplete = { vm.complete(); screen = Screen.Done },
                onCancel = { vm.cancel(); screen = Screen.Home },
            )
        }
        Screen.Done -> s.current?.let { o ->
            OrderCompleteScreen(order = o, onDone = { vm.clearOrder(); screen = Screen.Home })
        }
    }

    // navigation side effects from the view model
    LaunchedEffect(s.loggedIn) { if (s.loggedIn && screen == Screen.Login) screen = Screen.Home }
}
