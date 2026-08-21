package com.listentome.app.ui.navigation

import android.app.Application
import androidx.compose.runtime.Composable
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
import com.listentome.app.ui.episodelist.EpisodeListScreen
import com.listentome.app.ui.episodelist.EpisodeListViewModel
import com.listentome.app.ui.feedlist.FeedListScreen
import com.listentome.app.ui.feedlist.FeedListViewModel
import com.listentome.app.ui.player.PlayerScreen
import com.listentome.app.ui.player.PlayerViewModel

private object Routes {
    const val FEED_LIST = "feedList"
    const val ADD_FEED = "addFeed"
    const val EPISODE_LIST = "episodeList/{feedId}"
    const val PLAYER = "player"

    fun episodeList(feedId: Long) = "episodeList/$feedId"
}

@Composable
fun ListenToMeNavGraph(application: Application) {
    val navController: NavHostController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.FEED_LIST) {
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
    }
}
