package com.newflytaxi.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.newflytaxi.driver.data.Order

/* ------------------------------------------------------------------ login
 * reference: res/layout/login_fragment_layout.xml
 *   LockableScrollView > background canvas > header bar > description text
 *   > password layout > progress > WhatsApp support button
 */
@Composable
fun LoginScreen(onLogin: (String, String) -> Unit, loading: Boolean, error: String?) {
    var tel by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Tokens.Panel)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(80.dp))
            Text("new-fly-taxi", color = Tokens.Gold, fontSize = Tokens.fontXl, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Tokens.spaceMd))
            Text("司機夥伴登入", color = Tokens.White, fontSize = Tokens.fontMd)
            Spacer(Modifier.height(Tokens.spaceLg))
            OutlinedTextField(
                tel, { tel = it }, label = { Text("電話號碼") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Tokens.spaceSm))
            OutlinedTextField(
                pwd, { pwd = it }, label = { Text("密碼") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            if (error != null) {
                Spacer(Modifier.height(Tokens.spaceSm))
                Text(error, color = Tokens.Danger, fontSize = Tokens.fontSm)
            }
            Spacer(Modifier.height(Tokens.spaceLg))
            Button(
                onClick = { onLogin(tel, pwd) },
                enabled = !loading && tel.isNotBlank() && pwd.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(Tokens.buttonHeight),
                shape = Tokens.corner,
            ) { Text(if (loading) "登入中…" else "登入", fontSize = Tokens.fontMd) }
            Spacer(Modifier.weight(1f))
            Text(
                "需要幫助？聯絡客服",
                color = Tokens.TextDim,
                fontSize = Tokens.fontSm,
                modifier = Modifier.padding(bottom = Tokens.spaceLg).clickable { },
            )
        }
    }
}

/* ------------------------------------------------------------------ home
 * reference: res/layout/main_layout.xml
 *   DrawerLayout > Toolbar + MaterialTabLayout (tabs) + ViewPager
 *   + progress overlay + black_layout (idle/offline) + screen_saver_layout
 */
@Composable
fun HomeScreen(
    driverName: String,
    carNumber: String,
    online: Boolean,
    activeOrder: Order?,
    available: List<Order>,
    onToggleOnline: (Boolean) -> Unit,
    onAccept: (Order) -> Unit,
    onMore: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        // toolbar + tab bar
        Row(
            Modifier.fillMaxWidth().padding(Tokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(driverName.ifBlank { "司機" }, color = Tokens.White, fontSize = Tokens.fontMd)
                Text(carNumber, color = Tokens.Gold, fontSize = Tokens.fontSm)
            }
            Switch(online, onToggleOnline)
            Spacer(Modifier.width(Tokens.spaceSm))
            Text("更多", color = Tokens.Gold, fontSize = Tokens.fontSm, modifier = Modifier.clickable { onMore() })
        }
        Row(Modifier.fillMaxWidth().background(Tokens.Sheet)) {
            listOf("派單", "行程", "收入").forEachIndexed { i, t ->
                Text(
                    t,
                    color = if (tab == i) Tokens.Gold else Tokens.TextDim,
                    fontSize = Tokens.fontMd,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = i }
                        .padding(vertical = Tokens.spaceSm),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        when (tab) {
            0 -> DispatchList(available, onAccept)
            1 -> OrderHistoryPlaceholder()
            else -> EarningsPlaceholder()
        }
    }
}

@Composable
fun DispatchList(orders: List<Order>, onAccept: (Order) -> Unit) {
    if (orders.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("附近暫時冇訂單", color = Tokens.TextDim, fontSize = Tokens.fontMd)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
        items(orders) { o ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Tokens.spaceXs)
                    .clickable { onAccept(o) },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Tokens.Sheet),
            ) {
                Column(Modifier.padding(Tokens.spaceMd)) {
                    Row {
                        Text("#${o.id}", color = Tokens.Gold, fontSize = Tokens.fontSm)
                        Spacer(Modifier.weight(1f))
                        Text("${o.distance} m", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    }
                    Text(o.pickupAddress ?: "-", color = Tokens.White, fontSize = Tokens.fontMd)
                    Text(o.dropoffAddress ?: "-", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                }
            }
        }
    }
}

@Composable
private fun OrderHistoryPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("行程紀錄", color = Tokens.TextDim, fontSize = Tokens.fontMd)
    }
}

@Composable
private fun EarningsPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("收入紀錄", color = Tokens.TextDim, fontSize = Tokens.fontMd)
    }
}

/* ------------------------------------------------------------------ active order
 * reference: res/layout/order_fragment_main_layout.xml
 *   map (google map fragment + navigation buttons: google / amap / waze)
 *   + order_layout (order id + message) + seek_bar_layout with SwipeBar
 */
@Composable
fun ActiveOrderScreen(
    order: Order,
    onArrive: () -> Unit,
    onStartRide: () -> Unit,
    onArriveDropoff: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        // map placeholder (Google Maps Compose is wired in the service module)
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Tokens.Sheet)
        ) {
            Text(
                "地圖\n${order.pickupAddress ?: ""} → ${order.dropoffAddress ?: ""}",
                color = Tokens.TextDim,
                fontSize = Tokens.fontSm,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(Tokens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(Tokens.spaceXs),
            ) {
                NavChip("Google") { }
                NavChip("Amap") { }
                NavChip("Waze") { }
            }
        }
        // order card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .background(Tokens.White)
                .padding(Tokens.spaceMd)
        ) {
            Text("#${order.id}", color = Tokens.TextDim, fontSize = Tokens.fontSm)
            Text(
                when (order.status) {
                    "14" -> "已接單，前往接載點"
                    "21" -> "已到達接載點，可以開始行程"
                    "31" -> "行程中"
                    "41" -> "已到達落車點"
                    else -> order.statusLabel ?: order.status
                },
                color = Tokens.Panel, fontSize = Tokens.fontMd,
            )
            Spacer(Modifier.height(Tokens.spaceSm))
            SwipeBar(
                label = when (order.status) {
                    "14" -> "滑動：已到接載點"
                    "21" -> "滑動：開始行程"
                    "31" -> "滑動：已到落車點"
                    "41" -> "滑動：完成行程"
                    else -> ""
                },
                enabled = order.status in listOf("14", "21", "31", "41"),
                onConfirm = {
                    when (order.status) {
                        "14" -> onArrive()
                        "21" -> onStartRide()
                        "31" -> onArriveDropoff()
                        "41" -> onComplete()
                    }
                },
            )
            Spacer(Modifier.height(Tokens.spaceXs))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消訂單") }
        }
    }
}

@Composable
private fun NavChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Tokens.Panel)
            .clickable { onClick() }
            .padding(horizontal = Tokens.spaceSm, vertical = Tokens.spaceXs)
    ) { Text(label, color = Tokens.White, fontSize = Tokens.fontSm) }
}

/* ------------------------------------------------------------------ complete
 * reference: order completion panel in the order layout (amount breakdown)
 */
@Composable
fun OrderCompleteScreen(order: Order, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Panel)
            .padding(Tokens.spaceMd)
    ) {
        Text("行程完成", color = Tokens.Gold, fontSize = Tokens.fontXl, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Tokens.spaceMd))
        Text("#${order.id}", color = Tokens.White, fontSize = Tokens.fontMd)
        Spacer(Modifier.height(Tokens.spaceMd))
        order.fare?.let { f ->
            Row(Modifier.fillMaxWidth()) {
                Text("車資", color = Tokens.White, fontSize = Tokens.fontMd)
                Spacer(Modifier.weight(1f))
                Text("HK$${f.amountFare}", color = Tokens.White, fontSize = Tokens.fontMd)
            }
            f.summary.forEach { line ->
                Row(Modifier.fillMaxWidth().padding(top = Tokens.spaceXs)) {
                    Text(line.title ?: "", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    Spacer(Modifier.weight(1f))
                    Text("HK$${line.amountFare}", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(Tokens.buttonHeight),
            shape = Tokens.corner,
        ) { Text("完成", fontSize = Tokens.fontMd) }
    }
}
