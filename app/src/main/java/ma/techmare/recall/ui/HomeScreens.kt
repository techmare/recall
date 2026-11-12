package ma.techmare.recall.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ma.techmare.recall.data.Card
import ma.techmare.recall.fsrs.formatInterval
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(vm: AppViewModel, onReview: (Long?) -> Unit, onOpenDeck: (Long) -> Unit, onImport: () -> Unit, onNewDeck: () -> Unit) {
    val decks by vm.decks.collectAsState()
    val stats by vm.stats.collectAsState()
    val due = decks.sumOf { it.due }
    val newDue = decks.sumOf { it.newDue }
    val learn = decks.sumOf { it.counts.learnDue }
    val review = decks.sumOf { it.counts.reviewDue }
    val done = stats.todayDone

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = body(13, 500, C.Muted))
                Text(if (due > 0) "Ready to review?" else if (decks.isEmpty()) "Welcome to Recall" else "All caught up", style = display(26))
            }
            Row(Modifier.clip(RoundedCornerShape(50)).background(C.AmberBg).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, "Streak", tint = C.Amber, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stats.streak.toString(), style = body(14, 700, C.Amber))
            }
        }

        if (decks.isEmpty()) {
            Panel {
                Text("Start with a deck", style = display(22))
                Text("Import your Anki decks (.apkg) with all their cards, images and progress, or make a new deck and add cards yourself.",
                    style = body(14, 500, C.Muted))
                PrimaryButton("Import Anki deck", icon = Icons.Outlined.FileDownload, onClick = onImport)
                SecondaryButton("New deck", Modifier.fillMaxWidth(), icon = Icons.Filled.Add, onClick = onNewDeck)
            }
            return@Column
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Due today", style = body(13, 600, C.Muted))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(due.toString(), style = display(44, 700))
                        Text(if (due == 1) " card" else " cards", style = display(20, 500, C.Muted), modifier = Modifier.padding(bottom = 7.dp))
                    }
                    Text(if (due == 0) "Nothing left for today" else "About ${maxOf(1, (due * 8 + 30) / 60)} min", style = body(13, 500, C.Muted))
                }
                val frac = if (done + due == 0) 0f else done.toFloat() / (done + due)
                Ring(frac, label = "${(frac * 100).toInt()}%")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountChip("New", newDue, C.Blue, C.BlueBg, Modifier.weight(1f))
                CountChip("Learning", learn, C.Amber, C.AmberBg, Modifier.weight(1f))
                CountChip("Review", review, C.Green, C.GreenBg, Modifier.weight(1f))
            }
            PrimaryButton(if (due > 0) "Start review" else "Done for today", enabled = due > 0, icon = Icons.Filled.PlayArrow) { onReview(null) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("This week") { Text("${stats.weekDays.sum()} reviews", style = body(13, 500, C.Muted)) }
            val max = stats.weekDays.maxOrNull() ?: 0
            val today = LocalDate.now().minusDays(if (java.time.LocalTime.now().hour < 4) 1 else 0)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.weekDays.forEachIndexed { i, n ->
                    val day = today.minusDays((6 - i).toLong())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(intensity(n, max)))
                        Text(day.dayOfWeek.name.take(1), style = body(11, if (i == 6) 700 else 500, if (i == 6) C.Text else C.Muted))
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("Your decks") {
                Text("Import .apkg", style = body(13, 700, C.AccentSoft),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onImport).padding(horizontal = 6.dp, vertical = 8.dp))
            }
            decks.forEach { d ->
                DeckRowItem(d) { if (d.due > 0) onReview(d.id) else onOpenDeck(d.id) }
            }
        }
    }
}

@Composable
fun DeckRowItem(d: DeckRow, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(C.Surface).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DeckBadge(d.counts.name, d.counts.color)
        Column(Modifier.weight(1f)) {
            Text(d.counts.name, style = body(15, 700), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%,d cards".format(d.counts.total), style = body(12, 500, C.Muted))
        }
        DuePill(d.due)
    }
}

@Composable
fun DecksScreen(vm: AppViewModel, onOpenDeck: (Long) -> Unit, onImport: () -> Unit, onNewDeck: () -> Unit) {
    val decks by vm.decks.collectAsState()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Decks", style = display(30), modifier = Modifier.padding(top = 4.dp, bottom = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                SecondaryButton("New deck", Modifier.weight(1f), icon = Icons.Filled.Add, onClick = onNewDeck)
                SecondaryButton("Import", Modifier.weight(1f), icon = Icons.Outlined.FileDownload, onClick = onImport)
            }
        }
        if (decks.isEmpty()) item { Text("No decks yet.", style = body(14, 500, C.Muted)) }
        items(decks, key = { it.id }) { d -> DeckRowItem(d) { onOpenDeck(d.id) } }
    }
}

@Composable
fun DeckScreen(vm: AppViewModel, deckId: Long, onBack: () -> Unit, onReview: (Long) -> Unit, onEditCard: (Long, Long) -> Unit) {
    val decks by vm.decks.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId }
    var query by rememberSaveable { mutableStateOf("") }
    val cards by remember(query) { vm.searchCards(deckId, query) }.collectAsState(initial = emptyList())
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    if (deck == null) { LaunchedEffect(decks) { if (decks.isNotEmpty()) onBack() }; return }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { renaming = true }) { Icon(Icons.Outlined.Edit, "Rename deck", tint = C.Muted) }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "Delete deck", tint = C.Muted) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                DeckBadge(deck.counts.name, deck.counts.color, 52.dp)
                Column {
                    Text(deck.counts.name, style = display(24))
                    Text("%,d cards".format(deck.counts.total), style = body(13, 500, C.Muted))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CountChip("New", deck.newDue, C.Blue, C.BlueBg, Modifier.weight(1f))
                CountChip("Learning", deck.counts.learnDue, C.Amber, C.AmberBg, Modifier.weight(1f))
                CountChip("Review", deck.counts.reviewDue, C.Green, C.GreenBg, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (deck.due > 0) "Study ${deck.due} cards" else "Nothing due", enabled = deck.due > 0, icon = Icons.Filled.PlayArrow) { onReview(deckId) }
            Spacer(Modifier.height(10.dp))
            SecondaryButton("Add card", Modifier.fillMaxWidth(), icon = Icons.Filled.Add) { onEditCard(deckId, -1) }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search cards", style = body(14, 500, C.Muted)) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = C.Muted) },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                textStyle = body(15),
            )
            Spacer(Modifier.height(4.dp))
        }
        items(cards, key = { it.id }) { c -> CardListItem(c) { onEditCard(deckId, c.id) } }
    }

    if (renaming) NameDialog("Rename deck", deck.counts.name, onDismiss = { renaming = false }) { vm.renameDeck(deckId, it); renaming = false }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${deck.counts.name}?") },
        text = { Text("This deletes the deck, its ${deck.counts.total} cards and their review history. It can't be undone.") },
        confirmButton = { TextButton({ vm.deleteDeck(deckId); deleting = false; onBack() }) { Text("Delete", color = C.Rose) } },
        dismissButton = { TextButton({ deleting = false }) { Text("Cancel") } },
        containerColor = C.Surface,
    )
}

@Composable
private fun CardListItem(c: Card, onClick: () -> Unit) {
    val dueText = when (c.state) {
        0 -> "New"
        else -> {
            val left = c.due - System.currentTimeMillis()
            if (left <= 0) "Due now" else "In ${formatInterval(left)}"
        }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(C.Surface).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(htmlToText(c.front).replace(Regex("\\[sound:[^\\]]*\\]"), "").trim().ifEmpty { "(image)" }, style = body(15, 600), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(htmlToText(c.back).replace(Regex("\\[sound:[^\\]]*\\]"), "").trim(), style = body(13, 500, C.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(dueText, style = body(12, 600, if (c.state == 0) C.Blue else C.Muted))
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Accent, unfocusedBorderColor = C.Surface2,
    focusedContainerColor = C.Surface, unfocusedContainerColor = C.Surface,
    cursorColor = C.Accent, focusedTextColor = C.Text, unfocusedTextColor = C.Text,
    focusedLabelColor = C.AccentSoft, unfocusedLabelColor = C.Muted,
)

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, colors = fieldColors(),
                shape = RoundedCornerShape(14.dp))
        },
        confirmButton = { TextButton({ onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        containerColor = C.Surface,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEditorScreen(vm: AppViewModel, deckId: Long, cardId: Long, onBack: () -> Unit) {
    val decks by vm.decks.collectAsState()
    var selectedDeck by rememberSaveable { mutableStateOf(deckId) }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var card by remember { mutableStateOf<Card?>(null) }
    var loaded by rememberSaveable { mutableStateOf(cardId < 0) }
    var saved by remember { mutableStateOf(0) }
    var hasHtml by remember { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        if (cardId >= 0) {
            val c = vm.repo.dao.card(cardId)
            card = c
            if (!loaded && c != null) {
                hasHtml = Regex("<(img|div|span|table|b|i|u|font)[ >]", RegexOption.IGNORE_CASE).containsMatchIn(c.front + c.back)
                front = htmlToText(c.front); back = htmlToText(c.back); loaded = true
            }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = C.Text) }
            Text(if (cardId < 0) "New card" else "Edit card", style = display(22), modifier = Modifier.weight(1f))
            if (card != null) IconButton(onClick = { vm.deleteCard(cardId); onBack() }) {
                Icon(Icons.Outlined.Delete, "Delete card", tint = C.Muted)
            }
        }
        if (cardId < 0) {
            var open by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(open, { open = it }) {
                OutlinedTextField(
                    value = decks.firstOrNull { it.id == selectedDeck }?.counts?.name ?: "", onValueChange = {}, readOnly = true,
                    label = { Text("Deck") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(), colors = fieldColors(),
                    shape = RoundedCornerShape(14.dp), textStyle = body(15),
                )
                ExposedDropdownMenu(open, { open = false }, containerColor = C.Surface) {
                    decks.forEach { d -> DropdownMenuItem(text = { Text(d.counts.name) }, onClick = { selectedDeck = d.id; open = false }) }
                }
            }
        }
        if (hasHtml) Text("This card has formatting or images from Anki. Saving here keeps only the text.", style = body(13, 500, C.Amber))
        OutlinedTextField(front, { front = it }, label = { Text("Front") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(), shape = RoundedCornerShape(14.dp), textStyle = body(17))
        OutlinedTextField(back, { back = it }, label = { Text("Back") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(), shape = RoundedCornerShape(14.dp), textStyle = body(17))
        PrimaryButton(if (cardId < 0) "Add card" else "Save", enabled = front.isNotBlank() && back.isNotBlank()) {
            val c = card
            if (c != null) { vm.updateCard(c, front, back); onBack() }
            else { vm.addCard(selectedDeck, front, back); front = ""; back = ""; saved++ }
        }
        if (saved > 0) Text("Added $saved ${if (saved == 1) "card" else "cards"}. Keep going or go back.", style = body(13, 600, C.Green))
    }
}
