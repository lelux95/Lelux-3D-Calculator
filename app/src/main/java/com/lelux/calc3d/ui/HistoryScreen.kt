package com.lelux.calc3d.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lelux.calc3d.calc.Fmt
import com.lelux.calc3d.data.Quote
import com.lelux.calc3d.data.QuoteStatus

fun statusColor(s: QuoteStatus): Color = when (s) {
    QuoteStatus.DRAFT -> Color(0xFF8A8F98)
    QuoteStatus.SENT -> Color(0xFF4F8EF7)
    QuoteStatus.ACCEPTED -> Good
    QuoteStatus.REJECTED -> Bad
}

@Composable
fun HistoryScreen(vm: AppViewModel, openInCalculator: () -> Unit) {
    val data by vm.data.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<QuoteStatus?>(null) }
    var selected by remember { mutableStateOf<Quote?>(null) }
    var sharing by remember { mutableStateOf<Quote?>(null) }
    var deleting by remember { mutableStateOf<Quote?>(null) }

    val quotes = data.quotes.sortedByDescending { it.createdAt }
    val accepted = quotes.filter { it.status == QuoteStatus.ACCEPTED }
    val shown = quotes.filter { q ->
        (filter == null || q.status == filter) &&
            (query.isBlank() || listOf(q.number, q.customer, q.title).any { it.contains(query, ignoreCase = true) })
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Storico preventivi", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${quotes.size} preventivi", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = {
                    if (quotes.isEmpty()) vm.toast("Nessun preventivo da esportare")
                    else shareFile(context, vm.csvExport(), "text/csv", "Storico preventivi")
                }) { Text("Esporta CSV") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Vendite", Fmt.euro(accepted.sumOf { it.savedPrice }), Modifier.weight(1f), sub = "${accepted.size} accettati")
                StatTile("Utile", Fmt.euro(accepted.sumOf { it.savedProfit }), Modifier.weight(1f),
                    sub = if (accepted.sumOf { it.savedPrice } > 0) "margine ${Fmt.percent(accepted.sumOf { it.savedProfit } / accepted.sumOf { it.savedPrice } * 100)}" else "—")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Preventivato", Fmt.euro(quotes.sumOf { it.savedPrice }), Modifier.weight(1f), sub = "${quotes.size} totali")
                val sent = quotes.count { it.status != QuoteStatus.DRAFT }
                StatTile("Conversione", if (sent > 0) Fmt.percent(accepted.size * 100.0 / sent) else "—", Modifier.weight(1f), sub = "accettati / inviati")
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                placeholder = { Text("Cerca per cliente, titolo o numero") },
                singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Tutti ${quotes.size}") })
                QuoteStatus.entries.forEach { st ->
                    FilterChip(selected = filter == st, onClick = { filter = st }, label = { Text("${st.label} ${quotes.count { it.status == st }}") })
                }
            }
        }
        if (shown.isEmpty()) item {
            Text(
                if (quotes.isEmpty()) "Non hai ancora salvato preventivi. Dal Calcolatore premi Salva o Condividi." else "Nessun risultato",
                Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(shown, key = { it.id }) { q ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { selected = q }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(q.parts.firstOrNull()?.thumbnailFile, 54.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(q.title.ifBlank { "Preventivo" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${q.number} · ${Fmt.date(q.createdAt)}" + if (q.customer.isNotBlank()) " · ${q.customer}" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(q.status.label, style = MaterialTheme.typography.labelMedium, color = statusColor(q.status), fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Fmt.euro(q.savedPrice), fontWeight = FontWeight.Bold)
                    Text("utile ${Fmt.euro(q.savedProfit)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Spacer(Modifier.padding(12.dp)) }
    }

    selected?.let { q ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${q.number} · ${Fmt.euro(q.savedPrice)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(q.title, fontWeight = FontWeight.SemiBold)
                    if (q.customer.isNotBlank()) Text("Cliente: ${q.customer}")
                    Text("Stato", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuoteStatus.entries.forEach { st ->
                            FilterChip(selected = q.status == st, onClick = {
                                vm.setStatus(q, st); selected = q.copy(status = st)
                                if (vm.draft.value.id == q.id) vm.updateDraft { it.copy(status = st) }
                            }, label = { Text(st.label) })
                        }
                    }
                    ActionRow("Apri nel calcolatore") { vm.openQuote(q); selected = null; openInCalculator() }
                    ActionRow("Condividi (PDF o testo)") { sharing = q; selected = null }
                    ActionRow("Duplica come nuovo preventivo") { vm.duplicateQuote(q); selected = null; openInCalculator() }
                    ActionRow("Elimina", danger = true) { deleting = q; selected = null }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Chiudi") } },
        )
    }
    sharing?.let { q -> ShareDialog(vm, onDismiss = { sharing = null }, scope = scope, context = context, quoteOverride = q) }
    deleting?.let { q ->
        ConfirmDialog("Eliminare il preventivo?", "${q.number} – ${q.title}", "Elimina", {
            vm.repo.deleteQuote(q.id)
            if (vm.draft.value.id == q.id) vm.resetDraft()
        }, { deleting = null })
    }
}

@Composable
private fun ActionRow(label: String, danger: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick).padding(14.dp),
        color = if (danger) Bad else MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}
