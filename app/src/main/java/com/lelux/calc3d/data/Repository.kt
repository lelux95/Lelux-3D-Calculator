package com.lelux.calc3d.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val printers: List<Printer> = emptyList(),
    val filaments: List<Filament> = emptyList(),
    val channels: List<SalesChannel> = Defaults.defaultChannels(),
    val quotes: List<Quote> = emptyList(),
    val catalog: List<CatalogItem> = emptyList(),
)

/** Tutti i dati restano sul telefono, in un unico file JSON nella memoria privata dell'app. */
class Repository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val file = File(context.filesDir, "data.json")
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    val modelsDir: File get() = File(context.filesDir, "models").apply { mkdirs() }
    val thumbsDir: File get() = File(context.filesDir, "thumbs").apply { mkdirs() }
    val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }
    val sharedDir: File get() = File(context.cacheDir, "shared").apply { mkdirs() }

    private fun load(): AppData = try {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else AppData()
    } catch (e: Exception) {
        // File danneggiato: lo conserva da parte e riparte da zero
        runCatching { file.copyTo(File(context.filesDir, "data-corrotto-${System.currentTimeMillis()}.json")) }
        AppData()
    }

    @Synchronized
    fun update(transform: (AppData) -> AppData) {
        val next = transform(_data.value)
        _data.value = next
        val tmp = File(context.filesDir, "data.json.tmp")
        tmp.writeText(json.encodeToString(next))
        tmp.renameTo(file)
    }

    fun exportJson(): String = json.encodeToString(_data.value)

    fun importJson(text: String): Boolean = try {
        val d = json.decodeFromString<AppData>(text)
        update { d }
        true
    } catch (e: Exception) { false }

    // Aiuti per le liste
    fun upsertPrinter(p: Printer) = update { d -> d.copy(printers = d.printers.upsert(p) { it.id }) }
    fun deletePrinter(id: String) = update { d -> d.copy(printers = d.printers.filterNot { it.id == id }) }
    fun upsertFilament(f: Filament) = update { d -> d.copy(filaments = d.filaments.upsert(f) { it.id }) }
    fun deleteFilament(id: String) = update { d -> d.copy(filaments = d.filaments.filterNot { it.id == id }) }
    fun upsertChannel(c: SalesChannel) = update { d -> d.copy(channels = d.channels.upsert(c) { it.id }) }
    fun deleteChannel(id: String) = update { d -> d.copy(channels = d.channels.filterNot { it.id == id }) }
    fun upsertQuote(q: Quote) = update { d -> d.copy(quotes = d.quotes.upsert(q) { it.id }) }
    fun deleteQuote(id: String) = update { d -> d.copy(quotes = d.quotes.filterNot { it.id == id }) }
    fun addToCatalog(p: Part) = update { d -> d.copy(catalog = listOf(CatalogItem(part = p.copy(quantity = 1))) + d.catalog) }
    fun deleteCatalog(id: String) = update { d -> d.copy(catalog = d.catalog.filterNot { it.id == id }) }
    fun updateSettings(t: (Settings) -> Settings) = update { d -> d.copy(settings = t(d.settings)) }

    /** Restituisce il prossimo numero di preventivo (es. LX-2026-001) e incrementa il contatore. */
    fun takeQuoteNumber(): String {
        val s = _data.value.settings
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val n = s.nextQuoteNumber
        updateSettings { it.copy(nextQuoteNumber = n + 1) }
        val prefix = s.quotePrefix.ifBlank { "LX" }
        return "$prefix-$year-${n.toString().padStart(3, '0')}"
    }
}

fun <T> List<T>.upsert(item: T, key: (T) -> String): List<T> {
    val k = key(item)
    val idx = indexOfFirst { key(it) == k }
    return if (idx >= 0) toMutableList().also { it[idx] = item } else this + item
}
