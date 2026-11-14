package com.lelux.calc3d.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.lelux.calc3d.calc.CostEngine
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.data.FilamentUsage
import com.lelux.calc3d.data.PartSource
import kotlinx.coroutines.launch

/** Barra in alto: stampante e materiale usati per i nuovi pezzi. */
@Composable
fun DefaultsBar(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val printer = data.printers.firstOrNull { it.id == data.settings.defaultPrinterId } ?: data.printers.firstOrNull()
    val filament = vm.defaultFilament()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Selector("Stampante", data.printers, printer, { it.name }, { vm.setDefaults(printerId = it.id) }, Modifier.weight(1f))
        Selector("Materiale", data.filaments, filament, { f -> listOf(f.type, f.colorName).filter { it.isNotBlank() }.joinToString(" ") },
            { vm.setDefaults(filament = it) }, Modifier.weight(1f), leading = { f -> ColorDot(f.colorHex, 12.dp) })
    }
}

@Composable
fun AddPanel(vm: AppViewModel, onPickFile: () -> Unit, onOpenCatalog: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    SectionCard {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = tab == 0, onClick = { tab = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Importazione smart") }
            SegmentedButton(selected = tab == 1, onClick = { tab = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Inserimento manuale") }
        }
        if (tab == 0) SmartImport(vm, onPickFile, onOpenCatalog) else ManualForm(vm)
    }
}

@Composable
private fun SmartImport(vm: AppViewModel, onPickFile: () -> Unit, onOpenCatalog: () -> Unit) {
    val data by vm.data.collectAsState()
    Text(".3mf · .gcode · .stl", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BigAction("Dallo slicer", "file 3D o G-code", Modifier.weight(1f), onPickFile)
        BigAction("Dal catalogo", "${data.catalog.size} pezzi salvati", Modifier.weight(1f), onOpenCatalog)
    }
    if (data.catalog.isNotEmpty()) {
        Spacer(Modifier.height(2.dp))
        Text("CAROSELLO PRODOTTI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(data.catalog, key = { it.id }) { c ->
                val p = c.part
                Column(
                    Modifier.width(132.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { vm.addFromCatalog(p) }.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        Thumb(p.thumbnailFile, 112.dp)
                        Text(
                            p.filaments.map { it.type }.distinct().joinToString(" · ") + if (p.filaments.size > 1) " · ${p.filaments.size} colori" else "",
                            Modifier.padding(5.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Fmt.grams(p.totalGrams)} · ${Fmt.duration(p.printSeconds)}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text("Tocca un prodotto per aggiungerlo al calcolo.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BigAction(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(vertical = 16.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun ManualForm(vm: AppViewModel) {
    val m by vm.manual.collectAsState()
    var showCatalog by remember { mutableStateOf(false) }
    fun set(t: (com.lelux.calc3d.data.Part) -> com.lelux.calc3d.data.Part) { vm.manual.value = t(vm.manual.value) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        TextIn("Nome del pezzo", m.name, { v -> set { it.copy(name = v) } }, Modifier.weight(1f), placeholder = "Pezzo (opzionale)")
        Spacer(Modifier.width(10.dp))
        PhotoButton(vm, m.thumbnailFile) { path -> set { it.copy(thumbnailFile = path) } }
    }
    m.filaments.forEachIndexed { i, u ->
        FilamentUsageRow(
            vm = vm, usage = u, index = i, canDelete = m.filaments.size > 1, editableGrams = true,
            onChange = { nu -> set { it.copy(filaments = it.filaments.toMutableList().also { l -> l[i] = nu }) } },
            onDelete = { set { it.copy(filaments = it.filaments.toMutableList().also { l -> l.removeAt(i) }) } },
        )
    }
    TextButton(onClick = {
        val f = vm.defaultFilament()
        set { it.copy(filaments = it.filaments + FilamentUsage(filamentId = f?.id, type = f?.type ?: "PLA", colorHex = f?.colorHex ?: "#FFFFFF")) }
    }) { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Filamento") }

    Text("Tempo di stampa", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IntField("Ore", (m.printSeconds / 3600).toInt(), { h -> set { it.copy(printSeconds = h * 3600L + it.printSeconds % 3600) } }, Modifier.weight(1f), suffix = "h")
        IntField("Minuti", ((m.printSeconds % 3600) / 60).toInt(), { mi -> set { it.copy(printSeconds = (it.printSeconds / 3600) * 3600 + mi.coerceIn(0, 59) * 60L) } }, Modifier.weight(1f), suffix = "min")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NumField("Spurgo", m.purgeGrams, { v -> set { it.copy(purgeGrams = v) } }, Modifier.weight(1f), suffix = "g")
        IntField("Qtà per piatto", m.piecesPerPlate, { v -> set { it.copy(piecesPerPlate = v.coerceAtLeast(1)) } }, Modifier.weight(1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { showCatalog = true }, Modifier.weight(1f)) { Text("Importa dal catalogo", maxLines = 1) }
        OutlinedButton(onClick = { vm.manualToCatalog() }, Modifier.weight(1f)) { Text("Salva nel catalogo", maxLines = 1) }
    }
    Button(onClick = { vm.addManual() }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Text("Aggiungi al calcolatore", fontWeight = FontWeight.Bold)
    }
    if (showCatalog) CatalogPicker(vm, onPick = { vm.manualFromCatalog(it) }, onDismiss = { showCatalog = false })
}

/** Pulsante foto: scatta con la fotocamera o scegli dalla galleria. */
@Composable
fun PhotoButton(vm: AppViewModel, current: String?, size: androidx.compose.ui.unit.Dp = 72.dp, onPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handle(uri: Uri) = scope.launch {
        vm.savePhoto(uri)?.let(onPhoto) ?: vm.toast("Foto non salvata")
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { handle(it) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { handle(it) } }

    Box(
        Modifier.size(size).clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .clickable { ask = true },
        contentAlignment = Alignment.Center,
    ) {
        if (current != null) Thumb(current, size)
        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📷")
            Text("Foto", style = MaterialTheme.typography.labelSmall)
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("Foto del pezzo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    ask = false
                    val f = vm.cameraFile()
                    val u = FileProvider.getUriForFile(context, "com.lelux.calc3d.files", f)
                    cameraUri = u
                    runCatching { camera.launch(u) }.onFailure { vm.toast("Fotocamera non disponibile") }
                }, Modifier.fillMaxWidth()) { Text("Scatta una foto") }
                OutlinedButton(onClick = { ask = false; gallery.launch("image/*") }, Modifier.fillMaxWidth()) { Text("Scegli dalla galleria") }
            }
        },
        confirmButton = { TextButton(onClick = { ask = false }) { Text("Annulla") } },
    )
}

/** Riquadro mostrato dopo l'importazione: i pezzi sono gia' nella lista, qui si controllano. */
@Composable
fun ImportedConfirm(vm: AppViewModel) {
    val parts by vm.lastImported.collectAsState()
    val data by vm.data.collectAsState()
    if (parts.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(if (parts.size == 1) "Pezzo aggiunto" else "${parts.size} piatti aggiunti", Modifier.weight(1f),
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            IconButton(onClick = { vm.lastImported.value = emptyList() }) { Icon(Icons.Filled.Close, "Chiudi") }
        }
        parts.forEach { p ->
            val cost = CostEngine.partCost(p, data)
            val missing = p.filaments.filter { u -> data.filaments.none { it.id == u.filamentId } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(p.thumbnailFile, 56.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("${Fmt.grams(p.totalGrams)} · ${Fmt.duration(p.printSeconds)} · costo ${Fmt.euro(cost.perPiece)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        p.filaments.forEach { ColorDot(it.colorHex, 11.dp) }
                        Spacer(Modifier.width(2.dp))
                        Text(
                            when {
                                p.source == PartSource.ESTIMATE -> "Stima dalla geometria"
                                missing.isNotEmpty() -> "${missing.size} filamenti non in magazzino"
                                else -> "Dati esatti, filamenti abbinati"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (missing.isNotEmpty() || p.source == PartSource.ESTIMATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                IconButton(onClick = { vm.editPart(p) }) { Icon(Icons.Filled.Edit, "Modifica") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { parts.firstOrNull()?.let { vm.editPart(it) } }, Modifier.weight(1f)) { Text("Correggi") }
            Button(onClick = { vm.lastImported.value = emptyList() }, Modifier.weight(1f)) { Text("Va bene") }
        }
    }
}
