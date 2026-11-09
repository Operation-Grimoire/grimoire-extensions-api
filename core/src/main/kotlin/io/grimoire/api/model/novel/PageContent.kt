package io.grimoire.api.model.novel

/** The content of a single [NovelPage] — exactly one of prose, an image, or a divider. */
sealed interface PageContent {
    /**
     * Prose. [html] is an optional constrained-HTML rendering (the subset Compose's
     * `AnnotatedString.fromHtml` supports: `<br>`, `<i>`, `<b>`, `<u>`, `<a href>`,
     * `&nbsp;`). Plain-text consumers (TTS, search, export) use [text]; the reader
     * falls back to [text] when [html] is null.
     */
    data class Text @JvmOverloads constructor(val text: String, val html: String? = null) : PageContent

    /** An illustration rendered at this position. */
    data class Image(val url: String) : PageContent

    /** A scene-break / thematic divider (e.g. an `<hr/>`). Will gain properties later. */
    class Separator : PageContent
}
