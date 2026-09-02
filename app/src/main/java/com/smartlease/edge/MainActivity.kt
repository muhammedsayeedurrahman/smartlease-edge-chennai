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
import com.smartlease.edge.data.AppDatabase
import com.smartlease.edge.report.InspectionReport
import com.smartlease.edge.report.ReportGenerator
import com.smartlease.edge.ui.screens.HomeScreen
import com.smartlease.edge.ui.screens.ReportScreen
import com.smartlease.edge.ui.screens.WalkthroughScreen
import com.smartlease.edge.ui.theme.SmartLeaseEdgeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* handled by re-composition reading ContextCompat.checkSelfPermission */ }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var loadedReport by remember { mutableStateOf<InspectionReport?>(null) }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onStartWalkthrough = { navController.navigate("walkthrough") },
                onViewReports = { navController.navigate("report/none") }
            )
        }
        composable("walkthrough") {
            WalkthroughScreen(
                onReportGenerated = { sessionId ->
                    scope.launch {
                        val db = AppDatabase.get(context)
                        val findings = db.inspectionDao().findingsForSessionOnce(sessionId)
                        loadedReport = ReportGenerator.buildReport(sessionId, "Demo Property, Chennai", findings)
                        navController.navigate("report/$sessionId")
                    }
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
