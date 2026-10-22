package com.pulso.tv.ui.following.channels

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
import com.pulso.tv.model.ui.ChannelSort
import com.pulso.tv.repository.BookmarksRepository
import com.pulso.tv.repository.ChannelSortRepository
import com.pulso.tv.repository.GraphQLRepository
import com.pulso.tv.repository.HelixRepository
import com.pulso.tv.repository.LocalChannelFollowsRepository
import com.pulso.tv.repository.OfflineVideosRepository
import com.pulso.tv.repository.datasource.FollowedChannelsDataSource
import com.pulso.tv.util.C
import com.pulso.tv.util.TwitchApiHelper
import com.pulso.tv.util.prefs
import com.pulso.tv.util.tokenPrefs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

class FollowedChannelsViewModel(
    private val applicationContext: Context,
    private val channelSortRepository: ChannelSortRepository,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    val filter = MutableStateFlow<Filter?>(null)
    val sortText = MutableStateFlow<CharSequence?>(null)

    val sort: String
        get() = filter.value?.sort ?: FollowedChannelsSortDialog.SORT_LAST_BROADCAST
    val order: String
        get() = filter.value?.order ?: FollowedChannelsSortDialog.ORDER_DESC

    @OptIn(ExperimentalCoroutinesApi::class)
    val flow = filter.flatMapLatest {
        Pager(
            PagingConfig(pageSize = 15, prefetchDistance = 5, initialLoadSize = 15)
        ) {
            FollowedChannelsDataSource(
                userId = applicationContext.tokenPrefs().getString(C.USER_ID, null),
                sort = when (sort) {
                    FollowedChannelsSortDialog.SORT_FOLLOWED_AT -> "created_at"
                    FollowedChannelsSortDialog.SORT_ALPHABETICALLY -> "login"
                    FollowedChannelsSortDialog.SORT_LAST_BROADCAST -> "last_broadcast"
                    else -> "last_broadcast"
                },
                order = when (order) {
                    FollowedChannelsSortDialog.ORDER_DESC -> "desc"
                    FollowedChannelsSortDialog.ORDER_ASC -> "asc"
                    else -> "desc"
                },
                localChannelFollowsRepository = localChannelFollowsRepository,
                offlineVideosRepository = offlineVideosRepository,
                bookmarksRepository = bookmarksRepository,
                gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
                graphQLRepository = graphQLRepository,
                helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
                helixRepository = helixRepository,
                enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
                networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            )
        }.flow
    }.cachedIn(viewModelScope)

    suspend fun getChannelSort(id: String): ChannelSort? {
        return channelSortRepository.getById(id)
    }

    suspend fun saveChannelSort(item: ChannelSort) {
        channelSortRepository.save(item)
    }

    fun setFilter(sort: String?, order: String?) {
        filter.value = Filter(sort, order)
    }

    class Filter(
        val sort: String?,
        val order: String?,
    )

    companion object {
        val FollowedChannelsViewModelFactory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as PulsoApp)
                val pulsoModule = application.pulsoModule
                FollowedChannelsViewModel(application.applicationContext, pulsoModule.channelSortRepository, pulsoModule.localChannelFollowsRepository, pulsoModule.offlineVideosRepository, pulsoModule.bookmarksRepository, pulsoModule.graphQLRepository, pulsoModule.helixRepository)
            }
        }
    }
}
