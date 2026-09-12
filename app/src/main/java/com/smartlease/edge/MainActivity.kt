package com.smartlease.edge

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.screens.*
import com.smartlease.edge.ui.theme.SmartLeaseEdgeTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* handled by re-composition reading ContextCompat.checkSelfPermission */ }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )

        val sharedPrefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFirstLaunch = sharedPrefs.getBoolean("is_first_launch", true)

        setContent {
            SmartLeaseEdgeTheme {
                SmartLeaseApp(
                    viewModel = appViewModel,
                    isFirstLaunch = isFirstLaunch,
                    onFirstLaunchComplete = {
                        sharedPrefs.edit().putBoolean("is_first_launch", false).apply()
                    }
                )
            }
        }
    }
}

@Composable
fun SmartLeaseApp(
    viewModel: AppViewModel,
    isFirstLaunch: Boolean = false,
    onFirstLaunchComplete: () -> Unit = {}
) {
    val navController = rememberNavController()

    val startDestination = if (isFirstLaunch) "welcome" else "home"

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() }
    ) {
        composable("welcome") {
            WelcomeScreen(
                onCreateProperty = { isFlat ->
                    onFirstLaunchComplete()
                    navController.navigate("home") {
                        popUpTo("welcome") { inclusive = true }
                    }
                    navController.navigate("create_property/$isFlat")
                }
            )
        }
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onCreateProperty = { isFlat -> 
                    navController.navigate("create_property/$isFlat") 
                },
                onPropertySelected = { propertyId ->
                    navController.navigate("property_dashboard/$propertyId")
                }
            )
        }
        composable(
            route = "create_property/{isFlat}",
            arguments = listOf(navArgument("isFlat") { type = NavType.BoolType })
        ) { backStackEntry ->
            val isFlat = backStackEntry.arguments?.getBoolean("isFlat") ?: false
            CreatePropertyScreen(
                viewModel = viewModel,
                isFlat = isFlat,
                onBack = { navController.popBackStack() },
                onPropertyCreated = { propertyId ->
                    navController.popBackStack()
                    navController.navigate("property_dashboard/$propertyId")
                }
            )
        }
        composable(
            route = "property_dashboard/{propertyId}",
            arguments = listOf(navArgument("propertyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val propertyId = backStackEntry.arguments?.getString("propertyId") ?: return@composable
            PropertyDashboardScreen(
                viewModel = viewModel,
                propertyId = propertyId,
                onBack = { navController.popBackStack("home", inclusive = false) },
                onRoomSelected = { roomId ->
                    navController.navigate("room_config/$roomId")
                }
            )
        }
        composable(
            route = "room_config/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType })
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            RoomConfigScreen(
                viewModel = viewModel,
                roomId = roomId,
                onBack = { navController.popBackStack() },
                onRecordSurface = { rid, surfaceType ->
                    navController.navigate("video_capture/$rid/$surfaceType")
                }
            )
        }
        composable(
            route = "video_capture/{roomId}/{surfaceType}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("surfaceType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val surfaceType = backStackEntry.arguments?.getString("surfaceType") ?: return@composable
            
            VideoCaptureScreen(
                viewModel = viewModel,
                roomId = roomId,
                surfaceType = surfaceType,
                onExit = { navController.popBackStack() },
                onAddAnotherRoom = {
                    val room = viewModel.rooms.find { it.id == roomId }
                    if (room != null) {
                        navController.popBackStack("property_dashboard/${room.propertyId}", inclusive = false)
                    } else {
                        navController.popBackStack("home", inclusive = false)
                    }
                }
            )
        }
    }
}
