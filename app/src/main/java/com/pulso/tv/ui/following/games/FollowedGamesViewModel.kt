package com.pulso.tv.ui.following.games

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.pulso.tv.PulsoApp
import com.pulso.tv.repository.GraphQLRepository
import com.pulso.tv.repository.LocalGameFollowsRepository
import com.pulso.tv.repository.datasource.FollowedGamesDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs

class FollowedGamesViewModel(
    applicationContext: Context,
    private val localGameFollowsRepository: LocalGameFollowsRepository,
    private val graphQLRepository: GraphQLRepository,
) : ViewModel() {

    val flow = Pager(
        PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)
    ) {
        FollowedGamesDataSource(
            localGameFollowsRepository = localGameFollowsRepository,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
            graphQLRepository = graphQLRepository,
            enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
    }.flow.cachedIn(viewModelScope)

    companion object {
        val FollowedGamesViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as PulsoApp)
                val pulsoModule = application.pulsoModule
                FollowedGamesViewModel(application.applicationContext, pulsoModule.localGameFollowsRepository, pulsoModule.graphQLRepository)
            }
        }
    }
}
