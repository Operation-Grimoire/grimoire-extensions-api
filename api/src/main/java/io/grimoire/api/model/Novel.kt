package io.grimoire.api.model

data class Novel(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val status: NovelStatus = NovelStatus.UNKNOWN,
    val initialized: Boolean = false,
)

enum class NovelStatus {
    UNKNOWN, ONGOING, COMPLETED, HIATUS, CANCELLED
}
