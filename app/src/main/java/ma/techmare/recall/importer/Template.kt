package ma.techmare.recall.importer

/** Minimal Anki card-template renderer: fields, sections, common filters and cloze. */
object Template {
    private val sectionRe = Regex("""\{\{([#^])\s*([^\}]+?)\s*\}\}(.*?)\{\{/\s*\2\s*\}\}""", RegexOption.DOT_MATCHES_ALL)
    private val tagRe = Regex("""\{\{\s*([^\}]+?)\s*\}\}""")
    private val clozeRe = Regex("""\{\{c(\d+)::(.*?)(?:::(.*?))?\}\}""", RegexOption.DOT_MATCHES_ALL)

    fun render(
        template: String,
        fields: Map<String, String>,
        frontSide: String = "",
        clozeOrd: Int? = null,
        answer: Boolean = false,
    ): String {
        var t = template
        // Sections, repeated so nested ones resolve.
        repeat(5) {
            t = sectionRe.replace(t) { m ->
                val (kind, name, body) = m.destructured
                val value = fields[name.trim()].orEmpty()
                val nonEmpty = stripHtml(value).isNotBlank() ||
                    (clozeOrd != null && Regex("""\{\{c${clozeOrd + 1}::""").containsMatchIn(value))
                if ((kind == "#") == nonEmpty) body else ""
            }
        }
        return tagRe.replace(t) { m ->
            val expr = m.groupValues[1]
            when {
                expr == "FrontSide" -> frontSide
                expr.startsWith("!") || expr.startsWith("/") -> ""
                expr.startsWith("tts ") -> ""
                else -> {
                    val parts = expr.split(":")
                    val name = parts.last().trim()
                    val filters = parts.dropLast(1).map { it.trim() }
                    var v = when (name) {
                        "Tags", "Type", "Deck", "Subdeck", "Card", "CardFlag" -> ""
                        else -> fields[name] ?: return@replace ""
                    }
                    for (f in filters.reversed()) {
                        v = when {
                            f == "cloze" && clozeOrd != null -> cloze(v, clozeOrd, answer)
                            f == "text" -> stripHtml(v)
                            f == "type" -> ""
                            f == "furigana" -> Regex(""" ?([^ >]+?)\[(.+?)\]""").replace(v, "<ruby>$1<rt>$2</rt></ruby>")
                            f == "kana" -> Regex(""" ?([^ >]+?)\[(.+?)\]""").replace(v, "$2")
                            f == "kanji" -> Regex(""" ?([^ >]+?)\[(.+?)\]""").replace(v, "$1")
                            else -> v // hint and unknown filters: show the content
                        }
                    }
                    v
                }
            }
        }
    }

    fun cloze(text: String, ord: Int, answer: Boolean): String {
        val n = ord + 1
        return clozeRe.replace(text) { m ->
            val num = m.groupValues[1].toInt()
            val content = m.groupValues[2]
            val hint = m.groupValues[3]
            when {
                num != n -> content
                answer -> "<span class=\"cloze\">$content</span>"
                else -> "<span class=\"cloze\">[${hint.ifEmpty { "…" }}]</span>"
            }
        }
    }

    fun stripHtml(s: String) = s.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ")

    /** Removes the front side from an answer render, keeping only the answer part. */
    fun answerOnly(back: String): String {
        val hr = Regex("""<hr[^>]*id\s*=\s*["']?answer["']?[^>]*>""", RegexOption.IGNORE_CASE).find(back)
        return if (hr != null) back.substring(hr.range.last + 1) else back
    }
}
