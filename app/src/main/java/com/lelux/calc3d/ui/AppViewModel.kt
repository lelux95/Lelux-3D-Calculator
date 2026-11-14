package com.lelux.calc3d.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lelux.calc3d.calc.CostEngine
import com.lelux.calc3d.calc.Estimator
import com.lelux.calc3d.calc.QuoteResult
import com.lelux.calc3d.data.AppData
import com.lelux.calc3d.data.Defaults
import com.lelux.calc3d.data.Filament
import com.lelux.calc3d.data.FilamentUsage
import com.lelux.calc3d.data.Part
import com.lelux.calc3d.data.PartSource
import com.lelux.calc3d.data.Quote
import com.lelux.calc3d.data.QuoteStatus
import com.lelux.calc3d.data.Repository
import com.lelux.calc3d.io.ImportException
import com.lelux.calc3d.io.ImportResult
import com.lelux.calc3d.io.ModelImporter
import com.lelux.calc3d.pdf.QuotePdf
import com.lelux.calc3d.pdf.ShareText
import com.lelux.calc3d.render.SoftRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = Repository(app)
    val data: StateFlow<AppData> = repo.data

    val draft = MutableStateFlow(newQuote())
    /** Pezzo aperto nell'editor; isNew = non ancora nel preventivo */
    val editing = MutableStateFlow<Pair<Part, Boolean>?>(null)
    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** File ricevuto con "Apri con" prima di finire la configurazione */
    var pendingUri: Uri? = null

    fun toast(msg: String) { messages.tryEmit(msg) }

    fun newQuote(): Quote {
        val d = repo.data.value
        val s = d.settings
        return Quote(
            channelId = s.defaultChannelId ?: d.channels.firstOrNull()?.id,
            markupPercent = s.defaultMarkup,
            deliveryDays = s.deliveryDays,
            validityDays = s.validityDays,
        )
    }

    fun resetDraft() { draft.value = newQuote() }

    fun result(q: Quote = draft.value): QuoteResult = CostEngine.compute(q, repo.data.value)

    fun updateDraft(t: (Quote) -> Quote) { draft.value = t(draft.value) }

    // ---------- Pezzi ----------

    fun defaultPrinterId(): String? = repo.data.value.let { it.settings.defaultPrinterId ?: it.printers.firstOrNull()?.id }

    fun defaultFilament(): Filament? = repo.data.value.filaments.let { list ->
        list.firstOrNull { it.type.equals("PLA", true) } ?: list.firstOrNull()
    }

    fun newManualPart() {
        val f = defaultFilament()
        editing.value = Part(
            name = "Pezzo ${draft.value.parts.size + 1}",
            printerId = defaultPrinterId(),
            filaments = listOf(FilamentUsage(filamentId = f?.id, type = f?.type ?: "PLA", colorHex = f?.colorHex ?: "#FFFFFF")),
            source = PartSource.MANUAL,
        ) to true
    }

    fun editPart(p: Part) { editing.value = p to false }

    fun commitPart(p: Part, alsoCatalog: Boolean) {
        val exists = draft.value.parts.any { it.id == p.id }
        draft.value = draft.value.copy(parts = if (exists) draft.value.parts.map { if (it.id == p.id) p else it } else draft.value.parts + p)
        if (alsoCatalog) { repo.addToCatalog(p); toast("Salvato anche nel catalogo") }
        editing.value = null
    }

    fun removePart(id: String) { draft.value = draft.value.copy(parts = draft.value.parts.filterNot { it.id == id }) }

    fun setQuantity(id: String, q: Int) {
        draft.value = draft.value.copy(parts = draft.value.parts.map { if (it.id == id) it.copy(quantity = q.coerceAtLeast(1)) else it })
    }

    fun addFromCatalog(p: Part) {
        val copy = p.copy(id = com.lelux.calc3d.data.newId(), quantity = 1)
        draft.value = draft.value.copy(parts = draft.value.parts + copy)
        toast("Aggiunto: ${p.name}")
    }

    fun reEstimate(p: Part): Part {
        val d = repo.data.value
        val printer = CostEngine.printerFor(p, d) ?: com.lelux.calc3d.data.Printer()
        val f = p.filaments.firstOrNull()?.let { CostEngine.filamentFor(it.filamentId, it.type, d) }
        val density = f?.density ?: Defaults.densityFor(p.filaments.firstOrNull()?.type ?: "PLA")
        return Estimator.estimate(p, printer, d.settings, density)
    }

    // ---------- Importazione ----------

    private fun colorDistance(a: String, b: String): Int {
        val ca = runCatching { Color.parseColor(a) }.getOrDefault(Color.WHITE)
        val cb = runCatching { Color.parseColor(b) }.getOrDefault(Color.WHITE)
        val dr = Color.red(ca) - Color.red(cb); val dg = Color.green(ca) - Color.green(cb); val db = Color.blue(ca) - Color.blue(cb)
        return dr * dr + dg * dg + db * db
    }

    /** Abbina un filamento del file al magazzino: stesso tipo, colore piu' vicino. */
    fun matchFilament(type: String, color: String): Filament? {
        val list = repo.data.value.filaments
        val sameType = list.filter { it.type.equals(type, true) }.ifEmpty {
            list.filter { it.type.uppercase().startsWith(type.uppercase().take(3)) }
        }
        return sameType.minByOrNull { colorDistance(it.colorHex, color) }
    }

    private fun saveThumb(bmp: Bitmap?, id: String): String? {
        if (bmp == null) return null
        return runCatching {
            val f = File(repo.thumbsDir, "$id.png")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            f.absolutePath
        }.getOrNull()
    }

    fun importUri(uri: Uri) {
        if (!repo.data.value.settings.onboardingDone) { pendingUri = uri; return }
        viewModelScope.launch {
            busy.value = true
            try {
                val parts = withContext(Dispatchers.IO) { buildParts(ModelImporter.import(getApplication<Application>(), uri, repo.modelsDir)) }
                when {
                    parts.size == 1 -> editing.value = parts[0] to true
                    else -> {
                        draft.value = draft.value.copy(parts = draft.value.parts + parts)
                        toast("Importati ${parts.size} piatti dal file")
                    }
                }
            } catch (e: ImportException) {
                toast(e.message ?: "Errore di importazione")
            } catch (e: OutOfMemoryError) {
                toast("Il file e' troppo grande per la memoria del telefono")
            } catch (e: Exception) {
                toast("Impossibile leggere il file: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy.value = false
            }
        }
    }

    private fun buildParts(r: ImportResult): List<Part> {
        val d = repo.data.value
        val printerId = defaultPrinterId()
        val mesh = r.mesh
        val modelFile = if (r.kind == com.lelux.calc3d.io.FileKind.GCODE || mesh == null) null else r.storedFile.absolutePath
        if (r.kind == com.lelux.calc3d.io.FileKind.GCODE) r.storedFile.delete() // del G-code bastano i dati letti

        if (r.isSliced) {
            return r.plates.map { plate ->
                val id = com.lelux.calc3d.data.newId()
                val usages = plate.filaments.map { u ->
                    val f = matchFilament(u.type, u.colorHex)
                    u.copy(filamentId = f?.id)
                }
                val tint = runCatching { Color.parseColor(usages.firstOrNull()?.colorHex ?: "#FF8A3D") }.getOrDefault(0xFFFF8A3D.toInt())
                val thumbBmp = plate.thumbnail ?: r.thumbnail ?: mesh?.let { SoftRenderer.render(it, 384, safeTint(tint)) }
                Part(
                    id = id,
                    name = if (r.plates.size > 1) "${r.baseName} – piatto ${plate.index}" else r.baseName,
                    printerId = printerId,
                    filaments = usages.ifEmpty { listOf(FilamentUsage()) },
                    printSeconds = plate.seconds,
                    source = PartSource.SLICER,
                    modelFile = modelFile,
                    thumbnailFile = saveThumb(thumbBmp, id),
                    sizeX = mesh?.sizeX ?: 0.0, sizeY = mesh?.sizeY ?: 0.0, sizeZ = mesh?.sizeZ ?: 0.0,
                    volumeMm3 = mesh?.volume ?: 0.0, areaMm2 = mesh?.area ?: 0.0,
                )
            }
        }

        // Solo geometria: stima
        if (mesh == null) throw ImportException("Il file non contiene dati utilizzabili")
        val projectType = r.projectFilaments.firstOrNull()
        val f = if (projectType != null) matchFilament(projectType.first, projectType.second) ?: defaultFilament() else defaultFilament()
        val id = com.lelux.calc3d.data.newId()
        val tint = runCatching { Color.parseColor(f?.colorHex ?: "#FF8A3D") }.getOrDefault(0xFFFF8A3D.toInt())
        val thumbBmp = r.thumbnail ?: SoftRenderer.render(mesh, 384, safeTint(tint))
        val base = Part(
            id = id,
            name = r.baseName,
            printerId = printerId,
            filaments = listOf(FilamentUsage(filamentId = f?.id, type = f?.type ?: projectType?.first ?: "PLA", colorHex = f?.colorHex ?: "#FFFFFF")),
            source = PartSource.ESTIMATE,
            modelFile = modelFile,
            thumbnailFile = saveThumb(thumbBmp, id),
            sizeX = mesh.sizeX, sizeY = mesh.sizeY, sizeZ = mesh.sizeZ,
            volumeMm3 = mesh.volume, areaMm2 = mesh.area,
            infillPercent = d.settings.estimateInfill,
        )
        return listOf(reEstimate(base))
    }

    /** Colori quasi bianchi o quasi neri si vedono male nella miniatura: li ammorbidisce. */
    fun safeTint(c: Int): Int {
        val lum = (Color.red(c) * 0.3 + Color.green(c) * 0.59 + Color.blue(c) * 0.11)
        return when {
            lum > 235 -> 0xFFDADDE2.toInt()
            lum < 35 -> 0xFF4A4D55.toInt()
            else -> c or 0xFF000000.toInt()
        }
    }

    // ---------- Preventivi ----------

    fun saveDraft(): Quote? {
        val q = draft.value
        if (q.parts.isEmpty()) { toast("Aggiungi almeno un pezzo"); return null }
        val number = q.number.ifBlank { repo.takeQuoteNumber() }
        val r = CostEngine.compute(q.copy(number = number), repo.data.value)
        val title = q.title.ifBlank { q.parts.joinToString(" + ") { it.name } }
        val saved = q.copy(number = number, title = title, savedPrice = r.price, savedCost = r.productionCost, savedProfit = r.profit)
        repo.upsertQuote(saved)
        draft.value = saved
        return saved
    }

    fun openQuote(q: Quote) { draft.value = q }

    fun duplicateQuote(q: Quote) {
        draft.value = q.copy(
            id = com.lelux.calc3d.data.newId(), number = "", createdAt = System.currentTimeMillis(),
            status = QuoteStatus.DRAFT, parts = q.parts.map { it.copy(id = com.lelux.calc3d.data.newId()) },
        )
    }

    fun setStatus(q: Quote, s: QuoteStatus) { repo.upsertQuote(q.copy(status = s)) }

    suspend fun makePdf(q: Quote): File = withContext(Dispatchers.IO) {
        val r = CostEngine.compute(q, repo.data.value)
        val safe = q.number.ifBlank { "bozza" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        QuotePdf.create(q, r, repo.data.value, File(repo.sharedDir, "Preventivo_$safe.pdf"))
    }

    fun shareText(q: Quote): String = ShareText.build(q, CostEngine.compute(q, repo.data.value), repo.data.value)

    fun csvExport(): File {
        val d = repo.data.value
        val sb = StringBuilder("Numero;Data;Cliente;Titolo;Stato;Prezzo;Costo;Utile\n")
        d.quotes.sortedByDescending { it.createdAt }.forEach { q ->
            fun n(v: Double) = String.format(java.util.Locale.ITALY, "%.2f", v)
            fun esc(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            sb.append(listOf(q.number, com.lelux.calc3d.calc.Fmt.date(q.createdAt), esc(q.customer), esc(q.title), q.status.label, n(q.savedPrice), n(q.savedCost), n(q.savedProfit)).joinToString(";")).append('\n')
        }
        val f = File(repo.sharedDir, "Storico_preventivi.csv")
        f.writeText("﻿" + sb.toString())
        return f
    }

    fun saveLogo(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repo.imagesDir.listFiles()?.filter { it.name.startsWith("logo") }?.forEach { it.delete() }
                val f = File(repo.imagesDir, "logo-${System.currentTimeMillis()}.png")
                val bmp = getApplication<Application>().contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
                    ?: throw ImportException("Immagine non valida")
                // Ridimensiona a massimo 1200 px di lato
                val scale = minOf(1f, 1200f / maxOf(bmp.width, bmp.height))
                val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                repo.updateSettings { it.copy(business = it.business.copy(logoFile = f.absolutePath)) }
            }.onFailure { toast("Logo non caricato: ${it.message}") }
        }
    }
}
