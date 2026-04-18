package com.mlainton.nova

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Tony's On-Device Model Manager.
 *
 * Uses MediaPipe LLM Inference API with Gemma 2B to run Tony
 * completely offline — no internet required for basic responses.
 *
 * Model is downloaded once (~1.5GB) and stored on device.
 * Falls back to cloud (backend) if model not yet downloaded or unavailable.
 *
 * This gives Tony true independence from external APIs.
 */
object OnDeviceModel {

    private const val TAG = "TONY_ON_DEVICE"
    private const val MODEL_FILENAME = "gemma-2b-it-cpu-int4.bin"
    private const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/llm_inference/gemma-2b-it-cpu-int4/float32/1/gemma-2b-it-cpu-int4.bin"

    private var isInitialised = false
    private var isAvailable = false
    private var llmInference: Any? = null // MediaPipe LlmInference — late bound to avoid crash if not installed

    fun getModelPath(context: Context): File {
        return File(context.filesDir, MODEL_FILENAME)
    }

    fun isModelDownloaded(context: Context): Boolean {
        val file = getModelPath(context)
        return file.exists() && file.length() > 100_000_000L // > 100MB = real model
    }

    fun isReady(): Boolean = isAvailable && llmInference != null

    /**
     * Initialise the on-device model.
     * Call once at startup after checking model is downloaded.
     */
    fun initialise(context: Context): Boolean {
        if (isInitialised) return isAvailable
        isInitialised = true

        if (!isModelDownloaded(context)) {
            Log.d(TAG, "Model not downloaded yet")
            return false
        }

        return try {
            val modelPath = getModelPath(context).absolutePath

            // Use reflection to avoid hard dependency crash if MediaPipe not available
            val optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
            val builderClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions\$Builder")
            val builder = builderClass.getDeclaredConstructor().newInstance()
            builderClass.getMethod("setModelPath", String::class.java).invoke(builder, modelPath)
            builderClass.getMethod("setMaxTokens", Int::class.java).invoke(builder, 1024)
            builderClass.getMethod("setMaxTopK", Int::class.java).invoke(builder, 40)
            val options = builderClass.getMethod("build").invoke(builder)

            val inferenceClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            llmInference = inferenceClass.getMethod("createFromOptions", Context::class.java, optionsClass)
                .invoke(null, context, options)

            isAvailable = true
            Log.d(TAG, "On-device model initialised successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "On-device model init failed: ${e.message}")
            isAvailable = false
            false
        }
    }

    /**
     * Generate a response using the on-device model.
     * Returns null if model not available.
     */
    fun generate(prompt: String, systemPrompt: String = ""): String? {
        val inference = llmInference ?: return null

        return try {
            // Format for Gemma instruction tuning
            val formattedPrompt = buildString {
                if (systemPrompt.isNotEmpty()) {
                    append("<start_of_turn>system\n")
                    append(systemPrompt.take(500)) // Keep system prompt brief for on-device
                    append("<end_of_turn>\n")
                }
                append("<start_of_turn>user\n")
                append(prompt)
                append("<end_of_turn>\n")
                append("<start_of_turn>model\n")
            }

            val generateMethod = inference.javaClass.getMethod("generateResponse", String::class.java)
            val result = generateMethod.invoke(inference, formattedPrompt) as? String
            result?.trim()
        } catch (e: Exception) {
            Log.e(TAG, "On-device generation failed: ${e.message}")
            null
        }
    }

    /**
     * Download the model in the background.
     * Shows progress via callback.
     */
    fun downloadModel(context: Context, onProgress: (Int) -> Unit, onComplete: (Boolean) -> Unit) {
        Thread {
            try {
                val outputFile = getModelPath(context)
                val url = java.net.URL(MODEL_URL)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connect()

                val fileSize = connection.contentLengthLong
                val input = connection.inputStream
                val output = outputFile.outputStream()

                val buffer = ByteArray(8192)
                var downloaded = 0L
                var bytes: Int

                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (fileSize > 0) {
                        val progress = (downloaded * 100 / fileSize).toInt()
                        onProgress(progress)
                    }
                }

                output.close()
                input.close()
                Log.d(TAG, "Model downloaded successfully")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed: ${e.message}")
                onComplete(false)
            }
        }.start()
    }

    fun shutdown() {
        try {
            llmInference?.let {
                it.javaClass.getMethod("close").invoke(it)
            }
        } catch (_: Exception) {}
        llmInference = null
        isAvailable = false
        isInitialised = false
    }
}
