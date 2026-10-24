package hu.egyszeru.galeria.util

import android.content.Context
import android.text.format.Formatter
import hu.egyszeru.galeria.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HU: Locale = Locale.forLanguageTag("hu-HU")
private val dayFormat = DateTimeFormatter.ofPattern("MMMM d., EEEE", HU)
private val dayYearFormat = DateTimeFormatter.ofPattern("yyyy. MMMM d., EEEE", HU)
private val fullFormat = DateTimeFormatter.ofPattern("yyyy. MMMM d. HH:mm", HU)

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** "Ma", "Tegnap", "Október 12., hétfő" vagy "2025. október 12., hétfő". */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Ma"
    today.minusDays(1) -> "Tegnap"
    else -> {
        val text = if (date.year == today.year) date.format(dayFormat) else date.format(dayYearFormat)
        text.replaceFirstChar { it.titlecase(HU) }
    }
}

fun fullDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(fullFormat)

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(context: Context, bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

data class DaySection(val date: LocalDate, val items: List<MediaItem>)

/** Dátum szerint csökkenő listát napokra bont. */
fun groupByDay(items: List<MediaItem>): List<DaySection> {
    val result = ArrayList<DaySection>()
    var current: LocalDate? = null
    var bucket = ArrayList<MediaItem>()
    for (m in items) {
        val d = m.dateTaken.toLocalDate()
        if (d != current) {
            if (current != null) result += DaySection(current, bucket)
            current = d
            bucket = ArrayList()
        }
        bucket += m
    }
    if (current != null) result += DaySection(current, bucket)
    return result
}
