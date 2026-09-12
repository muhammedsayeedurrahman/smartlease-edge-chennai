package com.smartlease.edge.report

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the provenance sentences the generated PDF prints about its own origin.
 *
 * The report is offered as a draft certificate under Section 63 of the Bharatiya Sakshya
 * Adhiniyam -- a document whose entire purpose is to state where evidence came from. For most
 * of this project's life it printed "no network transmission at any point" and "it declares no
 * INTERNET permission", and both were true, because the manifest declared no INTERNET
 * permission. Adding backend sync falsified both while the strings sat untouched. That is the
 * failure this test exists to catch: a claim that was accurate when written, was never edited,
 * and quietly stopped being true.
 *
 * These assert on [ReportGenerator.mannerOfProduction] and
 * [ReportGenerator.attestationProvenanceNote] -- the functions the renderer actually calls --
 * rather than on copies of the text. A test holding its own copy of the sentence would have
 * passed happily throughout the period the real one was false.
 *
 * Instrumented because the premise is the *installed package's* permission set as the platform
 * merged it, which only the real PackageManager can report.
 */
@RunWith(AndroidJUnit4::class)
class ReportProvenanceClaimsDeviceTest {

    private fun declaredPermissions(): Set<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        return info.requestedPermissions?.toSet() ?: emptySet()
    }

    private fun bothConfigurations(): List<String> = listOf(true, false).flatMap { syncEnabled ->
        listOf(
            ReportGenerator.mannerOfProduction(syncEnabled),
            ReportGenerator.attestationProvenanceNote(syncEnabled)
        )
    }

    @Test
    fun theAppDeclaresInternet_whichIsThePremiseOfEverythingBelow() {
        // Not a claim that INTERNET is desirable. If it is ever removed, the stronger offline
        // wording becomes legitimate again -- and this failing is the prompt to restore it,
        // rather than the wording silently staying weaker than the truth.
        assertTrue(
            "INTERNET is gone; revisit the PDF provenance wording, it may now understate the app.",
            android.Manifest.permission.INTERNET in declaredPermissions()
        )
    }

    @Test
    fun noProvenanceSentenceDeniesTheInternetPermission() {
        bothConfigurations().forEach { sentence ->
            assertFalse(
                "A report sentence still claims the app has no INTERNET permission: $sentence",
                sentence.contains("no INTERNET permission")
            )
        }
    }

    @Test
    fun noProvenanceSentenceClaimsNothingIsEverTransmitted() {
        bothConfigurations().forEach { sentence ->
            assertFalse(
                "A report sentence still claims nothing is ever transmitted: $sentence",
                sentence.contains("no network transmission at any point")
            )
        }
    }

    @Test
    fun theSyncOnCertificateStatesWhatIsUploadedAndWhatIsNot() {
        val sentence = ReportGenerator.mannerOfProduction(syncEnabled = true)

        // The claim that survives is the one still structurally guaranteed: the upload body has
        // no bitmap, byte-array or file-path field and the server schema has no binary column,
        // so media cannot leave even with sync on. It must not be softened away along with the
        // false claims -- it is the part a tenant actually cares about.
        assertTrue(
            "The sync-on certificate no longer says the inspection media stays on the phone: $sentence",
            sentence.contains("photos, video and audio are not uploaded")
        )
        // And it must name what DOES leave, rather than mentioning only what does not.
        assertTrue(
            "The sync-on certificate does not say the findings digest is uploaded: $sentence",
            sentence.contains("upload the findings digest")
        )
    }

    @Test
    fun theSyncOffCertificateSaysPlainlyThatNothingWasSent() {
        val sentence = ReportGenerator.mannerOfProduction(syncEnabled = false)

        assertTrue(
            "With sync off the certificate should say nothing was transmitted: $sentence",
            sentence.contains("nothing was transmitted")
        )
        // Still no absolute claim about the app's capability -- only about this build's config.
        assertFalse(sentence.contains("cannot make"))
    }

    @Test
    fun theAttestationExplainsPlayIntegrityByReachability_notByPermission() {
        val sentence = ReportGenerator.attestationProvenanceNote(syncEnabled = true)

        assertTrue(
            "The Play Integrity rationale should rest on offline operability: $sentence",
            sentence.contains("no signal")
        )
        assertTrue(
            "With sync on, the attestation should own the INTERNET declaration: $sentence",
            sentence.contains("does declare the INTERNET permission")
        )
    }
}
