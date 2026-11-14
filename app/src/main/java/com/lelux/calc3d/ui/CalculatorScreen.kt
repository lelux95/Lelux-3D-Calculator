package com.lelux.calc3d.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.lelux.calc3d.calc.QuoteResult
import com.lelux.calc3d.data.Part
import com.lelux.calc3d.data.PartSource
import com.lelux.calc3d.data.Quote
import com.lelux.calc3d.data.QuoteStatus
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CalculatorScreen(vm: AppViewModel) {
    val data by vm.data.collectAsState()
    val quote by vm.draft.collectAsState()
    val result = remember(quote, data) { CostEngine.compute(quote, data) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showCatalog by remember { mutableStateOf(false) }
    var showBreakdown by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var viewerPart by remember { mutableStateOf<Part?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importUri(it) }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Intestazione: nuovo calcolo + stampante e materiale per i nuovi pezzi
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { if (quote.parts.isNotEmpty()) confirmNew = true else vm.resetDraft() },
                shape = RoundedCornerShape(18.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text("Nuovo calcolo", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Filled.Add, null)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    if (quote.number.isBlank()) "Preventivo non salvato" else quote.number,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (quote.number.isBlank()) "${quote.parts.size} pezzi" else "${quote.status.label} · ${quote.parts.size} pezzi",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        DefaultsBar(vm)

        AddPanel(vm, onPickFile = { picker.launch(arrayOf("*/*")) }, onOpenCatalog = { showCatalog = true })

        ImportedConfirm(vm)

        // Pezzi
        if (quote.parts.isNotEmpty()) {
            SectionCard("Prodotti nel preventivo") {
                result.parts.forEachIndexed { i, pc ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PartRow(
                        part = pc.part,
                        cost = pc.total,
                        price = result.lineTotals.getOrElse(i) { 0.0 },
                        onView = { viewerPart = pc.part },
                        onEdit = { vm.editPart(pc.part) },
                        onDelete = { vm.removePart(pc.part.id) },
                        onQty = { q -> vm.setQuantity(pc.part.id, q) },
                    )
                }
                Text(
                    "Totale: ${Fmt.grams(result.totalGrams)} · ${Fmt.hours(result.totalHours)} di stampa",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PricePanel(vm, quote, result, onBreakdown = { showBreakdown = true })
            DetailsPanel(vm, quote, onSave = {
                vm.saveDraft()?.let { vm.toast("Preventivo ${it.number} salvato nello storico") }
            }, onShare = { showShare = true })
        }
        Spacer(Modifier.height(24.dp))
    }

    if (confirmNew) ConfirmDialog("Nuovo calcolo", "Il preventivo attuale verrà chiuso e si ripartirà da zero. Se non l'hai salvato, le modifiche andranno perse.", "Nuovo calcolo", { vm.resetDraft() }, { confirmNew = false })
    if (showCatalog) CatalogPicker(vm, onPick = { vm.addFromCatalog(it) }, onDismiss = { showCatalog = false })
    if (showBreakdown) BreakdownDialog(result, data.settings.vatPercent, onDismiss = { showBreakdown = false })
    if (showShare) ShareDialog(vm, onDismiss = { showShare = false }, scope = scope, context = context)
    viewerPart?.let { p -> ViewerDialog(vm, p, onDismiss = { viewerPart = null }) }
}

@Composable
private fun AddButton(title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PartRow(part: Part, cost: Double, price: Double, onView: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onQty: (Int) -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Thumb(part.thumbnailFile, 58.dp, Modifier.clickable(onClick = onView))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clickable(onClick = onEdit)) {
            Text(part.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                part.filaments.take(8).forEach { ColorDot(it.colorHex, 11.dp) }
                Spacer(Modifier.width(2.dp))
                Text("${Fmt.grams(part.totalGrams)} · ${Fmt.duration(part.printSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (if (part.source == PartSource.ESTIMATE) "⚠ Stima · " else "") + "costo ${Fmt.euro(cost)} · prezzo ${Fmt.euro(price)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (part.source == PartSource.ESTIMATE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallStep("−") { onQty(part.quantity - 1) }
                Text("x${part.quantity}", Modifier.padding(horizontal = 4.dp), fontWeight = FontWeight.Bold)
                SmallStep("+") { onQty(part.quantity + 1) }
            }
            Row {
                IconButton(onClick = onEdit, Modifier.size(36.dp)) { Icon(Icons.Filled.Edit, "Modifica", Modifier.size(18.dp)) }
                IconButton(onClick = { confirm = true }, Modifier.size(36.dp)) { Icon(Icons.Filled.Delete, "Elimina", Modifier.size(18.dp)) }
            }
        }
    }
    if (confirm) ConfirmDialog("Rimuovere il pezzo?", part.name, "Rimuovi", onDelete, { confirm = false })
}

@Composable
private fun SmallStep(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PricePanel(vm: AppViewModel, quote: Quote, r: QuoteResult, onBreakdown: () -> Unit) {
    val data by vm.data.collectAsState()
    val s = data.settings
    val channel = CostEngine.channelFor(quote, data)
    SectionCard("Prezzo") {
        Selector("Canale di vendita", data.channels, channel, { c ->
            c.name + if (c.percentFee > 0 || c.fixedFee > 0) " (${Fmt.num1(c.percentFee)}% + ${Fmt.euro(c.fixedFee)})" else ""
        }, { c -> vm.updateDraft { it.copy(channelId = c.id) } }, Modifier.fillMaxWidth())

        val byProfit = quote.targetProfit != null
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !byProfit, onClick = { vm.updateDraft { it.copy(targetProfit = null, markupPercent = r.markupPercent.coerceAtLeast(0.0)) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Ricarico %") }
            SegmentedButton(selected = byProfit, onClick = { vm.updateDraft { it.copy(targetProfit = (r.profit * 100).roundToInt() / 100.0) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Utile in €") }
        }

        if (!byProfit) {
            val m = quote.markupPercent
            val band = when {
                m < s.minSafeMarkup -> "Sotto il minimo di sicurezza (${Fmt.num1(s.minSafeMarkup)}%)"
                m >= s.b2cMin && m <= s.b2cMax -> "Fascia privati (B2C)"
                m >= s.b2bMin && m <= s.b2bMax -> "Fascia aziende (B2B)"
                else -> ""
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallStep("−") { vm.updateDraft { it.copy(markupPercent = (it.markupPercent - 1).coerceAtLeast(0.0)) } }
                Slider(
                    value = m.toFloat().coerceIn(0f, s.sliderMax.toFloat().coerceAtLeast(1f)),
                    onValueChange = { v -> vm.updateDraft { it.copy(markupPercent = v.roundToInt().toDouble()) } },
                    valueRange = 0f..s.sliderMax.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                SmallStep("+") { vm.updateDraft { it.copy(markupPercent = it.markupPercent + 1) } }
            }
            if (band.isNotBlank()) Text(band, style = MaterialTheme.typography.labelMedium,
                color = if (m < s.minSafeMarkup) Bad else MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            NumField("Utile netto desiderato", quote.targetProfit ?: 0.0, { v -> vm.updateDraft { it.copy(targetProfit = v) } }, Modifier.fillMaxWidth(), suffix = "€",
                supporting = "Il prezzo viene calcolato per farti incassare esattamente questo utile")
        }
        if (r.markupClamped) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = Bad, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
            Text("Protezione profitto attiva: applicato il ricarico minimo", style = MaterialTheme.typography.labelMedium, color = Bad)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            StatTile("Utile", Fmt.euro(r.profit), Modifier.weight(1f))
            StatTile("Prezzo", Fmt.euro(r.price), Modifier.weight(1.4f), highlight = true)
            StatTile("Ricarico", Fmt.percent(r.markupPercent), Modifier.weight(1f))
        }
        Text(
            "Costo di produzione ${Fmt.euro(r.productionCost)} · margine ${Fmt.percent(r.marginPercent)} · ${Fmt.euro(r.profitPerHour)}/h di stampa",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onBreakdown, Modifier.fillMaxWidth()) { Text("Composizione del prezzo") }
    }
}

@Composable
private fun DetailsPanel(vm: AppViewModel, quote: Quote, onSave: () -> Unit, onShare: () -> Unit) {
    SectionCard("Dettagli preventivo") {
        TextIn("Titolo", quote.title, { v -> vm.updateDraft { it.copy(title = v) } }, placeholder = quote.parts.joinToString(" + ") { it.name })
        TextIn("Cliente", quote.customer, { v -> vm.updateDraft { it.copy(customer = v) } })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IntField("Consegna", quote.deliveryDays, { v -> vm.updateDraft { it.copy(deliveryDays = v) } }, Modifier.weight(1f), suffix = "giorni")
            IntField("Validità", quote.validityDays, { v -> vm.updateDraft { it.copy(validityDays = v) } }, Modifier.weight(1f), suffix = "giorni")
        }
        TextIn("Note per il cliente", quote.notes, { v -> vm.updateDraft { it.copy(notes = v) } }, singleLine = false)
        Text("Stato", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuoteStatus.entries.forEach { st ->
                FilterChip(selected = quote.status == st, onClick = { vm.updateDraft { it.copy(status = st) } }, label = { Text(st.label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onSave, Modifier.weight(1f)) { Text("Salva") }
            Button(onClick = onShare, Modifier.weight(1f)) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Condividi") }
        }
    }
}

@Composable
fun BreakdownDialog(r: QuoteResult, vatPercent: Double, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Composizione del prezzo") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val p = if (r.price > 0) r.price else 1.0
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatTile("Produzione", Fmt.percent(r.productionCost / p * 100), Modifier.weight(1f))
                    StatTile("Tasse e comm.", Fmt.percent((r.channelFees + r.vat) / p * 100), Modifier.weight(1f))
                    StatTile("Utile", Fmt.percent(r.profit / p * 100), Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Header("1. Costo di produzione")
                Line("Filamento", r.material)
                Line("Energia elettrica", r.energy)
                Line("Ammortamento e manutenzione", r.depreciation)
                Line("Manodopera", r.labor)
                Line("Spazio e costi fissi", r.fixed)
                Line("Margine stampe fallite", r.failure)
                Line("Imballaggio", r.packaging)
                Line("Altri costi", r.other)
                Line("Totale produzione", r.productionCost, bold = true)
                Header("2. Utile")
                Line("Ricarico ${Fmt.percent(r.markupPercent)}", r.profit, bold = true)
                Line("Margine netto", null, Fmt.percent(r.marginPercent))
                Line("Utile per ora di stampa", null, "${Fmt.euro(r.profitPerHour)}/h")
                Header("3. Commissioni e tasse")
                Line("Commissioni canale", r.channelFees)
                Line("IVA ${Fmt.num1(vatPercent)}%", r.vat)
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Line("PREZZO DI VENDITA", r.price, bold = true)
            }
        },
    )
}

@Composable private fun Header(t: String) {
    Text(t, Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable private fun Line(label: String, value: Double?, text: String? = null, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(text ?: Fmt.euro(value ?: 0.0), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun CatalogPicker(vm: AppViewModel, onPick: (Part) -> Unit, onDismiss: () -> Unit) {
    val data by vm.data.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text("Catalogo pezzi") },
        text = {
            if (data.catalog.isEmpty()) Text("Il catalogo è vuoto. Quando aggiungi un pezzo, spunta \"Salva anche nel catalogo\" per ritrovarlo qui.")
            else LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(data.catalog, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onPick(c.part); onDismiss() }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Thumb(c.part.thumbnailFile, 46.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.part.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${Fmt.grams(c.part.totalGrams)} · ${Fmt.duration(c.part.printSeconds)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
    )
}

fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "com.lelux.calc3d.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}

fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(intent, "Invia preventivo"))
}

@Composable
fun ShareDialog(vm: AppViewModel, onDismiss: () -> Unit, scope: kotlinx.coroutines.CoroutineScope, context: Context, quoteOverride: Quote? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        title = { Text("Condividi preventivo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Il preventivo viene salvato nello storico e riceve un numero.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ShareOption("Proposta in PDF", "Documento con logo, miniature e condizioni") {
                    val q = quoteOverride ?: vm.saveDraft()
                    if (q != null) scope.launch {
                        runCatching { vm.makePdf(q) }
                            .onSuccess { f ->
                                if (q.status == QuoteStatus.DRAFT) vm.setStatus(q, QuoteStatus.SENT)
                                if (quoteOverride == null) vm.updateDraft { it.copy(status = if (it.status == QuoteStatus.DRAFT) QuoteStatus.SENT else it.status) }
                                shareFile(context, f, "application/pdf", "Preventivo ${q.number}")
                            }
                            .onFailure { vm.toast("Errore nel PDF: ${it.message}") }
                    }
                    onDismiss()
                }
                ShareOption("Messaggio di testo", "Riepilogo con grassetto ed emoji per WhatsApp e Telegram") {
                    val q = quoteOverride ?: vm.saveDraft()
                    if (q != null) {
                        if (q.status == QuoteStatus.DRAFT) vm.setStatus(q, QuoteStatus.SENT)
                        if (quoteOverride == null) vm.updateDraft { it.copy(status = if (it.status == QuoteStatus.DRAFT) QuoteStatus.SENT else it.status) }
                        shareText(context, vm.shareText(q))
                    }
                    onDismiss()
                }
            }
        },
    )
}

@Composable
private fun ShareOption(title: String, sub: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(14.dp)
    ) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
