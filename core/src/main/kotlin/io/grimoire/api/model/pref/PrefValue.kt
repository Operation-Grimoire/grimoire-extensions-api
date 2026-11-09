package io.grimoire.api.model.pref

/**
 * A typed value for one [SourcePreference], passed back to a
 * [io.grimoire.api.source.feature.ConfigurableSource] via `setPreferences`. The
 * variant matches the declaring [SourcePreference] type ([SourcePreference.EditText]
 * → [Str] or [Sensitive] when `isPassword`, [SourcePreference.Switch] → [Bool]). How
 * the host persists these is its own concern; the contract stays typed.
 */
sealed interface PrefValue {
    @JvmInline
    value class Str(val value: String) : PrefValue

    @JvmInline
    value class Bool(val value: Boolean) : PrefValue

    /**
     * A secret string (password / token / cookie). The host is expected to store it
     * encrypted and keep it out of logs and plain UI. Read it like [Str].
     */
    @JvmInline
    value class Sensitive(val value: String) : PrefValue
}
