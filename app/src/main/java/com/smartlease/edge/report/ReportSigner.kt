package com.smartlease.edge.report

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec

/**
 * Signs a report's [FindingsDigest] with a per-device EC key generated inside the
 * AndroidKeyStore (StrongBox where the chip has one, TEE everywhere else) and never
 * exported. This proves the signature was produced by this specific phone's secure
 * hardware — it does NOT identify who operated it, and it is deliberately not Play
 * Integrity: Play Integrity needs a network round-trip to Google's servers to verify,
 * which this app cannot make since it declares no INTERNET permission. A hardware-backed
 * local signature is the strongest attestation available without breaking that guarantee.
 *
 * The key is reused across every report this device ever signs, so re-rendering the same
 * report twice produces two different (both valid) ECDSA signatures over the same digest —
 * that is expected: ECDSA signing is randomized, not deterministic.
 *
 * [sign] is reachable from a QR code someone else's phone shows to this one (the countersign
 * handshake) — a scanned string is untrusted input, not just this app's own data. Two things
 * keep that from turning this device's key into a "sign anything" oracle for whoever can show
 * it a QR code: [DIGEST_HEX_PATTERN] rejects anything that isn't shaped like a SHA-256 hex
 * digest before it ever reaches the key, and every signature covers [SIGNING_CONTEXT] +
 * the digest, not the bare digest -- so a signature produced here can never be replayed as if
 * it meant something in a different protocol that also happens to sign raw hex strings.
 * [com.smartlease.edge.ui.screens.CountersignScreen] additionally requires an explicit tap
 * before an incoming request is signed, so this never happens without the phone's owner
 * seeing what they are about to attest to.
 */
object ReportSigner {

    private const val KEY_ALIAS = "smartlease_edge_report_key"
    private const val PROVIDER = "AndroidKeyStore"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

    /** What is actually signed is this prefix + the digest, never the bare digest -- see class doc. */
    private const val SIGNING_CONTEXT = "smartlease-edge/countersign/v1\n"

    /** Lowercase hex SHA-256, exactly what [FindingsDigest.sha256Hex] produces. Nothing else is signable. */
    private val DIGEST_HEX_PATTERN = Regex("^[0-9a-f]{64}$")

    data class Attestation(
        val signatureBase64: String,
        val certificateChainBase64: List<String>,
        val hardwareBacked: Boolean
    )

    /**
     * Signs [digestHex], generating this device's key on first use.
     * @throws IllegalArgumentException if [digestHex] is not a 64-character lowercase hex
     * string -- callers scanning an incoming QR must treat this as untrusted input and catch it.
     */
    fun sign(digestHex: String): Attestation {
        require(DIGEST_HEX_PATTERN.matches(digestHex)) {
            "Refusing to sign a value that is not a SHA-256 hex digest"
        }
        val keyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        val privateKey = existingKey(keyStore) ?: generateKey(keyStore)
        val signatureBytes = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(signedMessage(digestHex))
        }.sign()

        val chain = keyStore.getCertificateChain(KEY_ALIAS) ?: arrayOf()
        return Attestation(
            signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP),
            certificateChainBase64 = chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) },
            hardwareBacked = isHardwareBacked(privateKey)
        )
    }

    /**
     * Verifies a signature captured from another device's [sign] call. Used both to check a
     * countersignature immediately when it is scanned in, and again at PDF render time so the
     * printed page reflects a signature that still verifies, not just one that once did.
     *
     * This checks only that the signature matches the digest under the supplied certificate's
     * public key -- it does NOT validate that certificate's chain against a trusted hardware
     * attestation root, so it does not itself prove the signing key was hardware-backed. See
     * [com.smartlease.edge.data.CountersignatureEntity.hardwareBackedSelfReported].
     */
    fun verify(digestHex: String, signatureBase64: String, certificateBase64: String): Boolean = try {
        if (!DIGEST_HEX_PATTERN.matches(digestHex)) {
            false
        } else {
            val certBytes = Base64.decode(certificateBase64, Base64.NO_WRAP)
            val certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(certBytes.inputStream())
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            Signature.getInstance(SIGNATURE_ALGORITHM).apply {
                initVerify(certificate)
                update(signedMessage(digestHex))
            }.verify(signatureBytes)
        }
    } catch (e: Exception) {
        false
    }

    private fun signedMessage(digestHex: String): ByteArray =
        (SIGNING_CONTEXT + digestHex).toByteArray(Charsets.UTF_8)

    private fun existingKey(keyStore: KeyStore): PrivateKey? =
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry)?.privateKey

    /**
     * StrongBox first, TEE fallback. `setIsStrongBoxBacked` needs API 28+ (this app's minSdk
     * is 26) and throws `StrongBoxUnavailableException` on chips without a StrongBox module
     * even above API 28 -- both cases fall through to the plain (TEE-backed) spec below.
     */
    private fun generateKey(keyStore: KeyStore): PrivateKey {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
        val strongBoxResult = if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                generator.initialize(buildSpec(strongBox = true))
                generator.generateKeyPair()
            }
        } else {
            Result.failure(UnsupportedOperationException("StrongBox requires API 28+"))
        }
        if (strongBoxResult.isFailure) {
            generator.initialize(buildSpec(strongBox = false))
            generator.generateKeyPair()
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry).privateKey
    }

    private fun buildSpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setAttestationChallenge(KEY_ALIAS.toByteArray(Charsets.UTF_8))
        if (strongBox) builder.setIsStrongBoxBacked(true)
        return builder.build()
    }

    // isInsideSecureHardware is deprecated in favour of getSecurityLevel() (API 31+), but
    // minSdk here is 26 -- this is the only property that works across the whole range.
    @Suppress("DEPRECATION")
    private fun isHardwareBacked(key: PrivateKey): Boolean = try {
        val factory = KeyFactory.getInstance(key.algorithm, PROVIDER)
        factory.getKeySpec(key, KeyInfo::class.java).isInsideSecureHardware
    } catch (e: Exception) {
        false
    }
}
