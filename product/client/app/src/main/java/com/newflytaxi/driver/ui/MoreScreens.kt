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

/* ------------------------------------------------------------------ chrome */

@Composable
fun HeaderBar(title: String, onBack: () -> Unit, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Tokens.Panel)
            .padding(horizontal = Tokens.spaceMd, vertical = Tokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("‹", color = Tokens.White, fontSize = Tokens.fontXl, modifier = Modifier.clickable { onBack() })
        Spacer(Modifier.width(Tokens.spaceSm))
        Text(title, color = Tokens.White, fontSize = Tokens.fontMd, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Tokens.Gold, fontSize = Tokens.fontSm)
    }
}

@Composable
fun SectionList(
    items: List<Pair<String, String?>>,
    onClick: (String) -> Unit = {},
) {
    LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
        items(items) { (title, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Tokens.spaceXs)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Tokens.Sheet)
                    .clickable { onClick(title) }
                    .padding(Tokens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Tokens.White, fontSize = Tokens.fontMd, modifier = Modifier.weight(1f))
                value?.let { Text(it, color = Tokens.TextDim, fontSize = Tokens.fontSm) }
            }
        }
    }
}

/* --------------------------------------------------------------- settings
 * reference: SettingActivity (general_layout shell) + code-built rows
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("設定", onBack)
        SectionList(
            listOf(
                "司機證件" to null,
                "車輛型號" to null,
                "咪錶計算機" to null,
                "收入紀錄" to null,
                "行程紀錄" to null,
                "客服與投訴" to null,
                "支付方式" to null,
                "字體大小" to null,
                "權限設定" to null,
                "公告" to null,
            ),
            onOpen,
        )
    }
}

/* ---------------------------------------------------------------- license
 * reference: driver_license_fragment_layout.xml
 *   header bar + RecyclerView + WhatsApp support button
 *   row layouts: driver_license_name_row / _calendar_row / _preview_row / _take_photo_row
 */
data class LicenseDoc(val name: String, val type: String, val expiry: String, val status: String)

@Composable
fun DriverLicenseScreen(onBack: () -> Unit, docs: List<LicenseDoc>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("司機證件", onBack, trailing = "上傳")
        LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
            items(docs) { d ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tokens.spaceXs)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tokens.Sheet)
                        .padding(Tokens.spaceMd)
                ) {
                    Row {
                        Text(d.name, color = Tokens.White, fontSize = Tokens.fontMd, modifier = Modifier.weight(1f))
                        Text(
                            d.status,
                            color = if (d.status == "已驗證") Tokens.Gold else Tokens.Danger,
                            fontSize = Tokens.fontSm,
                        )
                    }
                    Text(d.type, color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    Text("有效期至 ${d.expiry}", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                }
            }
        }
        Text(
            "需要協助？聯絡客服",
            color = Tokens.TextDim,
            fontSize = Tokens.fontSm,
            modifier = Modifier.padding(Tokens.spaceMd),
        )
    }
}

/* --------------------------------------------------------------- car model
 * reference: driver_car_model_fragment_layout.xml + car_model_*_row layouts
 */
data class CarModelItem(val brand: String, val model: String, val seats: String, val selected: Boolean)

@Composable
fun CarModelScreen(onBack: () -> Unit, models: List<CarModelItem>, onPick: (CarModelItem) -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("車輛型號", onBack, trailing = "確認")
        LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
            items(models) { m ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tokens.spaceXs)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (m.selected) Tokens.Gold.copy(alpha = 0.18f) else Tokens.Sheet)
                        .clickable { onPick(m) }
                        .padding(Tokens.spaceMd),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.brand} ${m.model}", color = Tokens.White, fontSize = Tokens.fontMd)
                        Text("${m.seats} 座", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    }
                    if (m.selected) Text("✓", color = Tokens.Gold, fontSize = Tokens.fontMd)
                }
            }
        }
    }
}

/* -------------------------------------------------------------- calculator
 * reference: calculator_fragment_layout.xml + calculator_row_layout + option_layout
 *   the app's own HK meter tariff editor (ConfigHKTaxiMeterPriceItem)
 */
data class FareRow(val label: String, val value: String, val note: String? = null)

@Composable
fun CalculatorScreen(onBack: () -> Unit, rows: List<FareRow>, options: List<FareRow>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("咪錶計算機", onBack)
        LazyColumn(Modifier.weight(1f).padding(Tokens.spaceSm)) {
            items(rows) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tokens.spaceXs)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tokens.Sheet)
                        .padding(Tokens.spaceMd)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.label, color = Tokens.White, fontSize = Tokens.fontMd)
                        r.note?.let { Text(it, color = Tokens.TextDim, fontSize = Tokens.fontSm) }
                    }
                    Text(r.value, color = Tokens.Gold, fontSize = Tokens.fontMd)
                }
            }
        }
        if (options.isNotEmpty()) {
            Text("更多選項", color = Tokens.Gold, fontSize = Tokens.fontSm, modifier = Modifier.padding(Tokens.spaceSm))
            options.forEach { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Tokens.spaceMd, vertical = Tokens.spaceXs)
                ) {
                    Text(o.label, color = Tokens.White, fontSize = Tokens.fontSm, modifier = Modifier.weight(1f))
                    Text(o.value, color = Tokens.TextDim, fontSize = Tokens.fontSm)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- earnings
 * reference: earning_record_item_row + record_transaction_item_row
 */
data class EarningRow(val date: String, val amount: String, val type: String)

@Composable
fun EarningsScreen(onBack: () -> Unit, rows: List<EarningRow>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("收入紀錄", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
            items(rows) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tokens.spaceXs)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tokens.Sheet)
                        .padding(Tokens.spaceMd)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.date, color = Tokens.White, fontSize = Tokens.fontMd)
                        Text(r.type, color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    }
                    Text(r.amount, color = Tokens.Gold, fontSize = Tokens.fontMd)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- history
 * reference: history_item_row / order_item_main_row
 */
@Composable
fun HistoryScreen(onBack: () -> Unit, orders: List<Order>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("行程紀錄", onBack)
        LazyColumn(Modifier.fillMaxSize().padding(Tokens.spaceSm)) {
            items(orders) { o ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = Tokens.spaceXs)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Tokens.Sheet)
                        .padding(Tokens.spaceMd)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("#${o.id}", color = Tokens.Gold, fontSize = Tokens.fontSm)
                        Text(o.pickupAddress ?: "-", color = Tokens.White, fontSize = Tokens.fontMd)
                        Text(o.dropoffAddress ?: "-", color = Tokens.TextDim, fontSize = Tokens.fontSm)
                    }
                    Text(o.statusLabel ?: o.status, color = Tokens.TextDim, fontSize = Tokens.fontSm)
                }
            }
        }
    }
}

/* --------------------------------------------------- complaints / customer service */
@Composable
fun ComplaintScreen(onBack: () -> Unit, types: List<String>, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("投訴", onBack)
        SectionList(types.map { it to null }, onPick)
    }
}

@Composable
fun CustomerServiceScreen(onBack: () -> Unit, hotlines: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("客服", onBack)
        SectionList(hotlines)
    }
}

/* --------------------------------------------------------------- payment */
@Composable
fun PaymentMethodsScreen(onBack: () -> Unit, methods: List<Pair<String, String>>) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("支付方式", onBack)
        SectionList(methods)
    }
}

/* ------------------------------------------------- phone verify / sign up */
@Composable
fun PhoneVerifyScreen(onBack: () -> Unit, onSubmit: (String) -> Unit) {
    var tel by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Panel)
            .padding(Tokens.spaceMd)
    ) {
        HeaderBar("電話驗證", onBack)
        Spacer(Modifier.height(Tokens.spaceMd))
        OutlinedTextField(tel, { tel = it }, label = { Text("電話號碼") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Tokens.spaceSm))
        OutlinedTextField(code, { code = it }, label = { Text("驗證碼") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Tokens.spaceMd))
        Button(onClick = { onSubmit(tel) }, modifier = Modifier.fillMaxWidth().height(Tokens.buttonHeight), shape = Tokens.corner) {
            Text("送出驗證碼")
        }
    }
}

@Composable
fun SignUpScreen(onBack: () -> Unit, onSubmit: (String, String) -> Unit) {
    var tel by remember { mutableStateOf("") }
    var pwd by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Panel)
            .padding(Tokens.spaceMd)
    ) {
        HeaderBar("註冊", onBack)
        Spacer(Modifier.height(Tokens.spaceMd))
        OutlinedTextField(tel, { tel = it }, label = { Text("電話號碼") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Tokens.spaceSm))
        OutlinedTextField(
            pwd, { pwd = it }, label = { Text("密碼") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Tokens.spaceMd))
        Button(
            onClick = { onSubmit(tel, pwd) },
            enabled = tel.isNotBlank() && pwd.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(Tokens.buttonHeight), shape = Tokens.corner,
        ) { Text("註冊") }
    }
}

/* --------------------------------------------------- idle / black screens
 * reference: main_layout.xml > black_layout (offline) and screen_saver_layout
 */
@Composable
fun IdleBlackScreen(phone: String, onExit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Tokens.Panel)
            .clickable { onExit() }
    ) {
        Text("點按任何位置離開", color = Tokens.White, fontSize = Tokens.fontSm, modifier = Modifier.align(Alignment.TopStart).padding(Tokens.spaceMd))
        Text(phone, color = Tokens.White, fontSize = Tokens.fontXl, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
        Text("透過 WhatsApp 聯絡乘客", color = Tokens.Gold, fontSize = Tokens.fontMd, modifier = Modifier.align(Alignment.BottomCenter).padding(Tokens.spaceLg))
    }
}


/* --------------------------------------------------------------- more menu */
@Composable
fun MoreMenuScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Tokens.Panel)) {
        HeaderBar("更多", onBack)
        SectionList(
            listOf(
                "設定" to null,
                "司機證件" to null,
                "車輛型號" to null,
                "咪錶計算機" to null,
                "收入紀錄" to null,
                "行程紀錄" to null,
                "支付方式" to null,
                "投訴" to null,
                "公告" to null,
                "客服" to null,
                "權限設定" to null,
                "字體大小" to null,
                "無障礙服務" to null,
            ),
            onOpen,
        )
    }
}
