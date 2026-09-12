package com.smartlease.edge

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.smartlease.edge.data.SessionType
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.ui.screens.HomeScreen
import com.smartlease.edge.ui.screens.ReportScreen
import com.smartlease.edge.ui.screens.SelfTestScreen
import com.smartlease.edge.ui.screens.TapCaptureScreen
import com.smartlease.edge.ui.screens.WalkthroughScreen
import com.smartlease.edge.ui.theme.SmartLeaseEdgeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* handled by re-composition reading ContextCompat.checkSelfPermission */ }

        // Only what the app uses. Location was requested here and never read.
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )

        setContent {
            SmartLeaseEdgeTheme {
                SmartLeaseApp()
            }
        }
    }
}

@Composable
fun SmartLeaseApp() {
    val navController = rememberNavController()
    var loadedReport by remember { mutableStateOf<InspectionReport?>(null) }
    // Set by HomeScreen just before navigating, read once WalkthroughScreen composes. A nav
    // route argument would round-trip this through String just to parse it straight back to
    // an enum -- this is in-process navigation between two screens in the same Activity.
    var pendingSessionType by remember { mutableStateOf(SessionType.MOVE_OUT) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // No "past reports" entry: there is no session-list query, so the button only
            // ever reached an empty report screen. A missing feature costs nothing; a broken
            // one a judge taps costs trust.
            HomeScreen(
                onStartWalkthrough = { sessionType ->
                    pendingSessionType = sessionType
                    navController.navigate("walkthrough")
                },
                onOpenSelfTest = { navController.navigate("selftest") },
                onOpenTapCapture =
                    if (BuildConfig.DEBUG) ({ navController.navigate("tapcapture") }) else null
            )
        }
        if (BuildConfig.DEBUG) {
            composable("tapcapture") {
                TapCaptureScreen(onBack = { navController.popBackStack() })
            }
        }
        // Ships in release on purpose: the judging handset is a loaner and there may be
        // no cable, no laptop and no adb at the venue.
        composable("selftest") {
            SelfTestScreen(onBack = { navController.popBackStack() })
        }
        composable("walkthrough") {
            WalkthroughScreen(
                sessionType = pendingSessionType,
                // The report is already built and rendered by the time this fires. Re-querying
                // and rebuilding it here produced a second report object for the same session,
                // with a different generatedAt, for no benefit.
                onReportGenerated = { report ->
                    loadedReport = report
                    navController.navigate("report/${report.sessionId}")
                }
            )
        }
        composable(
            route = "report/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            ReportScreen(
                report = loadedReport,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
