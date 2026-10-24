package hu.egyszeru.galeria.ui

import android.app.Activity
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import hu.egyszeru.galeria.data.MediaItem
import hu.egyszeru.galeria.util.formatDuration
import hu.egyszeru.galeria.util.fullDate
import hu.egyszeru.galeria.util.shareMedia
import kotlinx.coroutines.delay
import kotlin.math.abs

private sealed interface ViewerDialog {
    data class Info(val item: MediaItem) : ViewerDialog
    data class Rename(val item: MediaItem) : ViewerDialog
    data class AddTo(val item: MediaItem) : ViewerDialog
}

@Composable
fun ViewerScreen(vm: GalleryViewModel, albumKey: String, startId: Long, onBack: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val items = remember(data, albumKey) { data.itemsFor(albumKey) }

    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        if (data.loaded) LaunchedEffect(Unit) { onBack() }
        return
    }

    val context = LocalContext.current
    val isTrash = albumKey == KEY_TRASH
    val startIndex = remember { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = startIndex) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    var chrome by rememberSaveable { mutableStateOf(true) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<ViewerDialog?>(null) }

    LaunchedEffect(pager.currentPage) { playingId = null }
    SystemBarsEffect(visible = chrome)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pager,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            when {
                !item.isVideo -> ZoomableImage(item, onTap = { chrome = !chrome })
                playingId == item.id -> VideoPlayer(item, showControls = chrome, onTap = { chrome = !chrome })
                else -> VideoPoster(item, onTap = { chrome = !chrome }, onPlay = { playingId = item.id })
            }
        }

        // Felső sáv
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        current.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        fullDate(current.dateTaken), color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Text(
                    "${pager.currentPage + 1} / ${items.size}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Alsó műveletsáv
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .navigationBarsPadding()
                        .padding(top = 24.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    if (isTrash) {
                        ActionItem(Icons.Outlined.Restore, "Visszaállítás") { vm.restore(listOf(current)) }
                        ActionItem(Icons.Outlined.Info, "Részletek") { dialog = ViewerDialog.Info(current) }
                        ActionItem(Icons.Outlined.DeleteForever, "Végleges törlés") { vm.deleteForever(listOf(current)) }
                    } else {
                        val fav = current.id in data.favorites
                        ActionItem(Icons.Outlined.Share, "Megosztás") { shareMedia(context, listOf(current)) }
                        ActionItem(
                            if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            "Kedvenc",
                            tint = if (fav) Color(0xFFFF5A79) else Color.White,
                        ) { vm.setFavorite(listOf(current.id), !fav) }
                        ActionItem(Icons.Outlined.LibraryAdd, "Albumba") { dialog = ViewerDialog.AddTo(current) }
                        ActionItem(Icons.Outlined.Info, "Részletek") { dialog = ViewerDialog.Info(current) }
                        ActionItem(Icons.Outlined.Delete, "Törlés") { vm.moveToTrash(listOf(current)) }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is ViewerDialog.Info -> InfoDialog(
            item = d.item,
            onRename = if (isTrash) null else ({ dialog = ViewerDialog.Rename(d.item) }),
            onDismiss = { dialog = null },
        )
        is ViewerDialog.Rename -> NameDialog(
            title = "Átnevezés",
            initial = d.item.baseName,
            suffix = d.item.extension,
            onConfirm = { vm.rename(d.item, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is ViewerDialog.AddTo -> AddToAlbumDialog(
            albums = data.userAlbumInfos(),
            onPick = { id -> vm.addToAlbum(id, listOf(d.item.id)); dialog = null },
            onCreate = { name -> vm.addToAlbum(vm.createAlbum(name), listOf(d.item.id)); dialog = null },
            onDismiss = { dialog = null },
        )
    }
}

/** Fekete háttérhez világos rendszerikonok; a vezérlők elrejtésekor a rendszersávok is eltűnnek. */
@Composable
private fun SystemBarsEffect(visible: Boolean) {
    val view = LocalView.current
    val activity = view.context as? Activity ?: return
    val darkTheme = isSystemInDarkTheme()
    val controller = remember(activity) { WindowCompat.getInsetsController(activity.window, view) }

    DisposableEffect(controller) {
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            controller.show(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/** Kép csippentéses nagyítással, dupla koppintásos zoommal. Nagyítás nélkül a lapozás szabadon működik. */
@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val tap by rememberUpdatedState(onTap)

    fun clamp(o: Offset, s: Float): Offset {
        val maxX = boxSize.width * (s - 1f) / 2f
        val maxY = boxSize.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val request = remember(item.uri) {
        ImageRequest.Builder(context)
            .data(item.uri)
            .size(2560)                  // éles nagyításhoz, de memóriakímélően
            .precision(Precision.INEXACT)
            .build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { tap() },
                    onDoubleTap = { p ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            val s = 2.5f
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            scale = s
                            offset = clamp((center - p) * (s - 1f), s)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers > 1 || scale > 1f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val newScale = (scale * zoom).coerceIn(1f, 6f)
                            val centroid = event.calculateCentroid(useCurrent = true)
                            val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                            val d = if (centroid.isSpecified) centroid - center else Offset.Zero
                            val raw = d - (d - offset) * (newScale / scale) + pan
                            val clamped = clamp(raw, newScale)
                            // Ha a kép széléhez értünk, a vízszintes húzás lapoz.
                            val atEdge = pointers == 1 && abs(pan.x) > abs(pan.y) && abs(clamped.x - offset.x) < 0.5f
                            scale = newScale
                            offset = if (newScale == 1f) Offset.Zero else clamped
                            if (!atEdge) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = request,
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}

@Composable
private fun VideoPoster(item: MediaItem, onTap: () -> Unit, onPlay: () -> Unit) {
    val tap by rememberUpdatedState(onTap)
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { tap() }) },
        contentAlignment = Alignment.Center,
    ) {
        Thumbnail(item, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, background = Color.Transparent)
        Box(
            Modifier.size(76.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.PlayArrow, "Lejátszás", Modifier.size(44.dp), tint = Color.White)
        }
    }
}

@Composable
private fun VideoPlayer(item: MediaItem, showControls: Boolean, onTap: () -> Unit) {
    val context = LocalContext.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(item.duration.coerceAtLeast(1L)) }
    val tap by rememberUpdatedState(onTap)

    LaunchedEffect(playing, videoView) {
        val v = videoView ?: return@LaunchedEffect
        while (playing) {
            position = v.currentPosition.toLong()
            delay(200)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        videoView?.pause()
        playing = false
    }
    DisposableEffect(Unit) { onDispose { videoView?.stopPlayback() } }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(item.uri)
                setOnPreparedListener { mp ->
                    duration = mp.duration.toLong().coerceAtLeast(1L)
                    start()
                }
                setOnCompletionListener {
                    playing = false
                    position = duration
                }
                setOnErrorListener { _, _, _ ->
                    playing = false
                    Toast.makeText(context, "A videó nem játszható le", Toast.LENGTH_SHORT).show()
                    true
                }
                videoView = this
            }
        })

        // Átlátszó réteg a koppintásokhoz (a vízszintes húzást a lapozó kapja meg)
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = { tap() }) })

        if (showControls) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)).clickable {
                    val v = videoView ?: return@clickable
                    if (playing) {
                        v.pause()
                    } else {
                        if (position >= duration) v.seekTo(0)
                        v.start()
                    }
                    playing = !playing
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (playing) "Szünet" else "Lejátszás",
                    Modifier.size(40.dp),
                    tint = Color.White,
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 84.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(position), color = Color.White, style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = (position.toFloat() / duration).coerceIn(0f, 1f),
                    onValueChange = { f ->
                        position = (f * duration).toLong()
                        videoView?.seekTo(position.toInt())
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(duration), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
