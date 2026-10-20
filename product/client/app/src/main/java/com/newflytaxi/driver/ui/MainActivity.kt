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
    data object More : Screen
    data object Settings : Screen
    data object License : Screen
    data object CarModel : Screen
    data object Calculator : Screen
    data object Earnings : Screen
    data object History : Screen
    data object Payment : Screen
    data class Generic(val layout: String) : Screen
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
        Screen.More -> MoreMenuScreen(
            onBack = { screen = Screen.Home },
            onOpen = { key ->
                screen = when (key) {
                    "設定" -> Screen.Settings
                    "司機證件" -> Screen.License
                    "車輛型號" -> Screen.CarModel
                    "咪錶計算機" -> Screen.Calculator
                    "收入紀錄" -> Screen.Earnings
                    "行程紀錄" -> Screen.History
                    "支付方式" -> Screen.Payment
                    "投訴" -> Screen.Generic("complain_fragment_layout")
                    "公告" -> Screen.Generic("announcement_fragment_layout")
                    "客服" -> Screen.Generic("customer_service_detail_fragment_layout")
                    "權限設定" -> Screen.Generic("permission_setting_fragment_layout")
                    "字體大小" -> Screen.Generic("font_size_fragment_layout")
                    "無障礙服務" -> Screen.Generic("accessibility_service_fragment_layout")
                    "警告" -> Screen.Generic("warning_fragment_layout")
                    else -> Screen.Generic("setting_fragment_layout")
                }
            },
        )
        Screen.Settings -> SettingsScreen({ screen = Screen.More }) { screen = MoreRoute(it) }
        Screen.License -> DriverLicenseScreen({ screen = Screen.More }, listOf(
            LicenseDoc("行車駕照", "司機駕照", "2027-06-30", "已驗證"),
            LicenseDoc("巴士公司駕照", "接載資格證明書", "2026-12-31", "待更新"),
        ))
        Screen.CarModel -> CarModelScreen({ screen = Screen.More }, listOf(
            CarModelItem("豐田", "Prius", "4 座", true),
            CarModelItem("豐田", "Comfort", "4 座", false),
            CarModelItem("Tesla", "Model 3", "5 座", false),
        )) { }
        Screen.Calculator -> CalculatorScreen({ screen = Screen.More },
            listOf(
                FareRow("首 1.3 公里", "HK$27", "紅色 / 藍色的士"),
                FareRow("其後每 200 米", "HK$2.3"),
                FareRow("綠色的士首 1.3 公里", "HK$22"),
                FareRow("綠色的士其後每 200 米", "HK$1.9"),
            ),
            listOf(FareRow("隧道費（來回）", "HK$25"), FareRow("公司價下限（紅）", "HK$80")),
        )
        Screen.Earnings -> EarningsScreen({ screen = Screen.More }, listOf(
            EarningRow("2026-10-19", "HK$186.0", "車資"),
            EarningRow("2026-10-18", "HK$142.5", "車資"),
            EarningRow("2026-10-18", "-HK$6.0", "取消費"),
        ))
        Screen.History -> HistoryScreen({ screen = Screen.More }, listOf())
        Screen.Payment -> PaymentMethodsScreen({ screen = Screen.More }, listOf(
            "八達通" to "未綁定", "PayMe" to "未綁定", "WeChat Pay" to "未綁定", "支付寶" to "未綁定",
        ))
        is Screen.Generic -> SpecScreen(screen.layout)
        Screen.Home -> HomeScreen(
            driverName = s.driver?.name ?: "",
            carNumber = s.driver?.carNumber ?: "",
            online = s.online,
            activeOrder = s.current,
            available = s.orders,
            onToggleOnline = { vm.setOnline(it) },
            onAccept = { o -> vm.accept(o); screen = Screen.Accept },
            onMore = { screen = Screen.More },
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


private fun MoreRoute(key: String): Screen = when (key) {
    "設定" -> Screen.Settings
    "司機證件" -> Screen.License
    "車輛型號" -> Screen.CarModel
    "咪錶計算機" -> Screen.Calculator
    "收入紀錄" -> Screen.Earnings
    "行程紀錄" -> Screen.History
    "支付方式" -> Screen.Payment
    else -> Screen.Generic("setting_fragment_layout")
}
