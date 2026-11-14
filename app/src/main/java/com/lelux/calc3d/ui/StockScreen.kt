package com.lelux.calc3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.data.Defaults
import com.lelux.calc3d.data.Filament
import com.lelux.calc3d.data.Printer

@Composable
fun StockScreen(vm: AppViewModel, openInCalculator: () -> Unit) {
    val data by vm.data.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var editFil by remember { mutableStateOf<Filament?>(null) }
    var editPrinter by remember { mutableStateOf<Printer?>(null) }
    var confirmDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("Magazzino", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Filamenti ${data.filaments.size}") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Stampanti ${data.printers.size}") })
                Tab(tab == 2, { tab = 2 }, text = { Text("Catalogo ${data.catalog.size}") })
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.size(4.dp)) }
                when (tab) {
                    0 -> {
                        val groups = data.filaments.sortedWith(compareBy({ it.type }, { it.brand }, { it.colorName }))
                        items(groups, key = { it.id }) { f ->
                            ListRow(
                                leading = { ColorDot(f.colorHex, 28.dp) },
                                title = listOf(f.type, f.colorName).filter { it.isNotBlank() }.joinToString(" · "),
                                sub = listOf(f.brand, "${Fmt.num(f.pricePerKg)} €/kg", "densità ${Fmt.num(f.density)}").filter { it.isNotBlank() }.joinToString(" · "),
                                onClick = { editFil = f },
                                onDelete = { confirmDelete = "Eliminare ${f.label}?" to { vm.repo.deleteFilament(f.id) } },
                            )
                        }
                        if (data.filaments.isEmpty()) item { Empty("Nessun filamento. Aggiungi le bobine che usi con il loro prezzo al kg.") }
                    }
                    1 -> {
                        items(data.printers, key = { it.id }) { p ->
                            ListRow(
                                leading = null,
                                title = p.name + if (p.id == data.settings.defaultPrinterId) "  ★" else "",
                                sub = "${Fmt.euro(p.purchasePrice)} · ${p.powerWatts.toInt()} W · ${Fmt.euro(p.purchasePrice / p.lifetimeHours.coerceAtLeast(1.0) + p.maintenancePerHour)}/h macchina",
                                onClick = { editPrinter = p },
                                onDelete = if (data.printers.size > 1) ({ confirmDelete = "Eliminare ${p.name}?" to { vm.repo.deletePrinter(p.id) } }) else null,
                            )
                        }
                        item {
                            Text("★ = stampante predefinita per i nuovi pezzi. Tocca una stampante per modificarla.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    else -> {
                        items(data.catalog, key = { it.id }) { c ->
                            ListRow(
                                leading = { Thumb(c.part.thumbnailFile, 48.dp) },
                                title = c.part.name,
                                sub = "${Fmt.grams(c.part.totalGrams)} · ${Fmt.duration(c.part.printSeconds)} · salvato il ${Fmt.date(c.savedAt)}",
                                onClick = { vm.addFromCatalog(c.part); openInCalculator() },
                                onDelete = { confirmDelete = "Togliere ${c.part.name} dal catalogo?" to { vm.repo.deleteCatalog(c.id) } },
                            )
                        }
                        if (data.catalog.isEmpty()) item { Empty("Il catalogo raccoglie i pezzi che stampi spesso. Quando aggiungi un pezzo spunta \"Salva anche nel catalogo\".") }
                        else item { Text("Tocca un pezzo per aggiungerlo al preventivo aperto.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                item { Spacer(Modifier.size(88.dp)) }
            }
        }
        if (tab < 2) {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 0) editFil = Filament(colorName = "Bianco") else editPrinter = Printer(name = "Nuova stampante") },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(if (tab == 0) "Filamento" else "Stampante") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    editFil?.let { f -> FilamentDialog(f, onSave = { vm.repo.upsertFilament(it) }, onDismiss = { editFil = null }) }
    editPrinter?.let { p ->
        PrinterDialog(p, isDefault = data.settings.defaultPrinterId == p.id,
            onSave = { np, def ->
                vm.repo.upsertPrinter(np)
                if (def) vm.repo.updateSettings { it.copy(defaultPrinterId = np.id) }
            }, onDismiss = { editPrinter = null })
    }
    confirmDelete?.let { (msg, action) -> ConfirmDialog(msg, "L'operazione non si può annullare.", "Elimina", action, { confirmDelete = null }) }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ListRow(leading: (@Composable () -> Unit)?, title: String, sub: String, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Elimina") }
    }
}

@Composable
fun FilamentDialog(initial: Filament, onSave: (Filament) -> Unit, onDismiss: () -> Unit) {
    var f by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filamento") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Selector("Tipo", Defaults.filamentPresets, Defaults.filamentPresets.firstOrNull { it.type == f.type },
                    { it.type }, { pr -> f = f.copy(type = pr.type, density = pr.density, pricePerKg = if (f.pricePerKg <= 0) pr.pricePerKg else f.pricePerKg) }, Modifier.fillMaxWidth())
                TextIn("Tipo (testo libero)", f.type, { f = f.copy(type = it) })
                TextIn("Marca", f.brand, { f = f.copy(brand = it) }, placeholder = "Es. Bambu Lab, Sunlu, eSun")
                TextIn("Nome colore", f.colorName, { f = f.copy(colorName = it) })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Defaults.namedColors.forEach { (name, hex) ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(parseHex(hex))
                                .border(if (f.colorHex.equals(hex, true)) 3.dp else 1.dp,
                                    if (f.colorHex.equals(hex, true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { f = f.copy(colorHex = hex, colorName = if (f.colorName.isBlank() || Defaults.namedColors.any { it.first == f.colorName }) name else f.colorName) }
                        )
                    }
                }
                TextIn("Codice colore", f.colorHex, { f = f.copy(colorHex = it) }, placeholder = "#RRGGBB")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Prezzo", f.pricePerKg, { f = f.copy(pricePerKg = it) }, Modifier.weight(1f), suffix = "€/kg")
                    NumField("Densità", f.density, { f = f.copy(density = it) }, Modifier.weight(1f), suffix = "g/cm³")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(f.copy(colorHex = com.lelux.calc3d.io.ModelImporter.normalizeColor(f.colorHex))); onDismiss() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

@Composable
fun PrinterDialog(initial: Printer, isDefault: Boolean, onSave: (Printer, Boolean) -> Unit, onDismiss: () -> Unit) {
    var p by remember { mutableStateOf(initial) }
    var def by remember { mutableStateOf(isDefault) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stampante") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Selector("Modello (compila i dati)", Defaults.printerPresets, null, { it.name }, { pr ->
                    p = p.copy(name = pr.name, powerWatts = pr.powerWatts, avgVolumetricSpeed = pr.volumetricSpeed,
                        purchasePrice = if (p.purchasePrice <= 0) pr.suggestedPrice else p.purchasePrice)
                }, Modifier.fillMaxWidth())
                TextIn("Nome", p.name, { p = p.copy(name = it) })
                NumField("Prezzo d'acquisto", p.purchasePrice, { p = p.copy(purchasePrice = it) }, Modifier.fillMaxWidth(), suffix = "€")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Vita utile", p.lifetimeHours, { p = p.copy(lifetimeHours = it) }, Modifier.weight(1f), suffix = "h")
                    NumField("Manutenzione", p.maintenancePerHour, { p = p.copy(maintenancePerHour = it) }, Modifier.weight(1f), suffix = "€/h")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Consumo medio", p.powerWatts, { p = p.copy(powerWatts = it) }, Modifier.weight(1f), suffix = "W")
                    NumField("Velocità media", p.avgVolumetricSpeed, { p = p.copy(avgVolumetricSpeed = it) }, Modifier.weight(1f), suffix = "mm³/s")
                }
                Text("La velocità serve solo a stimare il tempo dagli STL. Manutenzione: ugelli, piatti, cinghie, lubrificante.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow("Stampante predefinita", null, def) { def = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(p, def); onDismiss() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
