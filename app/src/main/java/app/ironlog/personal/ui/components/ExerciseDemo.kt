package app.ironlog.personal.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Bundled demo frames from free-exercise-db (public domain): assets/exercises/<id>/0.webp is the
 * start position and 1.webp the end position. Decoded bitmaps are cached by path and sample size.
 */
object ExerciseMedia {
    private const val FRAMES = 2
    private val cache =
        object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
        }

    fun framePath(exerciseId: String, index: Int) = "exercises/$exerciseId/$index.webp"

    /** Loads up to two frames; returns an empty list when the exercise has no bundled media. */
    suspend fun frames(context: Context, exerciseId: String, sampleSize: Int = 1, count: Int = FRAMES): List<ImageBitmap> =
        withContext(Dispatchers.IO) {
            (0 until count).mapNotNull { index -> load(context, framePath(exerciseId, index), sampleSize) }
        }

    private fun load(context: Context, path: String, sampleSize: Int): ImageBitmap? {
        val key = "$path@$sampleSize"
        cache.get(key)?.let { return it }
        return runCatching {
                context.assets.open(path).use { stream ->
                    BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
                }
            }
            .getOrNull()
            ?.asImageBitmap()
            ?.also { cache.put(key, it) }
    }
}

/**
 * Looping movement demo: alternates the start and end frames with a crossfade so the user sees the
 * range of motion. Tap to pause on a position.
 */
@Composable
fun ExerciseDemo(exerciseId: String, name: String, modifier: Modifier = Modifier, intervalMs: Long = 1_100) {
    val context = LocalContext.current
    val frames by produceState<List<ImageBitmap>?>(null, exerciseId) { value = ExerciseMedia.frames(context, exerciseId) }
    var playing by remember { mutableStateOf(true) }
    var index by remember(exerciseId) { mutableIntStateOf(0) }
    val loaded = frames
    LaunchedEffect(exerciseId, playing, loaded?.size) {
        if (loaded == null || loaded.size < 2) return@LaunchedEffect
        while (playing) {
            delay(intervalMs)
            index = (index + 1) % loaded.size
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = (loaded?.size ?: 0) > 1) { playing = !playing },
        contentAlignment = Alignment.Center,
    ) {
        when {
            loaded == null -> Unit
            loaded.isEmpty() -> Monogram(name, size = 72.dp)
            else ->
                Crossfade(targetState = index.coerceAtMost(loaded.lastIndex), animationSpec = tween(350), label = "demo") { frame ->
                    Image(
                        loaded[frame],
                        contentDescription = "$name demonstration, ${if (frame == 0) "start" else "end"} position",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
        }
        if ((loaded?.size ?: 0) > 1) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        if (index == 0) "START" else "END",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
                Box(Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(5.dp)) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playing) "Pause demo" else "Play demo",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** Small still of the start position, used in lists. Falls back to a monogram. */
@Composable
fun ExerciseThumb(exerciseId: String, name: String, size: Dp = 48.dp) {
    val context = LocalContext.current
    val frames by produceState<List<ImageBitmap>?>(null, exerciseId) {
        value = ExerciseMedia.frames(context, exerciseId, sampleSize = 4, count = 1)
    }
    val first = frames?.firstOrNull()
    if (first == null) {
        Monogram(name, size = size)
    } else {
        Image(
            first,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(MaterialTheme.shapes.small),
        )
    }
}
