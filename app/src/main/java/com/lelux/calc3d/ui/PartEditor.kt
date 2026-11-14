package com.lelux.calc3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lelux.calc3d.calc.CostEngine
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.data.Defaults
import com.lelux.calc3d.data.Filament
import com.lelux.calc3d.data.FilamentUsage
import com.lelux.calc3d.data.Part
import com.lelux.calc3d.data.PartSource
import com.lelux.calc3d.io.Mesh
import com.lelux.calc3d.io.ModelImporter
import com.lelux.calc3d.render.ModelViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PartEditor(vm: AppViewModel, initial: Part, isNew: Boolean, onClose: () -> Unit) {
    val data by vm.data.collectAsState()
    var p by remember(initial.id) { mutableStateOf(initial) }
    var alsoCatalog by remember { mutableStateOf(false) }
    var showViewer by remember { mutableStateOf(false) }
    val cost = remember(p, data) { CostEngine.partCost(p, data) }

    fun estimateIfNeeded(np: Part): Part = if (np.source == PartSource.ESTIMATE) vm.reEstimate(np) else np

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 36.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Chiudi") }
                    Text(if (isNew) "Nuovo pezzo" else "Modifica pezzo", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(p.thumbnailFile, 96.dp, Modifier.clickable(enabled = p.modelFile != null) { showViewer = true })
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.source.label, color = if (p.source == PartSource.ESTIMATE) MaterialTheme.colorScheme.primary else Good, fontWeight = FontWeight.SemiBold)
                            if (p.sizeX > 0) Text("${Fmt.num1(p.sizeX)} × ${Fmt.num1(p.sizeY)} × ${Fmt.num1(p.sizeZ)} mm", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (p.modelFile != null) TextButton(onClick = { showViewer = true }) { Text("Guarda in 3D") }
                        }
                        PhotoButton(vm, null, 56.dp) { path -> p = p.copy(thumbnailFile = path) }
                    }
                    TextIn("Nome del pezzo", p.name, { p = p.copy(name = it) })
                    Selector("Stampante", data.printers, data.printers.firstOrNull { it.id == p.printerId } ?: CostEngine.printerFor(p, data),
                        { it.name }, { pr -> p = estimateIfNeeded(p.copy(printerId = pr.id)) }, Modifier.fillMaxWidth())

                    SectionCard("Filamenti") {
                        p.filaments.forEachIndexed { i, u ->
                            FilamentUsageRow(
                                vm = vm, usage = u, index = i, canDelete = p.filaments.size > 1, editableGrams = p.source != PartSource.ESTIMATE || i > 0,
                                onChange = { nu -> p = estimateIfNeeded(p.copy(filaments = p.filaments.toMutableList().also { it[i] = nu })) },
                                onDelete = { p = p.copy(filaments = p.filaments.toMutableList().also { it.removeAt(i) }) },
                            )
                        }
                        TextButton(onClick = {
                            val f = vm.defaultFilament()
                            p = p.copy(filaments = p.filaments + FilamentUsage(filamentId = f?.id, type = f?.type ?: "PLA", colorHex = f?.colorHex ?: "#FFFFFF"))
                        }) { Text("+ Aggiungi filamento") }
                        if (p.source != PartSource.ESTIMATE) {
                            NumField("Spurgo / torre di spurgo", p.purgeGrams, { p = p.copy(purgeGrams = it) }, Modifier.fillMaxWidth(), suffix = "g",
                                supporting = if (p.source == PartSource.SLICER) "I grammi letti dallo slicer includono già lo spurgo" else null)
                        }
                    }

                    if (p.source == PartSource.ESTIMATE) {
                        SectionCard("Stima dalla geometria") {
                            Text("Peso e tempo sono stimati dal volume del modello. Per dati esatti esegui lo slicing ed esporta il .3mf o il G-code.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            NumField("Riempimento", p.infillPercent, { v -> p = vm.reEstimate(p.copy(infillPercent = v.coerceIn(0.0, 100.0))) }, Modifier.fillMaxWidth(), suffix = "%")
                            Text("Volume ${Fmt.num1(p.volumeMm3 / 1000)} cm³ · stima ${Fmt.grams(p.totalGrams)} · ${Fmt.duration(p.printSeconds)}",
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { p = p.copy(source = PartSource.MANUAL) }) { Text("Correggi peso e tempo a mano") }
                        }
                    } else {
                        SectionCard("Tempo di stampa del piatto") {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IntField("Ore", (p.printSeconds / 3600).toInt(), { h -> p = p.copy(printSeconds = h * 3600L + (p.printSeconds % 3600)) }, Modifier.weight(1f), suffix = "h")
                                IntField("Minuti", ((p.printSeconds % 3600) / 60).toInt(), { m -> p = p.copy(printSeconds = (p.printSeconds / 3600) * 3600 + m.coerceIn(0, 59) * 60L) }, Modifier.weight(1f), suffix = "min")
                            }
                        }
                    }

                    SectionCard("Quantità") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IntField("Pezzi per piatto", p.piecesPerPlate, { p = p.copy(piecesPerPlate = it.coerceAtLeast(1)) }, Modifier.weight(1f))
                            IntField("Quantità ordinata", p.quantity, { p = p.copy(quantity = it.coerceAtLeast(1)) }, Modifier.weight(1f))
                        }
                        Text("Se il piatto produce più copie dello stesso pezzo, peso e tempo vengono divisi tra i pezzi.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    SectionCard("Costo") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("Per pezzo", Fmt.euro(cost.perPiece), Modifier.weight(1f), highlight = true)
                            StatTile("Totale x${p.quantity}", Fmt.euro(cost.total), Modifier.weight(1f))
                        }
                        Text("Filamento ${Fmt.euro(cost.material)} · energia ${Fmt.euro(cost.energy)} · macchina ${Fmt.euro(cost.depreciation)} · manodopera ${Fmt.euro(cost.labor)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Row(Modifier.fillMaxWidth().clickable { alsoCatalog = !alsoCatalog }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoCatalog, onCheckedChange = { alsoCatalog = it })
                        Text("Salva anche nel catalogo pezzi")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onClose, Modifier.weight(1f)) { Text("Annulla") }
                    Button(onClick = { vm.commitPart(p, alsoCatalog) }, Modifier.weight(1f)) { Text(if (isNew) "Aggiungi" else "Salva") }
                }
            }
        }
    }
    if (showViewer) ViewerDialog(vm, p, onDismiss = { showViewer = false })
}

@Composable
fun FilamentUsageRow(
    vm: AppViewModel, usage: FilamentUsage, index: Int, canDelete: Boolean, editableGrams: Boolean,
    onChange: (FilamentUsage) -> Unit, onDelete: () -> Unit,
) {
    val data by vm.data.collectAsState()
    val selected = data.filaments.firstOrNull { it.id == usage.filamentId }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Selector(
                "Filamento ${index + 1}", data.filaments, selected,
                { f -> f.label.ifBlank { f.type } + " · ${Fmt.num(f.pricePerKg)} €/kg" },
                { f -> onChange(usage.copy(filamentId = f.id, type = f.type, colorHex = f.colorHex)) },
                Modifier.weight(1f),
                leading = { f -> ColorDot(f.colorHex, 14.dp) },
            )
            if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Rimuovi") }
        }
        if (selected == null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorDot(usage.colorHex, 14.dp)
                Spacer(Modifier.width(8.dp))
                Text("${usage.type} non è nel magazzino: uso 20 €/kg", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    val preset = Defaults.filamentPresets.firstOrNull { it.type.equals(usage.type, true) }
                    val name = Defaults.namedColors.firstOrNull { it.second.equals(usage.colorHex, true) }?.first ?: ""
                    val f = Filament(type = usage.type, colorHex = usage.colorHex, colorName = name,
                        pricePerKg = preset?.pricePerKg ?: 20.0, density = Defaults.densityFor(usage.type))
                    vm.repo.upsertFilament(f)
                    onChange(usage.copy(filamentId = f.id))
                }) { Text("Aggiungi") }
            }
        }
        if (editableGrams) NumField("Peso", usage.grams, { g -> onChange(usage.copy(grams = g)) }, Modifier.fillMaxWidth(), suffix = "g")
        else Text("Peso stimato: ${Fmt.grams(usage.grams)}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ViewerDialog(vm: AppViewModel, part: Part, onDismiss: () -> Unit) {
    val bg = MaterialTheme.colorScheme.background.toArgb()
    val color = vm.safeTint(runCatching { android.graphics.Color.parseColor(part.filaments.firstOrNull()?.colorHex ?: "#FF8A3D") }.getOrDefault(0xFFFF8A3D.toInt()))
    val mesh by produceState<Mesh?>(null, part.modelFile) {
        value = withContext(Dispatchers.IO) { part.modelFile?.let { f -> if (File(f).exists()) ModelImporter.loadMesh(File(f)) else null } }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                val m = mesh
                val loaded = m != null
                if (m != null) {
                    AndroidView(factory = { ctx -> ModelViewer(ctx, m, color, bg) }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (part.modelFile == null) Text("Modello 3D non disponibile per questo pezzo")
                        else { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("Caricamento modello…") }
                    }
                }
                Column(Modifier.align(Alignment.TopStart).padding(top = 36.dp, start = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Chiudi") }
                        Text(part.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (loaded) Text("Un dito ruota · due dita zoom · doppio tocco per ripristinare",
                        Modifier.padding(start = 16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.size(0.dp))
            }
        }
    }
}
