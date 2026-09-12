package com.smartlease.edge.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The self-test screen tells the reader to screenshot it, which makes every line on it a
 * statement that can end up in front of a judge. So its claims are checked against the
 * installed package rather than against a copy of the expected wording.
 *
 * This exists because the screen went on printing "INTERNET declared: YES - THIS IS A
 * REGRESSION, the app must not have it" after the app started declaring INTERNET deliberately
 * for report sync. Nothing failed: the verdict was a constant, and a constant cannot notice
 * that the world changed underneath it.
 */
@RunWith(AndroidJUnit4::class)
class SelfTestClaimsDeviceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun privacyRows(): List<Pair<String, String>> =
        SelfTest.run(context).single { it.title == "PRIVACY" }.rows

    private fun declaredPermissions(): List<String> =
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toList()
            ?: emptyList()

    @Test
    fun theInternetRowMatchesWhatThePackageActuallyDeclares() {
        val declared = declaredPermissions().contains(android.Manifest.permission.INTERNET)
        val row = privacyRows().single { it.first == "INTERNET declared" }.second

        if (declared) {
            assertTrue("package declares INTERNET but the row says \"$row\"", row.startsWith("YES"))
        } else {
            assertTrue("package does not declare INTERNET but the row says \"$row\"", row.startsWith("NO"))
        }
    }

    @Test
    fun aDeliberatelyDeclaredPermissionIsNotReportedAsARegression() {
        // The specific stale verdict this test was written for. Calling a deliberate,
        // documented permission a regression is not a harmless bit of old copy -- it is the
        // app accusing itself of a defect, on a page the reader is told to screenshot.
        val declared = declaredPermissions().contains(android.Manifest.permission.INTERNET)
        val row = privacyRows().single { it.first == "INTERNET declared" }.second

        if (declared) {
            assertFalse(
                "the INTERNET row still calls a deliberate permission a regression: \"$row\"",
                row.contains("REGRESSION", ignoreCase = true)
            )
        }
    }

    @Test
    fun theMediaClaimIsPresentAndIsAboutMedia() {
        // The row that carries the privacy claim actually worth making. It survives sync
        // being switched on, because it is a statement about the shape of the upload
        // (ReportUploadRequest and CountersignRequest carry only scalars -- no byte array,
        // no file path, and the Retrofit surface has no multipart endpoint) rather than
        // about whether the network is reachable.
        val row = privacyRows().single { it.first.contains("photos", ignoreCase = true) }
        assertTrue(
            "the media row should say media cannot leave, got \"${row.second}\"",
            row.second.contains("cannot leave", ignoreCase = true)
        )
    }

    @Test
    fun noPrivacyRowClaimsTheAppHasNoInternetPermissionWhenItDoes() {
        // Same failure mode as the PDF carried before fb11bc5c: a sentence that is
        // disprovable in ten seconds with `dumpsys`. Pinned here too, because this screen
        // and that document make claims about the same thing.
        val declared = declaredPermissions().contains(android.Manifest.permission.INTERNET)
        if (!declared) return

        privacyRows().forEach { (label, value) ->
            val line = "$label: $value"
            assertFalse(
                "self-test still claims the app has no INTERNET permission: \"$line\"",
                line.contains("no INTERNET permission", ignoreCase = true) ||
                    line.contains("must not have it", ignoreCase = true)
            )
        }
    }
}
