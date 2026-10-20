package com.newflytaxi.driver.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Generic layout renderer.
 *
 * The reference app ships 216 compiled layouts; many of its screens are inflated from
 * XML and then filled from code. We keep the *structure* as data (assets/ui-spec.json,
 * produced by ui_json.py from the binary layouts) and render it with Compose, so the
 * long tail of screens does not need 200 hand-written composables.
 *
 * Supported view classes: LinearLayout / RelativeLayout / FrameLayout / ScrollView /
 * TextView / ImageView / Button / EditText / CheckBox / Switch / ProgressBar / RecyclerView.
 */
object UiSpec {
    private var cached: JSONObject? = null

    fun load(ctx: Context): JSONObject {
        cached?.let { return it }
        val text = ctx.assets.open("ui-spec.json").bufferedReader().use { it.readText() }
        return JSONObject(text).also { cached = it }
    }

    fun layout(ctx: Context, name: String): JSONArray? {
        val all = load(ctx).optJSONObject("layouts") ?: return null
        return all.optJSONObject(name)?.optJSONArray("tree")
    }
}

@Composable
fun SpecScreen(name: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val spec = remember(name) { UiSpec.layout(ctx, name) }
    Column(
        modifier
            .fillMaxSize()
            .background(Tokens.Panel)
            .verticalScroll(rememberScrollState())
    ) {
        spec?.forEach { node -> SpecNode(node) }
    }
}

@Composable
private fun SpecNode(node: JSONObject) {
    val tag = node.optString("tag")
    val a = node.optJSONObject("attrs") ?: JSONObject()
    if (a.optInt("visibility", 0) == 2) return          // GONE
    when (tag.substringAfterLast('.')) {
        "LinearLayout" -> {
            val horizontal = a.optInt("orientation", 1) == 0
            val col = if (horizontal) Row(Modifier.fillMaxWidth()) else Column(Modifier.fillMaxWidth())
            col {
                node.optJSONArray("children")?.forEach { SpecNode(it) }
            }
        }
        "RelativeLayout", "FrameLayout" -> Column(Modifier.fillMaxWidth()) {
            node.optJSONArray("children")?.forEach { SpecNode(it) }
        }
        "ScrollView" -> Column(Modifier.fillMaxWidth()) {
            node.optJSONArray("children")?.forEach { SpecNode(it) }
        }
        "TextView" -> Text(
            text = a.optString("text").ifBlank { a.optString("hint") },
            color = parseColor(a.optString("textColor")),
            fontSize = parseSp(a.optString("textSize")),
            fontWeight = if (a.optBoolean("bold")) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(parseDp(a.optString("padding"), 8)),
        )
        "Button" -> Text(
            text = a.optString("text"),
            color = Tokens.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Tokens.Gold)
                .padding(12.dp),
        )
        "ImageView" -> Box(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(Tokens.Sheet)
        )
        "EditText" -> Text(
            text = a.optString("hint").ifBlank { "輸入" },
            color = Tokens.TextDim,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Tokens.Sheet)
                .padding(12.dp),
        )
        "ProgressBar" -> Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Tokens.Gold)
        )
        "RecyclerView" -> Text(
            "• 列表項目（由 code 產生，見 MoreScreens）",
            color = Tokens.TextDim,
            fontSize = Tokens.fontSm,
            modifier = Modifier.padding(8.dp),
        )
        else -> node.optJSONArray("children")?.forEach { SpecNode(it) }
    }
}

internal fun parseColor(v: String?): Color {
    if (v.isNullOrBlank()) return Tokens.White
    return try {
        val hex = v.removePrefix("#")
        val argb = if (hex.length == 6) "FF$hex" else hex
        Color(android.graphics.Color.parseColor("#$argb"))
    } catch (e: Exception) {
        Tokens.White
    }
}

internal fun parseSp(v: String?): androidx.compose.ui.unit.TextUnit {
    val n = v?.replace("sp", "")?.toFloatOrNull() ?: return Tokens.fontMd
    return n.sp
}

internal fun parseDp(v: String?, fallback: Int): androidx.compose.ui.unit.Dp {
    val n = v?.replace("dp", "")?.toFloatOrNull() ?: return fallback.dp
    return n.dp
}
