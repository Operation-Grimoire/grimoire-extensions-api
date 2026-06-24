package io.grimoire.api.model.filter

/**
 * One entry in a source's filter list (see [io.grimoire.api.source.feature.FilterSource]).
 * The host renders each subtype into a control, mutates its [state] as the user
 * interacts, and hands the list back to
 * [io.grimoire.api.source.feature.SearchSource.searchNovels] so the source can
 * translate the chosen states into query parameters.
 *
 * [name] is the user-facing label; [state] is the mutable current value, typed
 * per subtype ([T]).
 */
sealed class Filter<T>(val name: String, var state: T) {

    /** A non-interactive section heading shown above a group of filters. */
    class Header(name: String) : Filter<Any>(name, 0)

    /** A blank visual divider between filter groups; [name] is usually empty. */
    class Separator(name: String = "") : Filter<Any>(name, 0)

    /** A free-text input; [state] holds the typed string (empty when untouched). */
    class Text(name: String) : Filter<String>(name, "")

    /** A single on/off toggle; [state] is whether it is checked. */
    class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    /**
     * A three-state toggle for include / exclude / ignore (e.g. a genre that can
     * be required, excluded, or left unconstrained). [state] is one of the
     * `STATE_*` constants.
     */
    class TriState(name: String) : Filter<Int>(name, STATE_IGNORE) {
        companion object {
            /** Not constrained either way (the default). */
            const val STATE_IGNORE = 0

            /** Results must match this entry. */
            const val STATE_INCLUDE = 1

            /** Results must not match this entry. */
            const val STATE_EXCLUDE = 2
        }
    }

    /**
     * A single-choice dropdown over [values]; [state] is the index of the chosen
     * option. Subclassed by a source to carry the per-option payload (e.g. the
     * query slug for each label).
     */
    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) :
        Filter<Int>(name, state)

    /**
     * A group of child filters (typically [CheckBox] or [TriState]); [state] is the
     * list of children, each with its own mutable state.
     */
    abstract class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)

    /**
     * A sortable field list; [values] are the sortable columns and [state] is the
     * current [Selection] (which column, ascending or descending), or null for the
     * source default.
     */
    abstract class Sort(
        name: String,
        val values: Array<String>,
        state: Selection? = null,
    ) : Filter<Sort.Selection?>(name, state) {
        /** The chosen sort column [index] and direction ([ascending]). */
        data class Selection(val index: Int, val ascending: Boolean)
    }
}
