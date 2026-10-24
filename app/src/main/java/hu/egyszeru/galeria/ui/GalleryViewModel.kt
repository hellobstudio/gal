package hu.egyszeru.galeria.ui

import android.app.Application
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import hu.egyszeru.galeria.data.AlbumStore
import hu.egyszeru.galeria.data.MediaItem
import hu.egyszeru.galeria.data.MediaRepository
import hu.egyszeru.galeria.data.UserAlbum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val KEY_PHOTOS = "photos"
const val KEY_VIDEOS = "videos"
const val KEY_FAVORITES = "favorites"
const val KEY_TRASH = "trash"
private const val USER_PREFIX = "user:"
private const val BUCKET_PREFIX = "bucket:"

fun userKey(id: Long) = "$USER_PREFIX$id"
fun bucketKey(id: Long) = "$BUCKET_PREFIX$id"

data class AlbumInfo(
    val key: String,
    val title: String,
    val count: Int,
    val cover: MediaItem?,
    val userId: Long? = null,
)

data class Overview(
    val main: List<AlbumInfo>,
    val user: List<AlbumInfo>,
    val folders: List<AlbumInfo>,
    val trashCount: Int,
)

/** A teljes galéria pillanatnyi állapota. */
data class GalleryData(
    val media: List<MediaItem> = emptyList(),
    val trash: List<MediaItem> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val albums: List<UserAlbum> = emptyList(),
    val loaded: Boolean = false,
) {
    fun itemsFor(key: String): List<MediaItem> = when {
        key == KEY_PHOTOS -> media.filter { !it.isVideo }
        key == KEY_VIDEOS -> media.filter { it.isVideo }
        key == KEY_FAVORITES -> media.filter { it.id in favorites }
        key == KEY_TRASH -> trash
        key.startsWith(USER_PREFIX) -> userAlbum(key)?.let { a -> media.filter { it.id in a.items } } ?: emptyList()
        key.startsWith(BUCKET_PREFIX) -> {
            val id = key.removePrefix(BUCKET_PREFIX).toLongOrNull()
            media.filter { it.bucketId == id }
        }
        else -> emptyList()
    }

    fun titleFor(key: String): String? = when {
        key == KEY_PHOTOS -> "Fényképek"
        key == KEY_VIDEOS -> "Videók"
        key == KEY_FAVORITES -> "Kedvencek"
        key == KEY_TRASH -> "Lomtár"
        key.startsWith(USER_PREFIX) -> userAlbum(key)?.name
        key.startsWith(BUCKET_PREFIX) -> {
            val id = key.removePrefix(BUCKET_PREFIX).toLongOrNull()
            media.firstOrNull { it.bucketId == id }?.bucketName?.ifBlank { null } ?: "Mappa"
        }
        else -> null
    }

    fun userAlbum(key: String): UserAlbum? {
        if (!key.startsWith(USER_PREFIX)) return null
        val id = key.removePrefix(USER_PREFIX).toLongOrNull() ?: return null
        return albums.firstOrNull { it.id == id }
    }

    fun userAlbumInfos(): List<AlbumInfo> = albums.map { a ->
        val items = media.filter { it.id in a.items }
        AlbumInfo(userKey(a.id), a.name, items.size, items.firstOrNull(), userId = a.id)
    }

    fun overview(): Overview {
        val photos = media.filter { !it.isVideo }
        val videos = media.filter { it.isVideo }
        val favs = media.filter { it.id in favorites }
        val main = listOf(
            AlbumInfo(KEY_PHOTOS, "Fényképek", photos.size, photos.firstOrNull()),
            AlbumInfo(KEY_VIDEOS, "Videók", videos.size, videos.firstOrNull()),
            AlbumInfo(KEY_FAVORITES, "Kedvencek", favs.size, favs.firstOrNull()),
        )
        val folders = media.groupBy { it.bucketId }
            .map { (id, list) ->
                AlbumInfo(bucketKey(id), list.first().bucketName.ifBlank { "Belső tárhely" }, list.size, list.first())
            }
            .sortedByDescending { it.count }
        return Overview(main, userAlbumInfos(), folders, trash.size)
    }
}

/** Rendszer-jóváhagyásra váró művelet. */
private sealed interface PendingOp {
    data class Simple(val successMessage: String) : PendingOp
    data class Rename(val uri: Uri, val newName: String) : PendingOp
}

class GalleryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepository(app)
    private val store = AlbumStore(app)

    private val _data = MutableStateFlow(
        GalleryData(favorites = store.loadFavorites(), albums = store.loadAlbums())
    )
    val data: StateFlow<GalleryData> = _data.asStateFlow()

    private val _columns = MutableStateFlow(store.columns)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    /** Rendszer-párbeszédablakok (törlés, átnevezés jóváhagyása) – a MainActivity indítja őket. */
    private val _requests = Channel<IntentSender>(Channel.BUFFERED)
    val requests = _requests.receiveAsFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    private val _selectionCleared = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val selectionCleared = _selectionCleared.asSharedFlow()

    private var pending: PendingOp? = null
    private var loadJob: Job? = null
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh(delayMs = 400)
    }

    /** Engedély megadása után hívandó. */
    fun start() {
        if (!observing) {
            observing = true
            repo.registerObserver(observer)
        }
        refresh()
    }

    fun refresh(delayMs: Long = 0) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (delayMs > 0) delay(delayMs)
            val (media, trash) = withContext(Dispatchers.IO) { repo.query(false) to repo.query(true) }
            _data.update { it.copy(media = media, trash = trash, loaded = true) }
        }
    }

    override fun onCleared() {
        if (observing) repo.unregisterObserver(observer)
    }

    // ---------- Kedvencek ----------

    fun setFavorite(ids: Collection<Long>, favorite: Boolean) {
        val updated = _data.value.favorites.toMutableSet().apply {
            if (favorite) addAll(ids) else removeAll(ids.toSet())
        }
        store.saveFavorites(updated)
        _data.update { it.copy(favorites = updated) }
    }

    // ---------- Saját albumok ----------

    fun createAlbum(name: String): Long {
        val id = System.currentTimeMillis()
        updateAlbums { it + UserAlbum(id, name.trim(), emptySet()) }
        return id
    }

    fun renameAlbum(id: Long, name: String) =
        updateAlbums { list -> list.map { if (it.id == id) it.copy(name = name.trim()) else it } }

    fun deleteAlbum(id: Long) = updateAlbums { list -> list.filterNot { it.id == id } }

    fun addToAlbum(albumId: Long, ids: Collection<Long>) {
        val name = _data.value.albums.firstOrNull { it.id == albumId }?.name ?: return
        updateAlbums { list -> list.map { if (it.id == albumId) it.copy(items = it.items + ids) else it } }
        message("${ids.size} elem hozzáadva: $name")
        _selectionCleared.tryEmit(Unit)
    }

    fun removeFromAlbum(albumId: Long, ids: Collection<Long>) {
        val remove = ids.toSet()
        updateAlbums { list -> list.map { if (it.id == albumId) it.copy(items = it.items - remove) else it } }
        message("${ids.size} elem eltávolítva az albumból")
        _selectionCleared.tryEmit(Unit)
    }

    private fun updateAlbums(transform: (List<UserAlbum>) -> List<UserAlbum>) {
        val updated = transform(_data.value.albums)
        store.saveAlbums(updated)
        _data.update { it.copy(albums = updated) }
    }

    // ---------- Fájlműveletek (rendszer-jóváhagyással) ----------

    fun moveToTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launchRequest(PendingOp.Simple("${items.size} elem a lomtárba került")) {
            repo.trashRequest(items.map { it.uri }, trash = true)
        }
    }

    fun restore(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launchRequest(PendingOp.Simple("${items.size} elem visszaállítva")) {
            repo.trashRequest(items.map { it.uri }, trash = false)
        }
    }

    fun deleteForever(items: List<MediaItem>) {
        if (items.isEmpty()) return
        launchRequest(PendingOp.Simple("${items.size} elem véglegesen törölve")) {
            repo.deleteRequest(items.map { it.uri })
        }
    }

    fun rename(item: MediaItem, newBaseName: String) {
        val base = newBaseName.trim()
        if (base.isEmpty() || base.contains('/')) {
            message("Érvénytelen név")
            return
        }
        val newName = base + item.extension
        if (newName == item.name) return
        launchRequest(PendingOp.Rename(item.uri, newName)) { repo.writeRequest(listOf(item.uri)) }
    }

    private fun launchRequest(op: PendingOp, build: () -> IntentSender) {
        try {
            val sender = build()
            pending = op
            _requests.trySend(sender)
        } catch (e: Exception) {
            message("A művelet nem sikerült")
        }
    }

    fun onRequestResult(ok: Boolean) {
        val op = pending
        pending = null
        if (!ok || op == null) return
        when (op) {
            is PendingOp.Simple -> {
                message(op.successMessage)
                _selectionCleared.tryEmit(Unit)
            }
            is PendingOp.Rename -> viewModelScope.launch {
                val success = withContext(Dispatchers.IO) {
                    runCatching { repo.rename(op.uri, op.newName) }.getOrDefault(false)
                }
                if (success) {
                    message("Átnevezve: ${op.newName}")
                    _selectionCleared.tryEmit(Unit)
                } else {
                    message("Az átnevezés nem sikerült (lehet, hogy már létezik ilyen nevű fájl)")
                }
            }
        }
        refresh(delayMs = 300)
    }

    // ---------- Egyéb ----------

    fun setColumns(count: Int) {
        store.columns = count
        _columns.value = count
    }

    private fun message(text: String) {
        _messages.tryEmit(text)
    }
}
