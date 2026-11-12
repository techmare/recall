package ma.techmare.recall.importer

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ma.techmare.recall.data.Card
import ma.techmare.recall.data.Repository
import ma.techmare.recall.fsrs.CardState
import java.io.File
import kotlin.math.max

class AndroidSqlReader(file: File) : SqlReader {
    private val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
    override fun query(sql: String): List<List<Any?>> = db.rawQuery(sql, null).use { c ->
        val rows = ArrayList<List<Any?>>(c.count.coerceAtLeast(0))
        while (c.moveToNext()) rows += (0 until c.columnCount).map { i ->
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> null
                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                Cursor.FIELD_TYPE_BLOB -> c.getBlob(i)
                else -> c.getString(i)
            }
        }
        rows
    }
    override fun close() = db.close()
}

data class ImportResult(val decks: List<String>, val added: Int, val duplicates: Int, val media: Int)

class Importer(private val context: Context, private val repo: Repository) {

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "import")
        try {
            val parsed = context.contentResolver.openInputStream(uri)?.use { input ->
                ApkgReader { AndroidSqlReader(it) }.read(input, work, repo.mediaDir)
            } ?: throw IllegalArgumentException("Couldn't open the file")
            save(parsed)
        } finally {
            work.deleteRecursively()
        }
    }

    private suspend fun save(parsed: ParsedDeck): ImportResult {
        val dao = repo.dao
        val existing = dao.allSourceKeys().toHashSet()
        val deckIds = HashMap<String, Long>()
        val now = System.currentTimeMillis()
        val basePos = dao.maxPosition()
        val toInsert = ArrayList<Card>()
        var dupes = 0

        for (p in parsed.cards) {
            if (p.suspended) continue
            if (p.sourceKey in existing) { dupes++; continue }
            existing += p.sourceKey
            val name = p.deckName.replace("::", " › ").ifBlank { "Imported" }
            val deckId = deckIds.getOrPut(name) { dao.deckByName(name)?.id ?: repo.createDeck(name) }
            toInsert += toCard(p, deckId, basePos, now)
        }
        // Anki's empty "Default" deck often comes along; don't create it if nothing landed there.
        toInsert.chunked(500).forEach { dao.insertCards(it) }
        return ImportResult(deckIds.keys.toList(), toInsert.size, dupes, parsed.mediaFiles)
    }

    private fun toCard(p: ParsedCard, deckId: Long, basePos: Long, now: Long): Card {
        val base = Card(deckId = deckId, front = p.front, back = p.back, sourceKey = p.sourceKey,
            position = basePos + p.newPosition + 1)
        return when (p.type) {
            2, 3 -> {
                val ivl = max(p.intervalDays, 1)
                val due = if (p.type == 3) now else p.dueMillis
                base.copy(
                    state = CardState.REVIEW,
                    stability = p.stability ?: ivl.toDouble(),
                    difficulty = p.difficulty ?: 5.0,
                    due = due,
                    lastReview = due - ivl * 86_400_000L,
                    reps = p.reps, lapses = p.lapses,
                )
            }
            else -> base
        }
    }
}
