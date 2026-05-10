package io.grimoire.api.model

sealed class Filter<T>(val name: String, var state: T) {

    class Header(name: String) : Filter<Any>(name, 0)

    class Separator(name: String = "") : Filter<Any>(name, 0)

    class Text(name: String) : Filter<String>(name, "")

    class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

    class TriState(name: String) : Filter<Int>(name, STATE_IGNORE) {
        companion object {
            const val STATE_IGNORE = 0
            const val STATE_INCLUDE = 1
            const val STATE_EXCLUDE = 2
        }
    }

    abstract class Select<V>(name: String, val values: Array<V>, state: Int = 0) :
        Filter<Int>(name, state)

    abstract class Group<V>(name: String, state: List<V>) : Filter<List<V>>(name, state)

    abstract class Sort(
        name: String,
        val values: Array<String>,
        state: Selection? = null,
    ) : Filter<Sort.Selection?>(name, state) {
        data class Selection(val index: Int, val ascending: Boolean)
    }
}
