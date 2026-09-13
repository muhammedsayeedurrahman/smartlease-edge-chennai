package com.smartlease.edge.sync

import com.smartlease.edge.BuildConfig

/**
 * The one place that turns build-time configuration into a [SyncConfig].
 *
 * Kept out of [SyncConfig] itself so that class stays a plain data class with no dependency
 * on generated `BuildConfig` -- it is constructed directly in JVM unit tests, and a type that
 * reaches for generated build constants in its own defaults is a type that is awkward to test
 * and easy to accidentally couple to a particular build variant.
 */
fun buildTimeSyncConfig(): SyncConfig = SyncConfig(
    // A build with no key configured is a build that cannot talk to the backend. Rather than
    // sending doomed requests every time a report is generated, sync is left off unless the
    // key was actually supplied at build time (see app/build.gradle.kts). This keeps the
    // failure visible in one place -- the build command -- instead of as a recurring error
    // banner in front of whoever is holding the phone.
    baseUrl = BuildConfig.SMARTLEASE_BASE_URL.ifBlank { SyncConfig.DEFAULT_BASE_URL },
    syncEnabled = BuildConfig.SMARTLEASE_API_KEY.isNotBlank(),
    apiKey = BuildConfig.SMARTLEASE_API_KEY.ifBlank { null }
)
