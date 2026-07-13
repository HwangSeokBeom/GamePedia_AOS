package com.hwb.gamepedia.feature.search.ui

import androidx.annotation.StringRes
import com.hwb.gamepedia.core.model.SearchFailure
import com.hwb.gamepedia.feature.search.R

/**
 * Maps the failure taxonomy to localized user copy. Backend `error.message` values
 * are never shown; provider/infrastructure details never reach the UI.
 */
@StringRes
fun SearchFailure.userMessageRes(): Int = when (this) {
    is SearchFailure.InvalidQuery -> R.string.search_error_invalid_query
    // An invalid limit is a client bug; users see a generic failure, QA sees the code.
    is SearchFailure.InvalidLimit -> R.string.search_error_unexpected
    is SearchFailure.RateLimited -> R.string.search_error_rate_limited
    is SearchFailure.ProviderUnavailable -> R.string.search_error_provider_unavailable
    is SearchFailure.ServerError -> R.string.search_error_server
    SearchFailure.Timeout -> R.string.search_error_timeout
    SearchFailure.NetworkUnavailable -> R.string.search_error_offline
    SearchFailure.MalformedResponse -> R.string.search_error_malformed
    is SearchFailure.Unexpected -> R.string.search_error_unexpected
}
