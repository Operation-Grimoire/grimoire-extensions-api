package io.grimoire.api.model.pref

/**
 * Outcome of [ConfigurableSource.validateConfiguration]: whether the
 * current settings (e.g. account credentials) are usable, plus a short
 * human-readable message the host app shows to the user.
 */
data class ConfigValidationResult(
    val success: Boolean,
    val message: String,
)
