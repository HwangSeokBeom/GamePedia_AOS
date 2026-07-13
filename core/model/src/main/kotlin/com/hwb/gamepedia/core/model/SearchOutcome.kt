package com.hwb.gamepedia.core.model

/** Result wrapper used by the search data layer instead of exceptions. */
sealed interface SearchOutcome<out T> {
    data class Success<T>(val value: T, val fromCache: Boolean = false) : SearchOutcome<T>
    data class Failure(val failure: SearchFailure) : SearchOutcome<Nothing>
}
