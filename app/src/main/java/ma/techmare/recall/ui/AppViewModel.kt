package ma.techmare.recall.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ma.techmare.recall.data.Card
import ma.techmare.recall.data.DeckCounts
import ma.techmare.recall.data.Repository
import ma.techmare.recall.data.StudyDay
import ma.techmare.recall.data.Undo
import ma.techmare.recall.fsrs.Grade
import ma.techmare.recall.importer.Importer

data class DeckRow(val counts: DeckCounts, val newDue: Int) {
    val id get() = counts.id
    val due get() = counts.learnDue + counts.reviewDue + newDue
}

data class StatsData(
    val todayDone: Int = 0,
    val weekDays: List<Int> = List(7) { 0 },   // oldest → today
    val weeks12: List<Int> = List(84) { 0 },   // oldest → today
    val retention: Int? = null,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val total84: Int = 0,
    val forecast: List<Int> = List(7) { 0 },  // tomorrow → +7
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = Repository(app)
    private val dao = repo.dao
    private val importer = Importer(app, repo)

    private val _settingsVersion = MutableStateFlow(0)
    private val ticker = flow { while (true) { emit(System.currentTimeMillis()); delay(30_000) } }

    val decks: StateFlow<List<DeckRow>> = combine(ticker, _settingsVersion) { now, _ -> now }
        .flatMapLatest { now -> dao.deckCounts(now, StudyDay.start(now), StudyDay.end(now)) }
        .map { list ->
            val limit = repo.settings.newPerDay
            list.map { DeckRow(it, minOf(it.newCount, (limit - it.newToday).coerceAtLeast(0))) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<StatsData> = ticker.flatMapLatest { now ->
        val off = StudyDay.offset(now)
        val today = StudyDay.dayNumber(now, off)
        val since = StudyDay.start(now) - 400L * 86_400_000L
        val month = now - 30L * 86_400_000L
        combine(
            dao.reviewsPerDay(since, off),
            dao.matureReviews(month),
            dao.matureRecalled(month),
            dao.forecast(StudyDay.end(now), StudyDay.end(now) + 7 * 86_400_000L, off),
        ) { perDay, mature, recalled, fc ->
            val m = perDay.associate { it.day to it.n }
            var streak = 0
            var d = if ((m[today] ?: 0) > 0) today else today - 1
            while ((m[d] ?: 0) > 0) { streak++; d-- }
            var best = 0; var run = 0
            for (x in (today - 400)..today) { if ((m[x] ?: 0) > 0) { run++; best = maxOf(best, run) } else run = 0 }
            val f = fc.associate { it.day to it.n }
            StatsData(
                todayDone = m[today] ?: 0,
                weekDays = (6 downTo 0).map { m[today - it] ?: 0 },
                weeks12 = (83 downTo 0).map { m[today - it] ?: 0 },
                retention = if (mature >= 5) Math.round(recalled * 100.0 / mature).toInt() else null,
                streak = streak,
                bestStreak = best,
                total84 = (83 downTo 0).sumOf { m[today - it] ?: 0 },
                forecast = (1..7).map { f[today + it] ?: 0 },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StatsData())

    val message = MutableStateFlow<String?>(null)
    val importing = MutableStateFlow(false)

    fun import(uri: Uri) {
        importing.value = true
        viewModelScope.launch {
            message.value = runCatching { importer.import(uri) }.fold(
                onSuccess = { r ->
                    buildString {
                        append("Imported ${r.added} cards")
                        if (r.decks.size == 1) append(" into ${r.decks.first()}") else if (r.decks.size > 1) append(" into ${r.decks.size} decks")
                        if (r.duplicates > 0) append(" (${r.duplicates} already here)")
                    }
                },
                onFailure = { "Import failed: ${it.message ?: it.javaClass.simpleName}" },
            )
            importing.value = false
        }
    }

    fun createDeck(name: String) = viewModelScope.launch { if (name.isNotBlank()) repo.createDeck(name) }
    fun renameDeck(id: Long, name: String) = viewModelScope.launch {
        dao.deck(id)?.let { if (name.isNotBlank()) dao.updateDeck(it.copy(name = name.trim())) }
    }
    fun deleteDeck(id: Long) = viewModelScope.launch { dao.deleteDeck(id) }
    fun addCard(deckId: Long, front: String, back: String) = viewModelScope.launch {
        repo.addCard(deckId, textToHtml(front), textToHtml(back))
    }
    fun updateCard(card: Card, front: String, back: String) = viewModelScope.launch {
        dao.updateCard(card.copy(front = textToHtml(front), back = textToHtml(back)))
    }
    fun deleteCard(id: Long) = viewModelScope.launch { dao.deleteCard(id) }
    fun searchCards(deck: Long, q: String) = dao.searchCards(deck, q)
    fun settingsChanged() { _settingsVersion.value++ }

    // ---------- review session ----------

    data class Session(
        val deckIds: List<Long>?,
        val title: String,
        val card: Card? = null,
        val flipped: Boolean = false,
        val labels: List<String> = emptyList(),
        val done: Int = 0,
        val total: Int = 0,
        val canUndo: Boolean = false,
        val loading: Boolean = true,
    )

    private val _session = MutableStateFlow<Session?>(null)
    val session = _session.asStateFlow()
    private var undo: Undo? = null

    fun startSession(deckId: Long?) {
        val rows = decks.value
        val title = if (deckId == null) "All decks" else rows.firstOrNull { it.id == deckId }?.counts?.name ?: "Review"
        val total = if (deckId == null) rows.sumOf { it.due } else rows.firstOrNull { it.id == deckId }?.due ?: 0
        undo = null
        _session.value = Session(deckId?.let { listOf(it) }, title, total = total)
        loadNext()
    }

    private fun loadNext() = viewModelScope.launch {
        val s = _session.value ?: return@launch
        val card = repo.nextCard(s.deckIds)
        val now = System.currentTimeMillis()
        val remaining = remainingDue(s.deckIds, now)
        _session.value = s.copy(card = card, flipped = false, loading = false, canUndo = undo != null,
            labels = card?.let { repo.fsrs().previewLabels(it.memory, now) } ?: emptyList(),
            total = s.done + maxOf(remaining, if (card != null) 1 else 0))
    }

    private suspend fun remainingDue(deckIds: List<Long>?, now: Long): Int {
        val limit = repo.settings.newPerDay
        return dao.deckCounts(now, StudyDay.start(now), StudyDay.end(now)).first()
            .filter { deckIds == null || it.id in deckIds }
            .sumOf { it.learnDue + it.reviewDue + minOf(it.newCount, (limit - it.newToday).coerceAtLeast(0)) }
    }

    fun flip() { _session.value = _session.value?.copy(flipped = true) }

    fun grade(g: Grade) = viewModelScope.launch {
        val s = _session.value ?: return@launch
        val card = s.card ?: return@launch
        undo = repo.grade(card, g)
        _session.value = s.copy(done = s.done + 1)
        loadNext()
    }

    fun undoLast() = viewModelScope.launch {
        val u = undo ?: return@launch
        repo.undo(u)
        undo = null
        val s = _session.value ?: return@launch
        val restored = dao.card(u.previous.id)
        _session.value = s.copy(card = restored, flipped = true, done = (s.done - 1).coerceAtLeast(0), canUndo = false,
            labels = restored?.let { repo.fsrs().previewLabels(it.memory, System.currentTimeMillis()) } ?: emptyList())
    }

    fun endSession() { _session.value = null }
}

fun textToHtml(s: String): String = s.trim()
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

fun htmlToText(s: String): String = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]*>"), "").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&amp;", "&")
