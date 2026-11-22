package br.com.cliplocal

/** Extrai o link de um post público do X ou Twitter. */
object XUrlParser {
    private val postPattern = Regex(
        """https?://(?:www\.|mobile\.)?(?:x\.com|twitter\.com)/([A-Za-z0-9_]+)/status/(\d+)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val match = postPattern.find(rawText) ?: return null
        val account = match.groupValues[1]
        val postId = match.groupValues[2]
        return "https://x.com/$account/status/$postId"
    }

    fun postId(url: String): String =
        postPattern.find(url)?.groupValues?.get(2) ?: "video"
}
