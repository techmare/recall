package ma.techmare.recall.importer

import com.github.luben.zstd.ZstdInputStream
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Runs a query on an SQLite file; implemented with Android SQLite in the app and JDBC in tests. */
interface SqlReader : AutoCloseable {
    fun query(sql: String): List<List<Any?>>
    fun tableExists(name: String) =
        query("SELECT name FROM sqlite_master WHERE type='table' AND name='$name'").isNotEmpty()
}

data class ParsedCard(
    val sourceKey: String,
    val deckName: String,
    val front: String,
    val back: String,
    /** Anki card type: 0 new, 1 learning, 2 review, 3 relearning. */
    val type: Int,
    val intervalDays: Int,
    val dueMillis: Long,
    val reps: Int,
    val lapses: Int,
    val stability: Double?,
    val difficulty: Double?,
    val newPosition: Long,
    val suspended: Boolean,
)

data class ParsedDeck(val cards: List<ParsedCard>, val mediaFiles: Int)

private class NoteType(val fields: List<String>, val templates: List<Pair<String, String>>, val cloze: Boolean)

/**
 * Reads an Anki .apkg (legacy collection.anki2/anki21 or the newer zstd collection.anki21b)
 * into plain cards, copying media into [mediaOut].
 */
class ApkgReader(private val openDb: (File) -> SqlReader) {

    fun read(apkg: InputStream, workDir: File, mediaOut: File): ParsedDeck {
        workDir.deleteRecursively(); workDir.mkdirs()
        unzip(apkg, workDir)

        val collection = when {
            File(workDir, "collection.anki21b").exists() ->
                File(workDir, "collection.unzstd").also { decompressTo(File(workDir, "collection.anki21b"), it) }
            File(workDir, "collection.anki21").exists() -> File(workDir, "collection.anki21")
            File(workDir, "collection.anki2").exists() -> File(workDir, "collection.anki2")
            else -> throw IllegalArgumentException("This file isn't an Anki deck (.apkg)")
        }

        val media = copyMedia(workDir, mediaOut)
        val cards = openDb(collection).use { parseCollection(it) }
        if (cards.isEmpty()) throw IllegalArgumentException(
            "No cards found. If this deck was exported from a recent Anki, try exporting with \"Support older Anki versions\" ticked.")
        return ParsedDeck(cards, media)
    }

    private fun unzip(input: InputStream, dir: File) {
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.substringAfterLast('/')
                if (e.isDirectory || name.isEmpty() || name.startsWith(".")) continue
                FileOutputStream(File(dir, name)).use { zip.copyTo(it) }
            }
        }
    }

    private fun isZstd(f: File): Boolean = FileInputStream(f).use { s ->
        val b = ByteArray(4); s.read(b) == 4 &&
            b[0] == 0x28.toByte() && b[1] == 0xB5.toByte() && b[2] == 0x2F.toByte() && b[3] == 0xFD.toByte()
    }

    private fun decompressTo(src: File, dst: File) {
        ZstdInputStream(FileInputStream(src)).use { i -> FileOutputStream(dst).use { i.copyTo(it) } }
    }

    private fun readBytes(f: File): ByteArray =
        if (isZstd(f)) ZstdInputStream(FileInputStream(f)).use { it.readBytes() } else f.readBytes()

    private fun copyMedia(dir: File, out: File): Int {
        val mapFile = File(dir, "media")
        if (!mapFile.exists()) return 0
        val raw = readBytes(mapFile)
        val names: Map<String, String> = if (raw.isNotEmpty() && raw[0] == '{'.code.toByte()) {
            val json = JSONObject(String(raw, Charsets.UTF_8))
            json.keys().asSequence().associateWith { json.getString(it) }
        } else {
            mediaEntryNames(raw).withIndex().associate { (i, n) -> i.toString() to n }
        }
        out.mkdirs()
        var count = 0
        for ((zipName, realName) in names) {
            val src = File(dir, zipName)
            if (!src.exists() || realName.contains('/') || realName.contains("..")) continue
            val dst = File(out, realName)
            if (isZstd(src)) decompressTo(src, dst) else src.copyTo(dst, overwrite = true)
            count++
        }
        return count
    }

    // ---------- collection ----------

    private fun parseCollection(db: SqlReader): List<ParsedCard> {
        val col = db.query("SELECT crt, models, decks FROM col").firstOrNull()
            ?: throw IllegalArgumentException("Empty collection")
        val crtMillis = (col[0] as Number).toLong() * 1000
        val modelsJson = col[1] as? String ?: ""
        val newSchema = modelsJson.isBlank() || modelsJson == "{}"

        val noteTypes = if (newSchema && db.tableExists("notetypes")) newNoteTypes(db) else legacyNoteTypes(modelsJson)
        val deckNames = if (newSchema && db.tableExists("decks") && !legacyDecksPresent(col[2] as? String))
            db.query("SELECT id, name FROM decks").associate { (it[0] as Number).toLong() to (it[1] as String).replace("\u001f", "::") }
        else legacyDecks(col[2] as? String ?: "{}")

        val hasData = db.query("PRAGMA table_info(cards)").any { it[1] == "data" }
        val rows = db.query(
            "SELECT c.ord, c.did, c.type, c.queue, c.due, c.ivl, c.reps, c.lapses, ${if (hasData) "c.data" else "''"}, " +
                "n.mid, n.flds, n.guid, c.odid FROM cards c JOIN notes n ON c.nid = n.id ORDER BY c.due, c.id")

        val dayMs = 86_400_000L
        val now = System.currentTimeMillis()
        return rows.mapNotNull { r ->
            val ord = (r[0] as Number).toInt()
            val odid = (r[12] as? Number)?.toLong() ?: 0L
            val did = if (odid != 0L) odid else (r[1] as Number).toLong()
            val type = (r[2] as Number).toInt()
            val queue = (r[3] as Number).toInt()
            val due = (r[4] as Number).toLong()
            val ivl = (r[5] as Number).toInt()
            val mid = (r[9] as Number).toLong()
            val nt = noteTypes[mid] ?: return@mapNotNull null
            val values = (r[10] as String).split('\u001f')
            val fields = nt.fields.withIndex().associate { (i, n) -> n to values.getOrElse(i) { "" } }

            val (qfmt, afmt) = if (nt.cloze) nt.templates.firstOrNull() ?: return@mapNotNull null
            else nt.templates.getOrNull(ord) ?: return@mapNotNull null
            val clozeOrd = if (nt.cloze) ord else null
            val front = clean(Template.render(qfmt, fields, clozeOrd = clozeOrd))
            if (Template.stripHtml(front).isBlank() && !front.contains("<img") && !front.contains("[sound:")) return@mapNotNull null
            val back = clean(Template.answerOnly(Template.render(afmt, fields, frontSide = "", clozeOrd = clozeOrd, answer = true)))

            var s: Double? = null
            var d: Double? = null
            val data = r[8] as? String
            if (!data.isNullOrBlank() && data.startsWith("{")) runCatching {
                val j = JSONObject(data)
                if (j.has("s")) s = j.getDouble("s")
                if (j.has("d")) d = j.getDouble("d")
            }
            val dueMillis = when (type) {
                2 -> crtMillis + due * dayMs
                1, 3 -> if (due > 1_000_000_000L) due * 1000 else now
                else -> 0L
            }
            ParsedCard(
                sourceKey = "${r[11]}:$ord",
                deckName = deckNames[did] ?: "Imported",
                front = front, back = back, type = type, intervalDays = ivl,
                dueMillis = dueMillis,
                reps = (r[6] as Number).toInt(), lapses = (r[7] as Number).toInt(),
                stability = s, difficulty = d,
                newPosition = if (type == 0) due else 0,
                suspended = queue < 0,
            )
        }
    }

    private fun legacyDecksPresent(json: String?) = !json.isNullOrBlank() && json != "{}"

    private fun legacyNoteTypes(json: String): Map<Long, NoteType> {
        val o = JSONObject(json)
        return o.keys().asSequence().associate { k ->
            val m = o.getJSONObject(k)
            val flds = m.getJSONArray("flds")
            val fields = (0 until flds.length()).map { flds.getJSONObject(it) }
                .sortedBy { it.optInt("ord") }.map { it.getString("name") }
            val tm = m.getJSONArray("tmpls")
            val templates = (0 until tm.length()).map { tm.getJSONObject(it) }
                .sortedBy { it.optInt("ord") }.map { it.getString("qfmt") to it.getString("afmt") }
            k.toLong() to NoteType(fields, templates, m.optInt("type") == 1)
        }
    }

    private fun legacyDecks(json: String): Map<Long, String> {
        val o = JSONObject(json)
        return o.keys().asSequence().associate { k -> k.toLong() to o.getJSONObject(k).getString("name") }
    }

    private fun newNoteTypes(db: SqlReader): Map<Long, NoteType> {
        val fields = db.query("SELECT ntid, ord, name FROM fields ORDER BY ntid, ord")
            .groupBy({ (it[0] as Number).toLong() }, { it[2] as String })
        val templates = db.query("SELECT ntid, ord, config FROM templates ORDER BY ntid, ord")
            .groupBy({ (it[0] as Number).toLong() }) { row ->
                val cfg = Proto.parse(row[2] as ByteArray)
                cfg.string(1) to cfg.string(2)
            }
        return db.query("SELECT id, config FROM notetypes").associate { row ->
            val id = (row[0] as Number).toLong()
            val kind = Proto.parse(row[1] as ByteArray).varint(1)
            id to NoteType(fields[id].orEmpty(), templates[id].orEmpty(), kind == 1L)
        }
    }

    /** Strips scripts/styles and the audio-tag syntax Anki uses in fields. */
    private fun clean(html: String): String = html
        .replace(Regex("<script.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<style.*?</style>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .trim()

    private fun mediaEntryNames(bytes: ByteArray): List<String> =
        Proto.parse(bytes).all(1).map { Proto.parse(it as ByteArray).string(1) }
}

/** Tiny protobuf reader: enough for Anki's media list and template configs. */
class Proto private constructor(private val fields: List<Pair<Int, Any>>) {
    fun all(n: Int) = fields.filter { it.first == n }.map { it.second }
    fun string(n: Int) = (fields.firstOrNull { it.first == n }?.second as? ByteArray)?.toString(Charsets.UTF_8) ?: ""
    fun varint(n: Int) = fields.firstOrNull { it.first == n }?.second as? Long ?: 0L

    companion object {
        fun parse(b: ByteArray): Proto {
            val out = mutableListOf<Pair<Int, Any>>()
            var i = 0
            fun readVarint(): Long {
                var result = 0L; var shift = 0
                while (i < b.size) {
                    val x = b[i++].toInt() and 0xff
                    result = result or ((x and 0x7f).toLong() shl shift)
                    if (x and 0x80 == 0) break
                    shift += 7
                }
                return result
            }
            while (i < b.size) {
                val key = readVarint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> out += field to readVarint()
                    1 -> i += 8
                    2 -> {
                        val len = readVarint().toInt()
                        if (len < 0 || i + len > b.size) break
                        out += field to b.copyOfRange(i, i + len); i += len
                    }
                    5 -> i += 4
                    else -> break
                }
            }
            return Proto(out)
        }
    }
}
