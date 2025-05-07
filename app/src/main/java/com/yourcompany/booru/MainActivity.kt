package com.yourcompany.booru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.composable
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yourcompany.booru.api.BooruApiClient
import com.yourcompany.booru.data.AppDatabase
import com.yourcompany.booru.data.DatabaseProvider
import com.yourcompany.booru.data.ImagePost
import com.yourcompany.booru.data.toImagePost
import com.yourcompany.booru.ui.BlockedTagsScreen
import com.yourcompany.booru.ui.FavoritesScreen
import com.yourcompany.booru.ui.FeedScreen
import com.yourcompany.booru.ui.FullScreenImageScreen
import com.yourcompany.booru.ui.MainScreen
import com.yourcompany.booru.ui.ShortiesScreen
import com.yourcompany.booru.ui.theme.BooruViewerTheme
import com.yourcompany.booru.viewmodel.MainViewModel
import com.yourcompany.booru.viewmodel.MainViewModelFactory
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalAnimationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = DatabaseProvider.getDatabase(this)

        setContent {
            BooruViewerTheme {
                val navController = rememberAnimatedNavController()
                AppNavHost(navController = navController, database = database)
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppNavHost(
    navController: NavController,
    database: AppDatabase
) {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(context))

    // Load booru options from SharedPreferences
    val sharedPreferences = context.getSharedPreferences("BooruPrefs", android.content.Context.MODE_PRIVATE)
    val booruOptionsJson = sharedPreferences.getString("booruOptions", null)
    val booruOptions: MutableList<String> = if (booruOptionsJson != null) {
        Gson().fromJson(booruOptionsJson, object : TypeToken<List<String>>() {}.type)
    } else {
        mutableListOf(
            "https://rule34.xxx/"
        ).also {
            with(sharedPreferences.edit()) {
                putString("booruOptions", Gson().toJson(it))
                apply()
            }
        }
    }

    // Set initial booru if not already set
    if (viewModel.selectedBooru.value.isEmpty()) {
        viewModel.selectedBooru.value = booruOptions.firstOrNull() ?: "https://rule34.xxx/"
        BooruApiClient.setBaseUrl(viewModel.selectedBooru.value)
    }

    AnimatedNavHost(
        navController = navController as NavHostController,
        startDestination = "main"
    ) {
        composable(
            route = "main",
            enterTransition = { fadeIn(animationSpec = tween(300)) },
            exitTransition = { fadeOut(animationSpec = tween(300)) },
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = { fadeOut(animationSpec = tween(300)) }
        ) {
            MainScreen(
                navController = navController,
                viewModel = viewModel,
                database = database
            )
        }
        composable(
            route = "feed",
            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) }
        ) {
            FeedScreen(
                navController = navController,
                viewModel = viewModel,
                database = database
            )
        }
        composable(
            route = "favorites",
            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) }
        ) {
            FavoritesScreen(
                navController = navController,
                database = database
            )
        }
        composable(
            route = "blocked_tags",
            enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) }
        ) {
            BlockedTagsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }
        composable(
            route = "shorties",
            enterTransition = { slideInVertically(initialOffsetY = { 1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            exitTransition = { slideOutVertically(targetOffsetY = { -1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) },
            popEnterTransition = { slideInVertically(initialOffsetY = { -1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            popExitTransition = { slideOutVertically(targetOffsetY = { 1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) }
        ) {
            ShortiesScreen(
                viewModel = viewModel,
                navController = navController // Передаём navController
            )
        }
        composable(
            route = "fullScreenImageScreen/{id}?index={index}&booru={booru}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("index") {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument("booru") {
                    type = NavType.StringType
                    defaultValue = "https://rule34.xxx/"
                }
            ),
            enterTransition = { slideInVertically(initialOffsetY = { 1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            exitTransition = { slideOutVertically(targetOffsetY = { 1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) },
            popEnterTransition = { slideInVertically(initialOffsetY = { -1000 }, animationSpec = tween(500)) + fadeIn(animationSpec = tween(500)) },
            popExitTransition = { slideOutVertically(targetOffsetY = { -1000 }, animationSpec = tween(500)) + fadeOut(animationSpec = tween(500)) }
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id") ?: 0L
            val index = backStackEntry.arguments?.getInt("index") ?: 0
            val booru = backStackEntry.arguments?.getString("booru")?.let { URLDecoder.decode(it, "UTF-8") } ?: "https://rule34.xxx/"

            // Найти пост по id и booru
            val posts = if (navController.previousBackStackEntry?.destination?.route == "favorites") {
                database.imagePostDao().getFavoritePosts()
                    .collectAsState(initial = emptyList())
                    .value
                    .map { entity -> entity.toImagePost() }
            } else {
                viewModel.posts.value
            }

            val post = posts.firstOrNull { it.id == id && it.sourceType == booru }
                ?: posts.getOrNull(index) // Fallback на индекс

            if (post != null) {
                FullScreenImageScreen(
                    post = post,
                    navController = navController,
                    posts = posts,
                    initialIndex = index,
                    viewModel = viewModel,
                    database = database
                )
            } else {
                // Обработка случая, если пост не найден
                navController.popBackStack()
            }
        }
    }
}