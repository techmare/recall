package ma.techmare.recall.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ma.techmare.recall.fsrs.Memory

@Entity
data class Deck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(indices = [Index("deckId"), Index("due"), Index("sourceKey")])
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deckId: Long,
    val front: String,
    val back: String,
    val state: Int = 0,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReview: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val step: Int = 0,
    val position: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** "noteGuid:ord" for imported Anki cards, so re-importing doesn't duplicate. */
    val sourceKey: String? = null,
) {
    val memory get() = Memory(state, stability, difficulty, due, lastReview, reps, lapses, step)
    fun withMemory(m: Memory) = copy(state = m.state, stability = m.stability, difficulty = m.difficulty,
        due = m.due, lastReview = m.lastReview, reps = m.reps, lapses = m.lapses, step = m.step)
}

@Entity(indices = [Index("time"), Index("cardId")])
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    val deckId: Long,
    val time: Long,
    val grade: Int,
    val stateBefore: Int,
)

data class DeckCounts(
    val id: Long,
    val name: String,
    val color: Int,
    val total: Int,
    @ColumnInfo(name = "newCount") val newCount: Int,
    val learnDue: Int,
    val reviewDue: Int,
    val newToday: Int,
)

data class DayCount(val day: Long, val n: Int)

@Dao
interface RecallDao {
    @Query("""
        SELECT d.id, d.name, d.color,
          (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id) AS total,
          (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id AND c.state = 0) AS newCount,
          (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id AND c.state IN (1,3) AND c.due <= :learnCutoff) AS learnDue,
          (SELECT COUNT(*) FROM Card c WHERE c.deckId = d.id AND c.state = 2 AND c.due < :dayEnd) AS reviewDue,
          (SELECT COUNT(*) FROM ReviewLog r WHERE r.deckId = d.id AND r.stateBefore = 0 AND r.time >= :dayStart) AS newToday
        FROM Deck d ORDER BY d.name COLLATE NOCASE
    """)
    fun deckCounts(learnCutoff: Long, dayStart: Long, dayEnd: Long): Flow<List<DeckCounts>>

    @Query("SELECT * FROM Card WHERE deckId IN (:decks) AND state IN (1,3) AND due <= :cutoff ORDER BY due LIMIT 1")
    suspend fun nextLearning(decks: List<Long>, cutoff: Long): Card?

    @Query("SELECT * FROM Card WHERE deckId IN (:decks) AND state = 2 AND due < :dayEnd ORDER BY due LIMIT 1")
    suspend fun nextReview(decks: List<Long>, dayEnd: Long): Card?

    @Query("SELECT * FROM Card WHERE deckId = :deck AND state = 0 ORDER BY position, id LIMIT 1")
    suspend fun nextNew(deck: Long): Card?

    @Query("SELECT COUNT(*) FROM ReviewLog WHERE deckId = :deck AND stateBefore = 0 AND time >= :dayStart")
    suspend fun newIntroduced(deck: Long, dayStart: Long): Int

    @Query("SELECT id FROM Deck")
    suspend fun deckIds(): List<Long>

    @Query("SELECT * FROM Deck ORDER BY name COLLATE NOCASE")
    fun decks(): Flow<List<Deck>>

    @Query("SELECT * FROM Deck WHERE id = :id")
    suspend fun deck(id: Long): Deck?

    @Query("SELECT * FROM Deck WHERE name = :name LIMIT 1")
    suspend fun deckByName(name: String): Deck?

    @Insert suspend fun insertDeck(d: Deck): Long
    @Update suspend fun updateDeck(d: Deck)
    @Query("DELETE FROM Deck WHERE id = :id") suspend fun deleteDeckRow(id: Long)
    @Query("DELETE FROM Card WHERE deckId = :id") suspend fun deleteDeckCards(id: Long)
    @Query("DELETE FROM ReviewLog WHERE deckId = :id") suspend fun deleteDeckLogs(id: Long)

    @Transaction
    suspend fun deleteDeck(id: Long) { deleteDeckLogs(id); deleteDeckCards(id); deleteDeckRow(id) }

    @Insert suspend fun insertCard(c: Card): Long
    @Insert suspend fun insertCards(c: List<Card>)
    @Update suspend fun updateCard(c: Card)
    @Query("DELETE FROM Card WHERE id = :id") suspend fun deleteCard(id: Long)
    @Query("SELECT * FROM Card WHERE id = :id") suspend fun card(id: Long): Card?

    @Query("SELECT * FROM Card WHERE deckId = :deck AND (front LIKE '%' || :q || '%' OR back LIKE '%' || :q || '%') ORDER BY id DESC LIMIT 300")
    fun searchCards(deck: Long, q: String): Flow<List<Card>>

    @Query("SELECT sourceKey FROM Card WHERE sourceKey IS NOT NULL")
    suspend fun allSourceKeys(): List<String>

    @Query("SELECT COALESCE(MAX(position), 0) FROM Card")
    suspend fun maxPosition(): Long

    @Insert suspend fun insertLog(l: ReviewLog): Long
    @Query("DELETE FROM ReviewLog WHERE id = :id") suspend fun deleteLog(id: Long)

    @Transaction
    suspend fun applyReview(updated: Card, log: ReviewLog): Long { updateCard(updated); return insertLog(log) }

    @Transaction
    suspend fun undoReview(previous: Card, logId: Long) { updateCard(previous); deleteLog(logId) }

    /** Reviews grouped by study day (day boundary passed as an offset in ms). */
    @Query("SELECT ((time + :offset) / 86400000) AS day, COUNT(*) AS n FROM ReviewLog WHERE time >= :since GROUP BY day")
    fun reviewsPerDay(since: Long, offset: Long): Flow<List<DayCount>>

    @Query("SELECT COUNT(*) FROM ReviewLog WHERE stateBefore = 2 AND time >= :since")
    fun matureReviews(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM ReviewLog WHERE stateBefore = 2 AND grade > 1 AND time >= :since")
    fun matureRecalled(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM Card") fun cardCount(): Flow<Int>

    @Query("SELECT ((due + :offset) / 86400000) AS day, COUNT(*) AS n FROM Card WHERE state = 2 AND due >= :from AND due < :to GROUP BY day")
    fun forecast(from: Long, to: Long, offset: Long): Flow<List<DayCount>>
}

@Database(entities = [Deck::class, Card::class, ReviewLog::class], version = 1, exportSchema = false)
abstract class RecallDb : RoomDatabase() {
    abstract fun dao(): RecallDao

    companion object {
        @Volatile private var instance: RecallDb? = null
        fun get(context: Context): RecallDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, RecallDb::class.java, "recall.db")
                .build().also { instance = it }
        }
    }
}
