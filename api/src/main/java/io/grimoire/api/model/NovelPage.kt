package io.grimoire.api.model

// @JvmOverloads keeps shorter constructor overloads on the JVM ABI so extensions
// built against an older api keep linking. Append new fields only; never reorder.
data class NovelPage @JvmOverloads constructor(
    val index: Int,
    val text: String,
    /** When non-null, the page is an illustration to render at this position instead of [text]. */
    val imageUrl: String? = null,
    /** Scene-break / thematic separator (e.g. an `<hr/>`). Reader renders a divider; [text] is ignored. */
    val isSeparator: Boolean = false,
    /**
     * Optional constrained-HTML version of [text] for the reader to render with rich
     * formatting — italics, bold, links, line breaks, indentation. Limited to the
     * subset Compose's `AnnotatedString.fromHtml` supports (`<br>`, `<i>`, `<b>`,
     * `<u>`, `<a href>`, `&nbsp;`). Plain-text consumers (TTS, search, exporting)
     * must keep using [text]; the reader falls back to [text] when this is `null`.
     */
    val formattedText: String? = null,
)
