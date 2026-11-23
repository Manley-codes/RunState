package com.runstate.mobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.runstate.mobile.data.local.ShoePairEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale
import java.math.BigDecimal
import java.math.MathContext

internal data class ShoeCatalogChoice(val brand: String, val model: String, val imageAsset: String?)

/** Bundled, sourced model names and photos. Browsing never sends a runner's shoe choices online. */
internal object ShoeCatalog {
    val brands = listOf("Nike", "Adidas", "Altra", "ASICS", "Brooks", "Dynafit", "HOKA",
        "Inov-8", "La Sportiva", "Merrell", "Mizuno", "New Balance", "Newton", "On", "Puma",
        "Reebok", "Salomon", "Saucony", "Scott", "Skechers", "The North Face", "Topo Athletic",
        "Under Armour", "Vibram", "Vivobarefoot", "Zoot")

    fun brandKey(value: String): String = when (val key = value.trim().lowercase(Locale.ROOT)) {
        "hoka one one" -> "hoka"
        "on cloud", "on running" -> "on"
        "topo" -> "topo athletic"
        "inov8", "inov 8" -> "inov-8"
        else -> key
    }

    fun brandMatches(brand: String, query: String): Boolean =
        brand.contains(query, true) || when (brandKey(brand)) {
            "hoka" -> "Hoka One One".contains(query, true)
            "on" -> "On Cloud".contains(query, true) || "On Running".contains(query, true)
            "topo athletic" -> "Topo".contains(query, true)
            "inov-8" -> "Inov8".contains(query, true) || "Inov 8".contains(query, true)
            else -> false
        }

    @Volatile private var saved: List<ShoeCatalogChoice>? = null
    fun load(context: Context): List<ShoeCatalogChoice> = saved ?: synchronized(this) {
        saved ?: context.assets.open("shoes/catalog.json").bufferedReader().use { reader ->
            val rows = JSONArray(reader.readText())
            val availablePhotos = context.assets.list("shoes/photos").orEmpty().toSet()
            List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                val image = row.optString("imageAsset").takeIf { it.isNotBlank() && it != "null" }
                require(image == null || image.matches(Regex("shoes/photos/[a-z0-9_-]+\\.img")))
                ShoeCatalogChoice(row.getString("brand"), row.getString("model"),
                    image?.takeIf { it.substringAfterLast('/') in availablePhotos })
            }.also { saved = it }
        }
    }
}

internal fun shoeGoalMeters(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
    ?.times(1609.344)?.takeIf(ShoePairEntity::validGoal)

internal fun shoeGoalInput(meters: Double?): String = meters?.let {
    BigDecimal.valueOf(it).divide(BigDecimal.valueOf(1609.344), MathContext(12)).stripTrailingZeros().toPlainString()
}.orEmpty()

// Small offline thumbnails are decoded away from scrolling, with a bounded shared cache.
private val shoePhotos = LruCache<String, ImageBitmap>(32)

@Composable
internal fun ShoeModelPhoto(asset: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val bitmap by produceState<ImageBitmap?>(initialValue = asset?.let(shoePhotos::get), asset) {
        value = if (asset == null) null else shoePhotos.get(asset) ?: withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, bounds) }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= 256) inSampleSize *= 2
                }
                context.assets.open(asset).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            }.getOrNull()?.also { shoePhotos.put(asset, it) }
        }
    }
    Box(modifier.size(76.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F5F5))) {
        bitmap?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.size(76.dp).padding(5.dp).testTag("shoePhoto:$asset"))
        }
    }
}
