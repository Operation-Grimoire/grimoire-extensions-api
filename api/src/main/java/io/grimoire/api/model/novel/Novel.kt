package io.grimoire.api.model.novel

import io.grimoire.api.model.lang.Language

/**
 * A novel as returned by a source — both the lightweight form in browse/search
 * listings (often just `url`, `title`, `language`, cover) and the fully populated
 * form from [io.grimoire.api.source.Source.getNovelDetails] (`initialized = true`).
 *
 * @property url Source-relative or absolute URL identifying the novel; the host
 *   keys saved novels by it. The only field guaranteed present in a listing.
 * @property title Display title.
 * @property language Required. Single-language sources set this to the source's own
 *   language; multi-language sources set the per-novel language, or [Language.UNKNOWN]
 *   when it can't be determined.
 * @property thumbnailUrl Cover image URL, if any.
 * @property author Author / translator credit, if known.
 * @property description Synopsis; may carry constrained HTML from
 *   [io.grimoire.api.util.richDescription].
 * @property genres Genre / tag labels.
 * @property status Publication status; [NovelStatus.UNKNOWN] when not exposed.
 * @property rating Average user rating normalized to a 0..5 scale. Sources that
 *   expose a different scale (e.g. 0..10) must convert before populating this field.
 * @property ratingCount Number of ratings backing [rating], if known.
 * @property initialized `true` once [io.grimoire.api.source.Source.getNovelDetails]
 *   has filled the full record; `false` for a bare listing entry.
 */
// @JvmOverloads keeps telescoping constructors so extension APKs built against an
// older API keep linking when fields are appended. Append new fields only (with a
// default); never reorder or remove.
data class Novel @JvmOverloads constructor(
    val url: String,
    val title: String,
    val language: Language,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: NovelStatus = NovelStatus.UNKNOWN,
    val rating: Float? = null,
    val ratingCount: Int? = null,
    val initialized: Boolean = false,
)

/** Publication status of a [Novel]. [UNKNOWN] is the default when a source doesn't expose it. */
enum class NovelStatus {
    UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED
}
