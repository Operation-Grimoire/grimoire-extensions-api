package io.grimoire.api.source

/**
 * Declarative description of a single user-configurable setting for a
 * [ConfigurableSource]. The host app renders these into a settings screen and
 * persists their values, then pushes the stored values back via
 * [ConfigurableSource.setPreferences].
 *
 * Values are exchanged as strings; a [Switch] is stored as `"true"`/`"false"`.
 */
sealed class SourcePreference(
    val key: String,
    val title: String,
    val summary: String? = null,
) {
    class EditText(
        key: String,
        title: String,
        summary: String? = null,
        val default: String = "",
        val isPassword: Boolean = false,
    ) : SourcePreference(key, title, summary)

    class Switch(
        key: String,
        title: String,
        summary: String? = null,
        val default: Boolean = false,
    ) : SourcePreference(key, title, summary)
}
