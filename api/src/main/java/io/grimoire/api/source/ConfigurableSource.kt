package io.grimoire.api.source

/**
 * A [Source] that exposes user-configurable settings (e.g. login credentials or
 * a mirror domain). The host app renders [getPreferences] into a settings
 * screen, persists the values, and pushes them back via [setPreferences] before
 * the source is used (and again whenever the user changes them).
 *
 * Values are keyed by [SourcePreference.key]. Absent keys mean "use the
 * declared default". [setPreferences] must be cheap and side-effect free
 * (just store the values); any network work happens lazily during normal
 * source calls.
 */
interface ConfigurableSource : Source {
    fun getPreferences(): List<SourcePreference>

    fun setPreferences(values: Map<String, String>)
}
