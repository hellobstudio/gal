package hu.egyszeru.galeria.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Saját albumok, kedvencek és beállítások tárolása a készüléken (SharedPreferences). */
class AlbumStore(context: Context) {

    private val prefs = context.getSharedPreferences("galeria", Context.MODE_PRIVATE)

    fun loadFavorites(): Set<Long> =
        (prefs.getString(KEY_FAVORITES, "") ?: "")
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun saveFavorites(ids: Set<Long>) {
        prefs.edit().putString(KEY_FAVORITES, ids.joinToString(",")).apply()
    }

    fun loadAlbums(): List<UserAlbum> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_ALBUMS, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val items = o.getJSONArray("items")
            UserAlbum(
                id = o.getLong("id"),
                name = o.getString("name"),
                items = (0 until items.length()).map { items.getLong(it) }.toSet(),
            )
        }
    }.getOrDefault(emptyList())

    fun saveAlbums(albums: List<UserAlbum>) {
        val arr = JSONArray()
        albums.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("name", a.name)
                    .put("items", JSONArray(a.items.toList()))
            )
        }
        prefs.edit().putString(KEY_ALBUMS, arr.toString()).apply()
    }

    var columns: Int
        get() = prefs.getInt(KEY_COLUMNS, 4)
        set(value) = prefs.edit().putInt(KEY_COLUMNS, value).apply()

    private companion object {
        const val KEY_FAVORITES = "favorites"
        const val KEY_ALBUMS = "albums"
        const val KEY_COLUMNS = "columns"
    }
}
