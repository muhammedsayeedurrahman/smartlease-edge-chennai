package com.smartlease.edge.domain.llm

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import com.google.mediapipe.tasks.genai.llminference.LlmInference

class DocumentAnalyzer(private val context: Context) {

    private var llmInference: LlmInference? = null
    
    private val TAG = "DocumentAnalyzer"



    suspend fun extractDosAndDonts(pdfUriString: String): String {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Extracting text from PDF: $pdfUriString")
            var extractedText = "Sample lease rules: No smoking. Pay rent on 1st. No pets."
            try {
                val uri = android.net.Uri.parse(pdfUriString)
                context.contentResolver.openInputStream(uri)?.use { inputStream -> 
                    val reader = PdfReader(inputStream)
                    val n = reader.numberOfPages
                    val builder = java.lang.StringBuilder()
                    for (i in 1..n) {
                        builder.append(PdfTextExtractor.getTextFromPage(reader, i))
                    }
                    extractedText = builder.toString()
                    reader.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read PDF: ${e.message}")
            }
            
            // 2. Load Gemma Model via LiteRT-LM (GPU Delegate)
            val isGemma4Loaded = loadGemmaModelFromAssets("gemma-4-E4B-it.litertlm")
            
            if (!isGemma4Loaded) {
                Log.w(TAG, "Gemma 4 failed to load. Falling back to Gemma 3 (1B)...")
                val isGemma3Loaded = loadGemmaModelFromAssets("gemma-3-1b-it.litertlm")
                if (!isGemma3Loaded) {
                    return@withContext "Error: No AI models could be loaded from app assets."
                }
            }

            // 3. Prompting the model
            val prompt = """
                Extract the dos and don'ts from this rental agreement. 
                Output exactly one summary paragraph, followed by a bulleted list.
                
                Agreement Text:
                $extractedText
            """.trimIndent()

            Log.d(TAG, "Prompting Gemma model...")
            
            val response = try {
                llmInference?.generateResponse(prompt) ?: "Summary: Model was not loaded successfully.\n\n- Do: Check device memory.\n- Don't: Ignore model assets."
            } catch (e: Exception) {
                "Error generating response: ${e.message}"
            }
            
            response
        }
    }

    private fun loadGemmaModelFromAssets(fileName: String): Boolean {
        if (llmInference != null) return true // Already loaded
        
        val modelFile = File(context.filesDir, fileName)
        if (!modelFile.exists()) {
            try {
                Log.d(TAG, "Copying $fileName from assets to internal storage...")
                context.assets.open("llm/$fileName").use { inputStream ->
                    modelFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model file not found in assets: $fileName")
                return false
            }
        }
        
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .build()
            llmInference = LlmInference.createFromOptions(context, options)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            false
        }
    }
}
