package ma.techmare.recall.data

import android.content.Context
import android.content.SharedPreferences
import ma.techmare.recall.fsrs.Fsrs
import ma.techmare.recall.fsrs.Grade
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** Study days roll over at 4 a.m. local time, like Anki. */
object StudyDay {
    private const val ROLLOVER_HOUR = 4L
    private const val DAY = 86_400_000L

    /** Add to an epoch-millis timestamp, then integer-divide by a day, to get a study-day number. */
    fun offset(now: Long = System.currentTimeMillis()): Long {
        val tz = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(now)).totalSeconds * 1000L
        return tz - ROLLOVER_HOUR * 3_600_000L
    }

    fun dayNumber(t: Long, off: Long = offset(t)) = Math.floorDiv(t + off, DAY)
    fun start(now: Long = System.currentTimeMillis()): Long {
        val off = offset(now)
        return dayNumber(now, off) * DAY - off
    }
    fun end(now: Long = System.currentTimeMillis()) = start(now) + DAY
}

class Settings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var newPerDay: Int
        get() = prefs.getInt("newPerDay", 20)
        set(v) = prefs.edit().putInt("newPerDay", v).apply()
    var retention: Float
        get() = prefs.getFloat("retention", 0.9f)
        set(v) = prefs.edit().putFloat("retention", v).apply()
    var autoplayAudio: Boolean
        get() = prefs.getBoolean("autoplayAudio", true)
        set(v) = prefs.edit().putBoolean("autoplayAudio", v).apply()
}

data class Undo(val previous: Card, val logId: Long)

class Repository(context: Context) {
    val dao = RecallDb.get(context).dao()
    val settings = Settings(context)
    val mediaDir: File = File(context.filesDir, "media").apply { mkdirs() }

    fun fsrs() = Fsrs(desiredRetention = settings.retention.toDouble())

    /** Learning cards up to this far ahead are shown when nothing else is left. */
    private val learnAhead = 20 * 60_000L

    suspend fun nextCard(deckIds: List<Long>?, now: Long = System.currentTimeMillis()): Card? {
        val decks = deckIds ?: dao.deckIds()
        if (decks.isEmpty()) return null
        dao.nextLearning(decks, now)?.let { return it }
        dao.nextReview(decks, StudyDay.end(now))?.let { return it }
        val dayStart = StudyDay.start(now)
        for (d in decks) {
            if (dao.newIntroduced(d, dayStart) < settings.newPerDay) dao.nextNew(d)?.let { return it }
        }
        return dao.nextLearning(decks, now + learnAhead)
    }

    suspend fun grade(card: Card, grade: Grade, now: Long = System.currentTimeMillis()): Undo {
        val next = fsrs().next(card.memory, grade, now)
        val updated = card.withMemory(next)
        val logId = dao.applyReview(updated, ReviewLog(cardId = card.id, deckId = card.deckId, time = now,
            grade = grade.value, stateBefore = card.state))
        return Undo(card, logId)
    }

    suspend fun undo(u: Undo) = dao.undoReview(u.previous, u.logId)

    suspend fun addCard(deckId: Long, front: String, back: String) {
        dao.insertCard(Card(deckId = deckId, front = front, back = back, position = dao.maxPosition() + 1))
    }

    suspend fun createDeck(name: String): Long {
        val count = dao.deckIds().size
        return dao.insertDeck(Deck(name = name.trim(), color = count % 4))
    }
}
