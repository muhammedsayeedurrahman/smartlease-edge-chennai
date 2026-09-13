package com.smartlease.edge

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.narration.ReportNarratorFactory
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.PropertyReportBuilder
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.ui.AppViewModel
import com.smartlease.edge.ui.screens.*
import com.smartlease.edge.ui.theme.SmartLeaseEdgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The report produced by the last "Generate report" tap, handed to ReportScreen and then
    // to CountersignScreen. Held here rather than rebuilt per screen because rebuilding it
    // would produce a second report object -- a new generatedAt, and therefore a different
    // document -- for the same session.
    var generatedReport by remember { mutableStateOf<InspectionReport?>(null) }
    // The per-report token the backend issues once, on upload. ReportScreen captures it and
    // hands it here so the countersign screen can record the signature server-side; null is
    // a legitimate value and produces an honest "not synced" line there, never a block.
    var generatedReportToken by remember { mutableStateOf<String?>(null) }

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
                },
                onOpenSelfTest = { navController.navigate("selftest") }
            )
        }
        // Restored: the UI rebuild in a9a37b46 dropped this destination while the screen
        // itself stayed in the tree, so the diagnostics page was still compiled, still
        // tested, and completely unreachable -- including from docs/GEMMA_SETUP.md, which
        // tells the reader to open it to confirm which narrator this device picked.
        composable("selftest") {
            SelfTestScreen(onBack = { navController.popBackStack() })
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
                onRoomSelected = { roomId, sessionType ->
                    navController.navigate("room_config/$roomId/$sessionType")
                },
                onGenerateReport = { sessionType ->
                    val property = viewModel.properties.find { it.id == propertyId }
                        ?: return@PropertyDashboardScreen
                    scope.launch {
                        val sessionId = UUID.randomUUID().toString()
                        // Digest, PDF rendering and deduction arithmetic are all real work
                        // over every captured frame's detections; off the main thread so a
                        // property with many areas does not freeze the UI mid-tap.
                        val report = withContext(Dispatchers.Default) {
                            val findings = PropertyReportBuilder.findingsFor(
                                property = property,
                                rooms = viewModel.getRoomsForProperty(propertyId),
                                sessionId = sessionId,
                                sessionType = sessionType
                            )
                            // Selected per report rather than held for the process
                            // lifetime: a loaded Gemma model pins hundreds of megabytes, and
                            // a rental inspection produces a report every few minutes, not
                            // every few seconds. Closed in `finally` so a failure part-way
                            // through cannot leak the native session.
                            val selection = ReportNarratorFactory.create(context)
                            val built = try {
                                ReportGenerator.buildReport(
                                    sessionId = sessionId,
                                    propertyLabel = property.name,
                                    findings = findings,
                                    depositRupees = PropertyReportBuilder.depositRupeesOrNull(property),
                                    // No baseline: this flow has no stored move-in session to
                                    // diff against yet, so a move-out report prices every
                                    // finding. That is the honest reading of "we have no prior
                                    // record", not a bug, but it is why the move-in/move-out
                                    // question is asked.
                                    baselineKeys = emptySet(),
                                    sessionType = sessionType,
                                    narrator = selection.narrator
                                )
                            } finally {
                                selection.narrator.close()
                            }
                            ReportGenerator.renderToPdf(context, built)
                            built
                        }
                        generatedReport = report
                        generatedReportToken = null
                        navController.navigate("report/${report.sessionId}")
                    }
                }
            )
        }
        composable(
            route = "report/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ReportScreen(
                report = generatedReport,
                onBack = { navController.popBackStack() },
                onCountersign = { reportToken ->
                    generatedReportToken = reportToken
                    navController.navigate("countersign")
                }
            )
        }
        composable("countersign") {
            CountersignScreen(
                report = generatedReport,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "room_config/{roomId}/{sessionType}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("sessionType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val sessionType = backStackEntry.arguments?.getString("sessionType") ?: "MOVE_IN"
            RoomConfigScreen(
                viewModel = viewModel,
                roomId = roomId,
                sessionType = sessionType,
                onBack = { navController.popBackStack() },
                onRecordSurface = { rid, surfaceType, sType ->
                    navController.navigate("video_capture/$rid/$surfaceType/$sType")
                }
            )
        }
        composable(
            route = "video_capture/{roomId}/{surfaceType}/{sessionType}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("surfaceType") { type = NavType.StringType },
                navArgument("sessionType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable
            val surfaceType = backStackEntry.arguments?.getString("surfaceType") ?: return@composable
            val sessionType = backStackEntry.arguments?.getString("sessionType") ?: "MOVE_IN"
            VideoCaptureScreen(
                viewModel = viewModel,
                roomId = roomId,
                surfaceType = surfaceType,
                sessionType = sessionType,
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
