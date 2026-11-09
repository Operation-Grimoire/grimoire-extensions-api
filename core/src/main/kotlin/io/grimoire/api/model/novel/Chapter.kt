package io.grimoire.api.model.novel

/**
 * One chapter in a novel's list, as returned by a
 * [io.grimoire.api.source.web.ChapterListSource] / [io.grimoire.api.source.web.PaginatedSource].
 *
 * @property url Source-relative or absolute chapter URL; the host keys chapters by it.
 * @property name Display title of the chapter.
 * @property uploadDate Release time in epoch millis; `0L` when the source doesn't
 *   expose a date.
 * @property chapterNumber Parsed chapter number; `-1f` when unknown (the host then
 *   falls back to list position).
 * @property translator Translator / group credit for this chapter, if any.
 * @property locked Whether this chapter is gated behind a paid account on the source
 *   and cannot be read without one. Locked chapters are still listed by the host
 *   (shown disabled) so the user knows to sign in; the host will not attempt to
 *   fetch or download their content.
 */
// @JvmOverloads keeps telescoping constructors so extension APKs built against an
// older API keep linking when fields are appended. Append new fields only (with a
// default); never reorder or remove.
data class Chapter @JvmOverloads constructor(
    val url: String,
    val name: String,
    val uploadDate: Long = 0L,
    val chapterNumber: Float = -1f,
    val translator: String? = null,
    val locked: Boolean = false,
)
