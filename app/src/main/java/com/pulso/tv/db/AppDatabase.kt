package com.pulso.tv.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pulso.tv.model.NotificationUser
import com.pulso.tv.model.PlaybackState
import com.pulso.tv.model.ShownNotification
import com.pulso.tv.model.VideoPosition
import com.pulso.tv.model.chat.RecentEmote
import com.pulso.tv.model.ui.Bookmark
import com.pulso.tv.model.ui.BookmarkIgnoredUser
import com.pulso.tv.model.ui.ChannelSort
import com.pulso.tv.model.ui.CustomProxy
import com.pulso.tv.model.ui.GameSort
import com.pulso.tv.model.ui.LocalChannelFollow
import com.pulso.tv.model.ui.LocalGameFollow
import com.pulso.tv.model.ui.OfflineVideo
import com.pulso.tv.model.ui.RecentSearch
import com.pulso.tv.model.ui.SavedFilter
import com.pulso.tv.model.ui.StreamProxy
import com.pulso.tv.model.ui.TranslatedChannel
import com.pulso.tv.model.ui.VideoSwap

@Database(
    entities = [
        OfflineVideo::class,
        RecentEmote::class,
        VideoPosition::class,
        LocalChannelFollow::class,
        LocalGameFollow::class,
        Bookmark::class,
        BookmarkIgnoredUser::class,
        ChannelSort::class,
        GameSort::class,
        ShownNotification::class,
        NotificationUser::class,
        TranslatedChannel::class,
        SavedFilter::class,
        RecentSearch::class,
        PlaybackState::class,
        CustomProxy::class,
        StreamProxy::class,
        VideoSwap::class,
    ],
    version = 40,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun offlineVideos(): OfflineVideosDao
    abstract fun recentEmotes(): RecentEmotesDao
    abstract fun videoPositions(): VideoPositionsDao
    abstract fun localChannelFollows(): LocalChannelFollowsDao
    abstract fun localGameFollows(): LocalGameFollowsDao
    abstract fun bookmarks(): BookmarksDao
    abstract fun bookmarkIgnoredUsers(): BookmarkIgnoredUsersDao
    abstract fun channelSort(): ChannelSortDao
    abstract fun gameSort(): GameSortDao
    abstract fun shownNotifications(): ShownNotificationsDao
    abstract fun notificationUsers(): NotificationUsersDao
    abstract fun translatedChannels(): TranslatedChannelsDao
    abstract fun savedFilters(): SavedFiltersDao
    abstract fun recentSearches(): RecentSearchesDao
    abstract fun playbackStates(): PlaybackStatesDao
    abstract fun customProxies(): CustomProxiesDao
    abstract fun streamProxies(): StreamProxiesDao
    abstract fun videoSwap(): VideoSwapDao
}