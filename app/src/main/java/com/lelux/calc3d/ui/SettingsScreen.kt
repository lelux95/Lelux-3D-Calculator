package com.lelux.calc3d.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.data.Defaults
import com.lelux.calc3d.data.Rounding
import com.lelux.calc3d.data.SalesChannel
import com.lelux.calc3d.data.Settings
import com.lelux.calc3d.data.ThemeMode
import java.io.File

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val s = data.settings
    val b = s.business
    val context = LocalContext.current
    fun set(t: (Settings) -> Settings) = vm.repo.updateSettings(t)
    var editChannel by remember { mutableStateOf<SalesChannel?>(null) }
    var confirmImport by remember { mutableStateOf<String?>(null) }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.saveLogo(it) } }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> runCatching { context.contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()?.let { confirmImport = it } }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Impostazioni", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        SectionCard("Aspetto") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.DARK to "Scuro", ThemeMode.LIGHT to "Chiaro", ThemeMode.SYSTEM to "Automatico").forEach { (m, l) ->
                    FilterChip(selected = s.theme == m, onClick = { set { it.copy(theme = m) } }, label = { Text(l) })
                }
            }
        }

        SectionCard("La mia attività") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(b.logoFile, 64.dp, Modifier.clickable { logoPicker.launch("image/*") })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Logo per i preventivi", fontWeight = FontWeight.SemiBold)
                    Text("PNG trasparente, almeno 512×512 px (o 1200×400 se rettangolare)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { logoPicker.launch("image/*") }) { Text(if (b.logoFile == null) "Carica logo" else "Cambia logo") }
                if (b.logoFile != null) TextButton(onClick = { set { it.copy(business = it.business.copy(logoFile = null)) } }) { Text("Rimuovi") }
            }
            Text("Colore del brand (PDF)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (listOf("#FF8A3D", "#9C5C46", "#C2591B", "#1E63D6", "#2E7D5B", "#7E57C2", "#D32F2F", "#1A1A1A", "#546E7A")).forEach { hex ->
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(parseHex(hex))
                            .border(if (b.brandColor.equals(hex, true)) 3.dp else 1.dp,
                                if (b.brandColor.equals(hex, true)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { set { it.copy(business = it.business.copy(brandColor = hex)) } }
                    )
                }
            }
            TextIn("Codice colore", b.brandColor, { v -> set { it.copy(business = it.business.copy(brandColor = v)) } })
            TextIn("Nome attività", b.name, { v -> set { it.copy(business = it.business.copy(name = v)) } })
            TextIn("Referente", b.contact, { v -> set { it.copy(business = it.business.copy(contact = v)) } })
            TextIn("Codice fiscale / P.IVA", b.taxCode, { v -> set { it.copy(business = it.business.copy(taxCode = v)) } })
            TextIn("Telefono", b.phone, { v -> set { it.copy(business = it.business.copy(phone = v)) } }, keyboard = KeyboardType.Phone)
            TextIn("E-mail", b.email, { v -> set { it.copy(business = it.business.copy(email = v)) } }, keyboard = KeyboardType.Email)
            TextIn("Sito web / profilo", b.website, { v -> set { it.copy(business = it.business.copy(website = v)) } }, keyboard = KeyboardType.Uri)
            TextIn("Città", b.city, { v -> set { it.copy(business = it.business.copy(city = v)) } })
        }

        SectionCard("Costi di produzione") {
            NumField("Corrente elettrica", s.electricityPrice, { v -> set { it.copy(electricityPrice = v) } }, Modifier.fillMaxWidth(), suffix = "€/kWh")
            NumField("Manodopera", s.laborRate, { v -> set { it.copy(laborRate = v) } }, Modifier.fillMaxWidth(), suffix = "€/h")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Preparazione piatto", s.setupMinutesPerPlate, { v -> set { it.copy(setupMinutesPerPlate = v) } }, Modifier.weight(1f), suffix = "min")
                NumField("Rifinitura pezzo", s.postMinutesPerPiece, { v -> set { it.copy(postMinutesPerPiece = v) } }, Modifier.weight(1f), suffix = "min")
            }
            NumField("Spazio e costi fissi", s.fixedCostPerHour, { v -> set { it.copy(fixedCostPerHour = v) } }, Modifier.fillMaxWidth(), suffix = "€/h")
            NumField("Margine stampe fallite", s.failureRatePercent, { v -> set { it.copy(failureRatePercent = v) } }, Modifier.fillMaxWidth(), suffix = "%")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Imballaggio", s.packagingPerOrder, { v -> set { it.copy(packagingPerOrder = v) } }, Modifier.weight(1f), suffix = "€/ordine")
                NumField("Altri costi", s.otherCostsPerOrder, { v -> set { it.copy(otherCostsPerOrder = v) } }, Modifier.weight(1f), suffix = "€/ordine")
            }
            NumField("IVA da aggiungere al prezzo", s.vatPercent, { v -> set { it.copy(vatPercent = v) } }, Modifier.fillMaxWidth(), suffix = "%",
                supporting = "Lascia 0 se vendi come privato o in regime forfettario")
        }

        SectionCard("Ricarico") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Predefinito", s.defaultMarkup, { v -> set { it.copy(defaultMarkup = v) } }, Modifier.weight(1f), suffix = "%")
                NumField("Massimo slider", s.sliderMax, { v -> set { it.copy(sliderMax = v.coerceAtLeast(10.0)) } }, Modifier.weight(1f), suffix = "%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Privati (B2C) da", s.b2cMin, { v -> set { it.copy(b2cMin = v) } }, Modifier.weight(1f), suffix = "%")
                NumField("a", s.b2cMax, { v -> set { it.copy(b2cMax = v) } }, Modifier.weight(1f), suffix = "%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Aziende (B2B) da", s.b2bMin, { v -> set { it.copy(b2bMin = v) } }, Modifier.weight(1f), suffix = "%")
                NumField("a", s.b2bMax, { v -> set { it.copy(b2bMax = v) } }, Modifier.weight(1f), suffix = "%")
            }
            NumField("Ricarico minimo di sicurezza", s.minSafeMarkup, { v -> set { it.copy(minSafeMarkup = v) } }, Modifier.fillMaxWidth(), suffix = "%")
            SwitchRow("Protezione del profitto", "Il ricarico non scende mai sotto il minimo di sicurezza", s.profitProtection) { v -> set { it.copy(profitProtection = v) } }
            Text("Arrotondamento del prezzo finale", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Rounding.entries.forEach { r -> FilterChip(selected = s.rounding == r, onClick = { set { it.copy(rounding = r) } }, label = { Text(r.label) }) }
            }
        }

        SectionCard("Canali di vendita") {
            data.channels.forEach { ch ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = (s.defaultChannelId ?: data.channels.firstOrNull()?.id) == ch.id, onClick = { set { it.copy(defaultChannelId = ch.id) } })
                    Column(Modifier.weight(1f).clickable { editChannel = ch }) {
                        Text(ch.name, fontWeight = FontWeight.SemiBold)
                        Text(if (ch.percentFee == 0.0 && ch.fixedFee == 0.0) "Nessuna commissione" else "${Fmt.num1(ch.percentFee)}% + ${Fmt.euro(ch.fixedFee)} a ordine",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (data.channels.size > 1) IconButton(onClick = { vm.repo.deleteChannel(ch.id) }) { Icon(Icons.Filled.Delete, "Elimina") }
                }
            }
            TextButton(onClick = { editChannel = SalesChannel(name = "Nuovo canale") }) { Text("+ Aggiungi canale") }
            Text("Il pallino indica il canale predefinito per i nuovi preventivi. Verifica periodicamente le commissioni sul sito di ogni piattaforma.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard("Preventivi") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextIn("Prefisso", s.quotePrefix, { v -> set { it.copy(quotePrefix = v.uppercase().take(6)) } }, Modifier.weight(1f))
                IntField("Prossimo n.", s.nextQuoteNumber, { v -> set { it.copy(nextQuoteNumber = v.coerceAtLeast(1)) } }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IntField("Validità", s.validityDays, { v -> set { it.copy(validityDays = v) } }, Modifier.weight(1f), suffix = "giorni")
                IntField("Consegna", s.deliveryDays, { v -> set { it.copy(deliveryDays = v) } }, Modifier.weight(1f), suffix = "giorni")
            }
            TextIn("Condizioni di pagamento", s.paymentTerms, { v -> set { it.copy(paymentTerms = v) } }, singleLine = false)
            TextIn("Garanzia", s.warrantyText, { v -> set { it.copy(warrantyText = v) } }, singleLine = false)
            TextIn("Altri termini", s.termsText, { v -> set { it.copy(termsText = v) } }, singleLine = false)
            SwitchRow("Miniature dei modelli nel PDF", null, s.showThumbnailsInPdf) { v -> set { it.copy(showThumbnailsInPdf = v) } }
            SwitchRow("Peso dei pezzi nel PDF", null, s.showWeightInPdf) { v -> set { it.copy(showWeightInPdf = v) } }
        }

        SectionCard("Stima da STL") {
            Text("Usata quando il file non contiene i dati dello slicer.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumField("Riempimento", s.estimateInfill, { v -> set { it.copy(estimateInfill = v) } }, Modifier.weight(1f), suffix = "%")
                NumField("Spessore pareti", s.estimateWallMm, { v -> set { it.copy(estimateWallMm = v) } }, Modifier.weight(1f), suffix = "mm")
            }
        }

        SectionCard("Backup dei dati") {
            Text("Salva in un file stampanti, filamenti, impostazioni, catalogo e preventivi, per spostarli su un altro telefono. Logo e miniature non sono inclusi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    val f = File(vm.repo.sharedDir, "Lelux3D_backup_${Fmt.date(System.currentTimeMillis()).replace("/", "-")}.json")
                    f.writeText(vm.repo.exportJson())
                    shareFile(context, f, "application/json", "Backup Lelux 3D Calculator")
                }, Modifier.weight(1f)) { Text("Esporta") }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "*/*")) }, Modifier.weight(1f)) { Text("Importa") }
            }
        }

        Text("Lelux 3D Calculator 0.1.0", Modifier.fillMaxWidth().padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(16.dp))
    }

    editChannel?.let { ch -> ChannelDialog(ch, onSave = { vm.repo.upsertChannel(it) }, onDismiss = { editChannel = null }) }
    confirmImport?.let { text ->
        ConfirmDialog("Importare il backup?", "I dati attuali verranno sostituiti da quelli del file.", "Importa", {
            if (vm.repo.importJson(text)) { vm.resetDraft(); vm.toast("Backup importato") } else vm.toast("File di backup non valido")
        }, { confirmImport = null })
    }
}

@Composable
private fun ChannelDialog(initial: SalesChannel, onSave: (SalesChannel) -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canale di vendita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextIn("Nome", c.name, { c = c.copy(name = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumField("Commissione", c.percentFee, { c = c.copy(percentFee = it) }, Modifier.weight(1f), suffix = "%")
                    NumField("Quota fissa", c.fixedFee, { c = c.copy(fixedFee = it) }, Modifier.weight(1f), suffix = "€")
                }
                Text("Commissioni trattenute al venditore sul prezzo di vendita.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(c); onDismiss() }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}
