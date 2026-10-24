package hu.egyszeru.galeria.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.egyszeru.galeria.data.MediaItem
import hu.egyszeru.galeria.util.dayLabel
import hu.egyszeru.galeria.util.formatDuration
import hu.egyszeru.galeria.util.groupByDay
import hu.egyszeru.galeria.util.shareMedia

private val SelectionSaver = Saver<Set<Long>, LongArray>(
    save = { it.toLongArray() },
    restore = { it.toSet() },
)

private sealed interface AlbumDialog {
    data object AddTo : AlbumDialog
    data class Rename(val item: MediaItem) : AlbumDialog
    data class Info(val item: MediaItem) : AlbumDialog
    data object RenameAlbum : AlbumDialog
    data object DeleteAlbum : AlbumDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    vm: GalleryViewModel,
    albumKey: String,
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val items = remember(data, albumKey) { data.itemsFor(albumKey) }
    val sections = remember(items) { groupByDay(items) }
    val title = data.titleFor(albumKey)
    val userAlbum = data.userAlbum(albumKey)
    val isTrash = albumKey == KEY_TRASH
    val context = LocalContext.current

    if (data.loaded && title == null) {
        // pl. az albumot közben törölték
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var selected by rememberSaveable(stateSaver = SelectionSaver) { mutableStateOf(emptySet<Long>()) }
    val inSelection = selected.isNotEmpty()
    val selectedItems = remember(selected, items) { items.filter { it.id in selected } }
    var dialog by remember { mutableStateOf<AlbumDialog?>(null) }

    fun toggle(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    BackHandler(enabled = inSelection) { selected = emptySet() }
    LaunchedEffect(Unit) { vm.selectionCleared.collect { selected = emptySet() } }
    LaunchedEffect(items) {
        // Eltűnt elemek kivétele a kijelölésből
        if (selected.isNotEmpty()) {
            val ids = items.mapTo(HashSet()) { it.id }
            val kept = selected.filterTo(HashSet()) { it in ids }
            if (kept.size != selected.size) selected = kept
        }
    }

    Scaffold(
        topBar = {
            if (inSelection) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) {
                            Icon(Icons.Outlined.Close, "Kijelölés megszüntetése")
                        }
                    },
                    title = { Text("${selected.size} kijelölve") },
                    actions = {
                        IconButton(onClick = {
                            selected = if (selected.size == items.size) emptySet() else items.mapTo(HashSet()) { it.id }
                        }) { Icon(Icons.Outlined.SelectAll, "Összes kijelölése") }
                    },
                )
            } else {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Vissza") }
                    },
                    title = {
                        Column {
                            Text(title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${items.size} elem", style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.setColumns(if (columns >= 5) 3 else columns + 1) }) {
                            Icon(Icons.Outlined.GridView, "Rácsméret")
                        }
                        if (userAlbum != null || (isTrash && items.isNotEmpty())) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Továbbiak") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    if (userAlbum != null) {
                                        DropdownMenuItem(
                                            text = { Text("Album átnevezése") },
                                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                            onClick = { menu = false; dialog = AlbumDialog.RenameAlbum },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Album törlése") },
                                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                            onClick = { menu = false; dialog = AlbumDialog.DeleteAlbum },
                                        )
                                    }
                                    if (isTrash) {
                                        DropdownMenuItem(
                                            text = { Text("Lomtár ürítése") },
                                            leadingIcon = { Icon(Icons.Outlined.DeleteSweep, null) },
                                            onClick = { menu = false; vm.deleteForever(items) },
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = inSelection,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        if (isTrash) {
                            ActionItem(Icons.Outlined.Restore, "Visszaállítás") { vm.restore(selectedItems) }
                            ActionItem(Icons.Outlined.DeleteForever, "Végleges törlés") { vm.deleteForever(selectedItems) }
                        } else {
                            val allFav = selectedItems.isNotEmpty() && selectedItems.all { it.id in data.favorites }
                            ActionItem(Icons.Outlined.Share, "Megosztás") { shareMedia(context, selectedItems) }
                            ActionItem(
                                if (allFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                if (allFav) "Nem kedvenc" else "Kedvenc",
                            ) {
                                vm.setFavorite(selected, !allFav)
                                selected = emptySet()
                            }
                            ActionItem(Icons.Outlined.LibraryAdd, "Albumba") { dialog = AlbumDialog.AddTo }
                            ActionItem(Icons.Outlined.Delete, "Törlés") { vm.moveToTrash(selectedItems) }
                            Box {
                                var more by remember { mutableStateOf(false) }
                                ActionItem(Icons.Outlined.MoreVert, "Több") { more = true }
                                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Átnevezés") },
                                        leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                        enabled = selectedItems.size == 1,
                                        onClick = { more = false; dialog = AlbumDialog.Rename(selectedItems.first()) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Részletek") },
                                        leadingIcon = { Icon(Icons.Outlined.Info, null) },
                                        enabled = selectedItems.size == 1,
                                        onClick = { more = false; dialog = AlbumDialog.Info(selectedItems.first()) },
                                    )
                                    if (userAlbum != null) {
                                        DropdownMenuItem(
                                            text = { Text("Eltávolítás az albumból") },
                                            leadingIcon = { Icon(Icons.Outlined.RemoveCircleOutline, null) },
                                            onClick = { more = false; vm.removeFromAlbum(userAlbum.id, selected) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { pad ->
        when {
            !data.loaded -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            items.isEmpty() -> EmptyState(
                icon = if (isTrash) Icons.Outlined.Delete else Icons.Outlined.PhotoLibrary,
                title = if (isTrash) "A lomtár üres" else "Nincs itt még semmi",
                subtitle = when {
                    userAlbum != null -> "Jelölj ki képeket bármelyik albumban, és a „Albumba” gombbal tedd ide őket."
                    albumKey == KEY_FAVORITES -> "A szív ikonnal jelölheted meg a kedvenc képeidet."
                    else -> null
                },
                padding = pad,
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = pad.calculateTopPadding(),
                    bottom = pad.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (isTrash) {
                    item(key = "trash_info", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "A lomtárban lévő elemek 30 nap után automatikusan törlődnek.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                sections.forEach { section ->
                    item(key = "day_${section.date}", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                        val allSelected = inSelection && section.items.all { it.id in selected }
                        DateHeader(
                            label = dayLabel(section.date),
                            inSelection = inSelection,
                            allSelected = allSelected,
                        ) {
                            val ids = section.items.map { it.id }
                            selected = if (allSelected) selected - ids.toSet() else selected + ids
                        }
                    }
                    items(section.items, key = { it.id }, contentType = { "media" }) { item ->
                        MediaCell(
                            item = item,
                            favorite = item.id in data.favorites,
                            selected = item.id in selected,
                            inSelection = inSelection,
                            onClick = { if (inSelection) toggle(item.id) else onOpenItem(item.id) },
                            onLongClick = { toggle(item.id) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        AlbumDialog.AddTo -> AddToAlbumDialog(
            albums = data.userAlbumInfos(),
            onPick = { id -> vm.addToAlbum(id, selected); dialog = null },
            onCreate = { name -> vm.addToAlbum(vm.createAlbum(name), selected); dialog = null },
            onDismiss = { dialog = null },
        )
        is AlbumDialog.Rename -> NameDialog(
            title = "Átnevezés",
            initial = d.item.baseName,
            suffix = d.item.extension,
            onConfirm = { vm.rename(d.item, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is AlbumDialog.Info -> InfoDialog(
            item = d.item,
            onRename = { dialog = AlbumDialog.Rename(d.item) },
            onDismiss = { dialog = null },
        )
        AlbumDialog.RenameAlbum -> if (userAlbum != null) NameDialog(
            title = "Album átnevezése",
            initial = userAlbum.name,
            onConfirm = { vm.renameAlbum(userAlbum.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        AlbumDialog.DeleteAlbum -> if (userAlbum != null) ConfirmDialog(
            title = "Törlöd az albumot?",
            text = "Az album megszűnik, de a benne lévő képek és videók megmaradnak a telefonon.",
            confirmLabel = "Törlés",
            onConfirm = { dialog = null; vm.deleteAlbum(userAlbum.id) },
            onDismiss = { dialog = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DateHeader(label: String, inSelection: Boolean, allSelected: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = { if (inSelection) onToggle() }, onLongClick = onToggle)
            .padding(start = 16.dp, end = 12.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        if (inSelection) {
            Icon(
                if (allSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (allSelected) "Nap kijelölésének megszüntetése" else "Egész nap kijelölése",
                tint = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCell(
    item: MediaItem,
    favorite: Boolean,
    selected: Boolean,
    inSelection: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val inset by animateDpAsState(if (selected) 10.dp else 0.dp, label = "selectionInset")
    val corner by animateDpAsState(if (selected) 12.dp else 0.dp, label = "selectionCorner")

    Box(
        Modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        Thumbnail(item, Modifier.fillMaxSize().padding(inset).clip(RoundedCornerShape(corner)))

        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(inset + 4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PlayArrow, null, Modifier.size(14.dp), tint = Color.White)
                Spacer(Modifier.width(2.dp))
                Text(formatDuration(item.duration), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (favorite) {
            Icon(
                Icons.Filled.Favorite, "Kedvenc",
                Modifier.align(Alignment.BottomStart).padding(inset + 5.dp).size(16.dp),
                tint = Color.White,
            )
        }
        if (inSelection) {
            Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                if (selected) {
                    Icon(
                        Icons.Filled.CheckCircle, "Kijelölve",
                        Modifier.size(24.dp).background(Color.White, CircleShape),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(Icons.Outlined.RadioButtonUnchecked, null, Modifier.size(24.dp), tint = Color.White)
                }
            }
        }
    }
}
