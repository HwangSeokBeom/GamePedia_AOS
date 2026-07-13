package com.hwb.gamepedia.app

import com.hwb.gamepedia.core.network.GamePediaNetwork
import com.hwb.gamepedia.feature.search.data.DefaultSearchRepository
import com.hwb.gamepedia.feature.search.data.SearchRepository

/**
 * Manual dependency wiring for the current slice. A DI framework is deliberately
 * deferred until more features exist (docs/DECISIONS.md D-0002).
 */
class AppContainer {

    private val searchApi = GamePediaNetwork.createSearchApi()

    val searchRepository: SearchRepository =
        DefaultSearchRepository(
            api = searchApi,
            failureMapper = GamePediaNetwork.createFailureMapper(),
        )
}
