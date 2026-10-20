package com.newflytaxi.driver.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Design tokens rebuilt from the reference app's resources
 * (see product/spec/reference-data/design-tokens.json — extracted by reverse
 * engineering res/values + resources.arsc). Values are the same *scale* as the
 * reference; the palette is re-declared here under our own names.
 */
object Tokens {
    val Gold = Color(0xFFFCA62A)        // reference: order_gold #BFFCA62A
    val GoldDim = Color(0xFF73BA27)     // reference: order_green #BF73BA27
    val Panel = Color(0xFF000000)       // reference: black
    val Sheet = Color(0xFF1C1C1E)
    val White = Color(0xFFFFFFFF)
    val TextDim = Color(0xFF9A9A9E)
    val Danger = Color(0xFFD32F2F)

    val spaceXs = 4.dp
    val spaceSm = 8.dp
    val spaceMd = 16.dp
    val spaceLg = 24.dp

    val fontSm = 20.sp
    val fontMd = 24.sp
    val fontLg = 28.sp
    val fontXl = 32.sp

    val buttonHeight = 48.dp
    val swipeButton = 78.dp
    val swipePadding = 18.dp
    val corner = RoundedCornerShape(12.dp)
}

@Composable
fun NewFlyTaxiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Tokens.Gold,
            secondary = Tokens.GoldDim,
            background = Tokens.Panel,
            surface = Tokens.Sheet,
            onPrimary = Tokens.White,
            onBackground = Tokens.White,
            onSurface = Tokens.White,
        ),
        content = content,
    )
}

/**
 * Swipe-to-confirm bar, rebuilt from the reference app's custom
 * com.flytaxi.layout.swipeBar.SwipeBar view (see research/ui/layouts).
 */
@Composable
fun SwipeBar(
    label: String,
    enabled: Boolean = true,
    onConfirm: () -> Unit,
) {
    var offset by remember { mutableFloatStateOf(0f) }
    val widthPx = with(LocalDensity.current) { 320.dp.toPx() }
    val buttonPx = with(LocalDensity.current) { Tokens.swipeButton.toPx() }
    val threshold = buttonPx * 1.6f

    Box(
        Modifier
            .fillMaxWidth()
            .height(Tokens.swipeButton)
            .clip(Tokens.corner)
            .background(if (enabled) Tokens.Gold else Tokens.TextDim),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            modifier = Modifier.align(Alignment.Center),
            color = Tokens.White,
            fontSize = Tokens.fontLg,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .padding(start = Tokens.swipePadding)
                .size(Tokens.swipeButton)
                .clip(RoundedCornerShape(20.dp))
                .background(Tokens.White)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset >= threshold) onConfirm()
                            offset = 0f
                        },
                        onDragCancel = { offset = 0f },
                        onHorizontalDrag = { _, dx -> offset = (offset + dx).coerceIn(0f, widthPx) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("→", color = Tokens.Panel, fontSize = Tokens.fontLg)
        }
    }
}
