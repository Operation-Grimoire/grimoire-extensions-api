package io.grimoire.api.model

data class Chapter(
    val url: String,
    val name: String,
    val uploadDate: Long = 0L,
    val chapterNumber: Float = -1f,
    val translator: String? = null,
    /**
     * Whether this chapter is gated behind a paid account on the source and
     * cannot be read without one. Locked chapters are still listed by the host
     * (shown disabled) so the user knows to sign in; the host will not attempt
     * to fetch or download their content.
     */
    val locked: Boolean = false,
)
