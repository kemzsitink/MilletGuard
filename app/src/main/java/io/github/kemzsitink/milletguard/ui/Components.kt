package io.github.kemzsitink.milletguard.ui

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Horizontal page gutter shared by every screen. */
val PageGutter = 16.dp

/** Content padding for a screen's LazyColumn: gutter on the sides, breathing room at the end. */
fun pagePadding(extraBottom: Dp = 24.dp) =
    PaddingValues(start = PageGutter, end = PageGutter, top = 8.dp, bottom = extraBottom)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

fun LazyListScope.gap(height: Dp = 12.dp) = item { Spacer(Modifier.size(height)) }

/**
 * Decoded launcher icons. Lazy rows leave composition while scrolling, so without this every
 * icon would be decoded again and flash its placeholder each time it scrolls back into view.
 */
private val iconCache = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

/** Launcher icon of another app, loaded off the main thread. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = "$packageName@$px"
    val bitmap by produceState(iconCache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName).toBitmap(px, px).asImageBitmap()
                    .also { iconCache.put(key, it) }
            } catch (_: Throwable) {
                null
            }
        }
    }
    Box(
        Modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(if (bitmap == null) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
    }
}

/**
 * One container color for every row of a segmented group. The default colors only fill rows
 * in the checked/selected state, which made mixed groups look broken.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun groupColors(): ListItemColors {
    val container = MaterialTheme.colorScheme.surfaceContainer
    return ListItemDefaults.segmentedColors(
        containerColor = container,
        selectedContainerColor = container,
        disabledContainerColor = container,
    )
}
