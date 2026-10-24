package hu.egyszeru.galeria.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hu.egyszeru.galeria.util.canRequestManageMedia
import hu.egyszeru.galeria.util.openManageMediaSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    vm: GalleryViewModel,
    partialAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    val overview = remember(data) { data.overview() }
    val context = LocalContext.current
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val canSkipConfirm = canRequestManageMedia(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galéria") },
                actions = {
                    IconButton(onClick = { showCreate = true }) { Icon(Icons.Outlined.Add, "Új album") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Továbbiak") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Lomtár") },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; onOpen(KEY_TRASH) },
                            )
                            if (canSkipConfirm) {
                                DropdownMenuItem(
                                    text = { Text("Kevesebb megerősítő ablak") },
                                    leadingIcon = { Icon(Icons.Outlined.DoneAll, null) },
                                    onClick = { menu = false; openManageMediaSettings(context) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { pad ->
        if (!data.loaded) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = pad.calculateTopPadding() + 8.dp,
                    bottom = pad.calculateBottomPadding() + 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (partialAccess) {
                    item(key = "partial", span = { GridItemSpan(maxLineSpan) }) {
                        PartialAccessCard(onRequestAccess)
                    }
                }
                items(overview.main, key = { it.key }) { AlbumTile(it) { onOpen(it.key) } }
                items(overview.user, key = { it.key }) { AlbumTile(it) { onOpen(it.key) } }
                item(key = "new") { NewAlbumTile { showCreate = true } }

                if (overview.folders.isNotEmpty()) {
                    item(key = "folders_title", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Mappák a telefonon",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(overview.folders, key = { it.key }) { AlbumTile(it) { onOpen(it.key) } }
                }

                item(key = "trash", span = { GridItemSpan(maxLineSpan) }) {
                    ListItem(
                        headlineContent = { Text("Lomtár") },
                        supportingContent = {
                            Text(
                                if (overview.trashCount == 0) "Üres"
                                else "${overview.trashCount} elem · 30 napig visszaállítható"
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.Delete, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.padding(top = 8.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { onOpen(KEY_TRASH) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        NameDialog(
            title = "Új album",
            confirmLabel = "Létrehozás",
            onConfirm = { name ->
                showCreate = false
                onOpen(userKey(vm.createAlbum(name)))
            },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun AlbumTile(info: AlbumInfo, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val cover = info.cover
            if (cover != null) {
                Thumbnail(cover, Modifier.fillMaxSize())
            } else {
                Icon(
                    albumIcon(info.key), null, Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            info.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp, end = 4.dp),
        )
        Text(
            "${info.count} elem",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun NewAlbumTile(onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Add, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(
            "Új album",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun PartialAccessCard(onRequestAccess: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Csak a kiválasztott képekhez van hozzáférés.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestAccess) { Text("Módosítás") }
        }
    }
}
