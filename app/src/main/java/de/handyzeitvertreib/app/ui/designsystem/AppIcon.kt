package de.handyzeitvertreib.app.ui.designsystem

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object AppIconCache {
    private val cache = LruCache<String, ImageBitmap>(80)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun load(
        context: Context,
        packageName: String,
        sizePx: Int,
    ): ImageBitmap? {
        val key = "$packageName@$sizePx"
        cache.get(key)?.let { return it }
        val drawable = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull() ?: return null
        val bitmap = runCatching { drawable.toBitmap(sizePx, sizePx).asImageBitmap() }.getOrNull() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }
}

/** Icon of an installed app, or a lettered placeholder for missing or uninstalled apps. */
@Composable
fun AppIcon(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState(AppIconCache.get("$packageName@$sizePx"), packageName, sizePx) {
        if (value == null) value = withContext(Dispatchers.IO) { AppIconCache.load(context, packageName, sizePx) }
    }
    val current = icon
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(HzvShapes.inner)
                .background(HzvTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = HzvTheme.colors.accent)
        }
    }
}
