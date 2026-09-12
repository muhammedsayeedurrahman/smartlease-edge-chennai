package com.smartlease.edge.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

object LLMEngine {
    var isInitialized = false
        private set

    init {
        try {
            System.loadLibrary("native-lib")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private external fun initGenie(modelPath: String): Boolean
    private external fun generateToken(prompt: String): String

    suspend fun initialize(context: Context): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Ensure model directory exists
                val modelDir = File(context.filesDir, "qnn_models")
                if (!modelDir.exists()) modelDir.mkdirs()

                // Initialize the Qualcomm Genie runtime with the models path
                val success = initGenie(modelDir.absolutePath)
                isInitialized = success
                success
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun generateReportStream(prompt: String): Flow<String> = callbackFlow {
        if (!isInitialized) {
            trySend("Qualcomm Genie LLM not initialized.")
            close()
            return@callbackFlow
        }

        try {
            // Offload the blocking C++ call to the IO dispatcher to avoid freezing the UI thread
            val fullResponse = withContext(Dispatchers.IO) {
                generateToken(prompt)
            }
            
            val words = fullResponse.split(" ")
            for (word in words) {
                trySend("$word ")
                kotlinx.coroutines.delay(50) // simulate token generation speed
            }
            close()
        } catch (e: Exception) {
            trySend("\nError generating report: ${e.message}")
            close()
        }

        awaitClose {
            // Clean up
        }
    }
}
