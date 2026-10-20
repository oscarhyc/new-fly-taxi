package com.newflytaxi.driver.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import com.newflytaxi.driver.data.Order

/**
 * Accept-order screen, rebuilt to match the reference app's
 * res/layout/accept_order_main_layout.xml (structure recovered by reverse
 * engineering the binary layout — see research/ui/layouts/accept_order_main_layout.xml.txt):
 *
 *   full-screen black panel
 *     ├ top bar: back button (left) + countdown timer (right, large font)
 *     ├ progress bar (countdown)
 *     ├ scrollable body: advance time / first option / location / amount /
 *     │                  option list / remark / debug line
 *     └ bottom: "arrived" ghost button + gold swipe-to-confirm bar
 */
@Composable
fun AcceptOrderScreen(
    order: Order,
    countdownSeconds: Int,
    onArrive: () -> Unit,
    onSwipeAccept: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Tokens.Panel)
    ) {
        // ---- top bar: back + countdown
        Row(
            Modifier
                .fillMaxWidth()
                .padding(Tokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("‹", color = Tokens.White, fontSize = Tokens.fontXl)
            Spacer(Modifier.weight(1f))
            Text(
                text = if (countdownSeconds > 0) "$countdownSeconds s" else "0 s",
                color = Tokens.Gold,
                fontSize = Tokens.fontLg,
                fontWeight = FontWeight.Bold,
            )
        }
        // ---- countdown progress
        if (countdownSeconds > 0) {
            LinearProgressIndicator(
                progress = { countdownSeconds / 20f },
                modifier = Modifier.fillMaxWidth(),
                color = Tokens.Gold,
                trackColor = Tokens.Sheet,
            )
        }

        // ---- body
        LazyColumn(
            Modifier
                .weight(1f)
                .background(Tokens.Panel),
            contentPadding = PaddingValues(Tokens.spaceSm),
        ) {
            item { SectionCard("預約時間", listOf(order.dpu ?: "-")) }
            item { SectionCard("上車", listOf(order.pickupAddress ?: "-")) }
            item { SectionCard("落車", listOf(order.dropoffAddress ?: "-")) }
            item { SectionCard("金額", listOf(order.fare?.amountFare?.let { "HK$$it" } ?: "-")) }
            item {
                Text(
                    "備註",
                    color = Tokens.TextDim,
                    fontSize = Tokens.fontSm,
                    modifier = Modifier.padding(Tokens.spaceSm),
                )
            }
        }

        // ---- bottom actions
        Column(Modifier.padding(Tokens.spaceSm)) {
            OutlinedButton(
                onClick = onArrive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Tokens.buttonHeight),
                shape = Tokens.corner,
            ) { Text("已到接載點", fontSize = Tokens.fontMd) }
            Spacer(Modifier.height(Tokens.spaceSm))
            SwipeBar(label = "滑動接單", enabled = countdownSeconds > 0, onConfirm = onSwipeAccept)
        }
    }
}

@Composable
private fun SectionCard(title: String, lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Tokens.spaceXs)
            .clip(RoundedCornerShape(10.dp))
            .background(Tokens.Sheet)
            .padding(Tokens.spaceMd)
    ) {
        Text(title, color = Tokens.Gold, fontSize = Tokens.fontSm)
        Spacer(Modifier.height(Tokens.spaceXs))
        lines.forEach { Text(it, color = Tokens.White, fontSize = Tokens.fontMd) }
    }
}
