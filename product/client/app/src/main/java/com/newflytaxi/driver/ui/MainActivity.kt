package com.newflytaxi.driver.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.newflytaxi.driver.data.Order

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme { DriverApp() }
        }
    }
}

@Composable
fun DriverApp(vm: DriverViewModel = viewModel()) {
    val s by vm.state.collectAsState()
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("new-fly-taxi 司機", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(8.dp)) }
            when {
                !s.loggedIn -> LoginPane(vm)
                s.current != null -> OrderPane(s.current!!, vm)
                else -> HomePane(s, vm)
            }
        }
    }
}

@Composable
private fun LoginPane(vm: DriverViewModel) {
    var tel by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(tel, { tel = it }, label = { Text("電話") }, singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(pwd, { pwd = it }, label = { Text("密碼") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation())
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.login(tel, pwd) }, enabled = tel.isNotBlank() && pwd.isNotBlank()) {
            Text("登入")
        }
    }
}

@Composable
private fun HomePane(s: DriverUiState, vm: DriverViewModel) {
    var lat by remember { mutableStateOf(22.3089203) }
    var lon by remember { mutableStateOf(114.157572) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(s.online, { vm.setOnline(it, lat, lon) })
            Spacer(Modifier.width(8.dp))
            Text(if (s.online) "上線中（每 5 秒刷新派單）" else "已下線")
        }
        Spacer(Modifier.height(12.dp))
        Text("附近訂單（半徑 ${s.config.dispatchRadiusM} m）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxWidth()) {
            items(s.orders) { o ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("#${o.id}  ${o.status} ${o.statusLabel.orEmpty()}")
                        Text(o.pickupAddress.orEmpty())
                        Text(o.dropoffAddress.orEmpty())
                        Text("距離 ${o.distance} m")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { vm.accept(o) }) { Text("接單") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderPane(o: Order, vm: DriverViewModel) {
    Column {
        Text("#${o.id}  ${o.status} ${o.statusLabel.orEmpty()}", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("上車：${o.pickupAddress.orEmpty()}")
        Text("落車：${o.dropoffAddress.orEmpty()}")
        Spacer(Modifier.height(16.dp))
        // Buttons are driven by the order status — the server rejects anything else.
        when (o.status) {
            "14" -> Button(onClick = { vm.arrivedPickup() }) { Text("已到接載點") }
            "21" -> Button(onClick = { vm.startRide() }) { Text("開始行程") }
            "31" -> Button(onClick = { vm.arrivedDropoff() }) { Text("已到落車點") }
            "41" -> Button(onClick = { vm.complete() }) { Text("完成行程") }
            else -> Text("訂單進行中…")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.cancel() }) { Text("取消訂單") }
        o.fare?.let { f ->
            Spacer(Modifier.height(16.dp))
            Text("車資 HK$${f.amountFare}")
            f.summary.forEach { Text("${it.title}: ${it.amountFare}") }
        }
    }
}
