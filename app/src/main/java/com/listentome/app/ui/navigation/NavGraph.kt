package com.listentome.app.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.listentome.app.ui.miniplayer.MiniPlayer
import com.listentome.app.ui.miniplayer.MiniPlayerViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.listentome.app.ui.addfeed.AddFeedScreen
import com.listentome.app.ui.addfeed.AddFeedViewModel
import com.listentome.app.ui.downloads.DownloadsScreen
import com.listentome.app.ui.downloads.DownloadsViewModel
import com.listentome.app.ui.episodelist.EpisodeListScreen
import com.listentome.app.ui.episodelist.EpisodeListViewModel
import com.listentome.app.ui.feedlist.FeedListScreen
import com.listentome.app.ui.feedlist.FeedListViewModel
import com.listentome.app.ui.player.PlayerScreen
import com.listentome.app.ui.player.PlayerViewModel
import com.listentome.app.ui.queue.QueueScreen
import com.listentome.app.ui.queue.QueueViewModel
import com.listentome.app.ui.settings.SettingsScreen
import com.listentome.app.ui.settings.SettingsViewModel

private object Routes {
    const val FEED_LIST = "feedList"
    const val ADD_FEED = "addFeed"
    const val EPISODE_LIST = "episodeList/{feedId}"
    const val PLAYER = "player"
    const val QUEUE = "queue"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"

    fun episodeList(feedId: Long) = "episodeList/$feedId"
}

@Composable
fun ListenToMeNavGraph(application: Application, openPlayerRequest: Int = 0) {
    val navController: NavHostController = rememberNavController()
    val miniPlayerViewModel: MiniPlayerViewModel = viewModel(
        factory = viewModelFactory { initializer { MiniPlayerViewModel(application) } }
    )
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val tabs = listOf(
        Triple(Routes.QUEUE, "Queue", Icons.AutoMirrored.Filled.QueueMusic),
        Triple(Routes.FEED_LIST, "Podcasts", Icons.Default.Podcasts),
        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings)
    )
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0 && navController.currentDestination?.route != Routes.PLAYER) {
            navController.navigate(Routes.PLAYER)
        }
    }
    val showTabs = currentRoute in tabs.map { it.first }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(Routes.QUEUE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                if (currentRoute != null && currentRoute != Routes.PLAYER) {
                    MiniPlayer(
                        viewModel = miniPlayerViewModel,
                        onOpenPlayer = { navController.navigate(Routes.PLAYER) }
                    )
                }
                if (showTabs) {
                    NavigationBar {
                        tabs.forEach { (route, label, icon) ->
                            NavigationBarItem(
                                selected = currentRoute == route,
                                onClick = { navigateToTab(route) },
                                icon = { Icon(icon, contentDescription = null) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    ) { outerPadding ->
    NavHost(
        navController = navController,
        startDestination = Routes.QUEUE,
        modifier = Modifier.padding(outerPadding).consumeWindowInsets(outerPadding)
    ) {
        composable(Routes.FEED_LIST) {
            val viewModel: FeedListViewModel = viewModel(
                factory = viewModelFactory { initializer { FeedListViewModel(application) } }
            )
            FeedListScreen(
                viewModel = viewModel,
                onAddFeed = { navController.navigate(Routes.ADD_FEED) },
                onOpenFeed = { feedId -> navController.navigate(Routes.episodeList(feedId)) }
            )
        }

        composable(Routes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory { initializer { SettingsViewModel(application) } }
            )
            SettingsScreen(
                viewModel = viewModel,
                onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) }
            )
        }

        composable(Routes.DOWNLOADS) {
            val viewModel: DownloadsViewModel = viewModel(
                factory = viewModelFactory { initializer { DownloadsViewModel(application) } }
            )
            DownloadsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADD_FEED) {
            val viewModel: AddFeedViewModel = viewModel(
                factory = viewModelFactory { initializer { AddFeedViewModel(application) } }
            )
            AddFeedScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onFeedAdded = { feedId ->
                    navController.popBackStack()
                    navController.navigate(Routes.episodeList(feedId))
                }
            )
        }

        composable(
            route = Routes.EPISODE_LIST,
            arguments = listOf(navArgument("feedId") { type = NavType.LongType })
        ) { backStackEntry ->
            val feedId = backStackEntry.arguments?.getLong("feedId") ?: return@composable
            val viewModel: EpisodeListViewModel = viewModel(
                key = "episodeList_$feedId",
                factory = viewModelFactory { initializer { EpisodeListViewModel(application, feedId) } }
            )
            EpisodeListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onPlay = { navController.navigate(Routes.PLAYER) }
            )
        }

        composable(Routes.PLAYER) {
            val viewModel: PlayerViewModel = viewModel(
                factory = viewModelFactory { initializer { PlayerViewModel(application) } }
            )
            PlayerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.QUEUE) {
            val viewModel: QueueViewModel = viewModel(
                factory = viewModelFactory { initializer { QueueViewModel(application) } }
            )
            QueueScreen(
                viewModel = viewModel,
                onBrowsePodcasts = { navigateToTab(Routes.FEED_LIST) },
                onPlay = { navController.navigate(Routes.PLAYER) }
            )
        }
    }
    }
}
