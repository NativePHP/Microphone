package com.nativephp.microphone

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction

/**
 * Functions related to microphone recording operations
 * Namespace: "Microphone.*"
 */
object MicrophoneFunctions {

    // Static recorder for synchronous status queries
    @Volatile
    var microphoneRecorder: MicrophoneRecorder? = null

    /**
     * Start microphone recording
     * Parameters:
     *   - id: (optional) string - Unique identifier for this recording
     *   - event: (optional) string - Custom event class to dispatch when recording completes
     * Returns:
     *   - status: string - "success" or "error"
     * Events:
     *   - Fires "NativePHP\Microphone\Events\MicrophoneRecorded" when recording is stopped
     */
    class Start(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String
            val event = parameters["event"] as? String

            Log.d("MicrophoneFunctions.Start", "🎤 Starting microphone recording with id=$id, event=$event")

            // Must run on main thread for fragment operations
            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = MicrophoneCoordinator.install(activity)
                    coordinator.launchMicrophoneRecorder(id, event)
                } catch (e: Exception) {
                    Log.e("MicrophoneFunctions.Start", "Error: ${e.message}", e)
                }
            }

            return emptyMap()
        }
    }

    /**
     * Stop microphone recording
     * Events:
     *   - Fires "NativePHP\Microphone\Events\MicrophoneRecorded" via Livewire dispatch when stopped
     */
    class Stop(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Log.d("MicrophoneFunctions.Stop", "⏹️ Stopping microphone recording")

            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = MicrophoneCoordinator.install(activity)
                    coordinator.stopRecording()
                } catch (e: Exception) {
                    Log.e("MicrophoneFunctions.Stop", "Error: ${e.message}", e)
                }
            }

            return emptyMap()
        }
    }

    /**
     * Pause microphone recording
     */
    class Pause(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Log.d("MicrophoneFunctions.Pause", "⏸️ Pausing microphone recording")

            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = MicrophoneCoordinator.install(activity)
                    coordinator.pauseRecording()
                } catch (e: Exception) {
                    Log.e("MicrophoneFunctions.Pause", "Error: ${e.message}", e)
                }
            }

            return emptyMap()
        }
    }

    /**
     * Resume microphone recording
     */
    class Resume(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Log.d("MicrophoneFunctions.Resume", "▶️ Resuming microphone recording")

            Handler(Looper.getMainLooper()).post {
                try {
                    val coordinator = MicrophoneCoordinator.install(activity)
                    coordinator.resumeRecording()
                } catch (e: Exception) {
                    Log.e("MicrophoneFunctions.Resume", "Error: ${e.message}", e)
                }
            }

            return emptyMap()
        }
    }

    /**
     * Get current recording status (synchronous - uses static recorder)
     */
    class GetStatus(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Log.d("MicrophoneFunctions.GetStatus", "📊 Getting microphone status")

            // Initialize recorder if needed (can be done off main thread)
            if (microphoneRecorder == null) {
                microphoneRecorder = MicrophoneRecorder(activity.applicationContext, false)
            }

            val status = microphoneRecorder?.getStatus() ?: "idle"
            Log.d("MicrophoneFunctions.GetStatus", "📊 Status: $status")

            return mapOf("status" to status)
        }
    }

    /**
     * Get path to last recording (synchronous - uses static recorder)
     */
    class GetRecording(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            Log.d("MicrophoneFunctions.GetRecording", "📁 Getting last recording path")

            // Initialize recorder if needed
            if (microphoneRecorder == null) {
                microphoneRecorder = MicrophoneRecorder(activity.applicationContext, false)
            }

            val path = microphoneRecorder?.getLastRecording()

            return if (path != null) {
                mapOf("path" to path)
            } else {
                mapOf("path" to "")
            }
        }
    }
}