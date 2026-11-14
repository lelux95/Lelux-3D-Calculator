package com.lelux.calc3d.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lelux.calc3d.data.Defaults
import com.lelux.calc3d.data.Filament
import com.lelux.calc3d.data.Printer
import com.lelux.calc3d.data.Rounding
import com.lelux.calc3d.data.ThemeMode

private data class PrinterDraft(val preset: Defaults.PrinterPreset, val price: Double)
private data class FilDraft(val preset: Defaults.FilamentPreset, val selected: Boolean, val price: Double)

@Composable
fun Onboarding(vm: AppViewModel, onDone: () -> Unit) {
    val data by vm.data.collectAsState()
    var step by remember { mutableIntStateOf(0) }
    var s by remember { mutableStateOf(data.settings) }
    val printers = remember { mutableStateListOf(PrinterDraft(Defaults.printerPresets[0], Defaults.printerPresets[0].suggestedPrice)) }
    val fils = remember {
        mutableStateListOf<FilDraft>().apply {
            Defaults.filamentPresets.forEach { add(FilDraft(it, it.type == "PLA" || it.type == "PETG", it.pricePerKg)) }
        }
    }
    var channelIndex by remember { mutableIntStateOf(1) } // Vinted
    val steps = 6
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.saveLogo(it) } }

    BackHandler(enabled = step > 0) { step-- }

    fun finish() {
        val printerObjs = printers.map {
            Printer(name = it.preset.name, purchasePrice = it.price, powerWatts = it.preset.powerWatts, avgVolumetricSpeed = it.preset.volumetricSpeed)
        }.ifEmpty { listOf(Printer(name = "La mia stampante")) }
        val filObjs = fils.filter { it.selected }.map {
            Filament(type = it.preset.type, colorName = "", colorHex = "#FFFFFF", pricePerKg = it.price, density = it.preset.density)
        }.ifEmpty { listOf(Filament()) }
        vm.repo.update { d ->
            val channels = d.channels
            d.copy(
                printers = d.printers + printerObjs,
                filaments = d.filaments + filObjs,
                settings = s.copy(
                    onboardingDone = true,
                    business = d.settings.business.copy(
                        name = s.business.name, contact = s.business.contact, phone = s.business.phone,
                        email = s.business.email, city = s.business.city,
                    ),
                    defaultPrinterId = printerObjs.first().id,
                    defaultChannelId = channels.getOrNull(channelIndex)?.id ?: channels.firstOrNull()?.id,
                ),
            )
        }
        vm.resetDraft()
        onDone()
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().imePadding()
    ) {
        LinearProgressIndicator(progress = { (step + 1f) / steps }, modifier = Modifier.fillMaxWidth().padding(16.dp))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(24.dp))
                    Text("Benvenuto in", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Lelux 3D Calculator", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("Configuriamo l'app in pochi passaggi. Ogni valore arriva già compilato con un dato consigliato: confermalo o correggilo. Potrai cambiare tutto in seguito da Impostazioni e Magazzino.")
                    SectionCard("Tema") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ThemeMode.DARK to "Scuro", ThemeMode.LIGHT to "Chiaro", ThemeMode.SYSTEM to "Automatico").forEach { (m, l) ->
                                FilterChip(selected = s.theme == m, onClick = {
                                    s = s.copy(theme = m)
                                    vm.repo.updateSettings { it.copy(theme = m) }
                                }, label = { Text(l) })
                            }
                        }
                    }
                }
                1 -> {
                    Text("Le tue stampanti", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Scegli il modello: consumo elettrico e velocità si compilano da soli. Inserisci solo quanto l'hai pagata. Puoi selezionarne più di una.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Defaults.printerPresets.forEach { p ->
                        val idx = printers.indexOfFirst { it.preset == p }
                        val sel = idx >= 0
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .clickable { if (sel) printers.removeAt(idx) else printers.add(PrinterDraft(p, p.suggestedPrice)) }
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text("Consumo medio ${p.powerWatts.toInt()} W", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Checkbox(checked = sel, onCheckedChange = { if (sel) printers.removeAt(idx) else printers.add(PrinterDraft(p, p.suggestedPrice)) })
                            }
                            if (sel) {
                                NumField("Prezzo d'acquisto", printers[idx].price, { v -> printers[idx] = printers[idx].copy(price = v) }, Modifier.fillMaxWidth(), suffix = "€")
                            }
                        }
                    }
                }
                2 -> {
                    Text("Costi di produzione", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Valori consigliati: verifica soprattutto il prezzo della corrente sulla tua bolletta.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    NumField("Corrente elettrica", s.electricityPrice, { s = s.copy(electricityPrice = it) }, Modifier.fillMaxWidth(), suffix = "€/kWh",
                        supporting = "Riferimento ARERA IV trim. 2026: 0,43 €/kWh tasse incluse")
                    NumField("Il tuo tempo (manodopera)", s.laborRate, { s = s.copy(laborRate = it) }, Modifier.fillMaxWidth(), suffix = "€/h",
                        supporting = "Metti 0 se non vuoi conteggiarlo")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumField("Preparazione piatto", s.setupMinutesPerPlate, { s = s.copy(setupMinutesPerPlate = it) }, Modifier.weight(1f), suffix = "min")
                        NumField("Rifinitura pezzo", s.postMinutesPerPiece, { s = s.copy(postMinutesPerPiece = it) }, Modifier.weight(1f), suffix = "min")
                    }
                    NumField("Margine per stampe fallite", s.failureRatePercent, { s = s.copy(failureRatePercent = it) }, Modifier.fillMaxWidth(), suffix = "%",
                        supporting = "Ricarico su materiale ed energia per coprire gli errori")
                    NumField("Imballaggio per ordine", s.packagingPerOrder, { s = s.copy(packagingPerOrder = it) }, Modifier.fillMaxWidth(), suffix = "€")
                    NumField("Spazio e costi fissi", s.fixedCostPerHour, { s = s.copy(fixedCostPerHour = it) }, Modifier.fillMaxWidth(), suffix = "€/h di stampa")
                }
                3 -> {
                    Text("Filamenti", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Seleziona i materiali che usi e controlla il prezzo al kg. Colori e marche le aggiungi poi dal Magazzino.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    fils.forEachIndexed { i, f ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = f.selected, onCheckedChange = { fils[i] = f.copy(selected = it) })
                            Text(f.preset.type, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            NumField("€/kg", f.price, { v -> fils[i] = fils[i].copy(price = v) }, Modifier.width(120.dp), enabled = f.selected)
                        }
                    }
                }
                4 -> {
                    Text("Prezzo di vendita", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    NumField("Ricarico predefinito sul costo", s.defaultMarkup, { s = s.copy(defaultMarkup = it) }, Modifier.fillMaxWidth(), suffix = "%",
                        supporting = "Lo regoli poi per ogni preventivo, anche indicando l'utile che vuoi in euro")
                    Text("Canale di vendita principale", fontWeight = FontWeight.SemiBold)
                    data.channels.forEachIndexed { i, ch ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .border(if (channelIndex == i) 2.dp else 1.dp, if (channelIndex == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                                .clickable { channelIndex = i }.padding(14.dp),
                        ) {
                            Text(ch.name, Modifier.weight(1f))
                            Text(if (ch.percentFee == 0.0 && ch.fixedFee == 0.0) "nessuna commissione" else "${ch.percentFee}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("Vinted ed eBay non trattengono commissioni ai venditori privati: le paga l'acquirente. Puoi aggiungere altri canali con le loro commissioni dalle Impostazioni.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Arrotondamento del prezzo", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Rounding.NONE to "No", Rounding.HALF to "0,50", Rounding.EURO to "1 €", Rounding.NINETY to ",90").forEach { (r, l) ->
                            FilterChip(selected = s.rounding == r, onClick = { s = s.copy(rounding = r) }, label = { Text(l) })
                        }
                    }
                }
                5 -> {
                    Text("La tua attività", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Facoltativo: questi dati compaiono sui preventivi PDF. Puoi lasciarli vuoti e compilarli più tardi.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val logo = data.settings.business.logoFile
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Thumb(logo, 64.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Logo", fontWeight = FontWeight.SemiBold)
                            Text("PNG con sfondo trasparente, almeno 512×512 px", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { logoPicker.launch("image/*") }) { Text(if (logo == null) "Carica" else "Cambia") }
                    }
                    TextIn("Nome attività", s.business.name, { s = s.copy(business = s.business.copy(name = it)) }, placeholder = "Es. Lelux 3D")
                    TextIn("Referente", s.business.contact, { s = s.copy(business = s.business.copy(contact = it)) })
                    TextIn("Telefono", s.business.phone, { s = s.copy(business = s.business.copy(phone = it)) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Phone)
                    TextIn("E-mail", s.business.email, { s = s.copy(business = s.business.copy(email = it)) }, keyboard = androidx.compose.ui.text.input.KeyboardType.Email)
                    TextIn("Città", s.business.city, { s = s.copy(business = s.business.copy(city = it)) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton(onClick = { step-- }) { Text("Indietro") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (step < steps - 1) step++ else finish() },
                enabled = step != 1 || printers.isNotEmpty(),
            ) { Text(if (step < steps - 1) "Avanti" else "Inizia") }
        }
        Box(Modifier.size(0.dp))
    }
}
