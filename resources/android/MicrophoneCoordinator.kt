package com.nativephp.microphone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.nativephp.microphone.services.AudioRecordingService
import com.nativephp.mobile.utils.WebViewProvider
import org.json.JSONObject

/**
 * MicrophoneCoordinator handles the microphone recording lifecycle and permissions.
 * It's a headless fragment that manages:
 * - RECORD_AUDIO permission requests
 * - MicrophoneRecorder lifecycle
 * - Event dispatching back to PHP/Livewire
 */
class MicrophoneCoordinator : Fragment() {

    private var pendingRecordingId: String? = null
    private var pendingRecordingEvent: String? = null
    private var enableBackgroundRecording: Boolean = false

    // Use the static recorder from MicrophoneFunctions
    private val microphoneRecorder: MicrophoneRecorder?
        get() = MicrophoneFunctions.microphoneRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "🎤 MicrophoneCoordinator created")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up recorder
        MicrophoneFunctions.microphoneRecorder?.release()
        MicrophoneFunctions.microphoneRecorder = null
        Log.d(TAG, "🧹 MicrophoneCoordinator destroyed")
    }

    // Audio permission launcher
    private val audioPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "🔒 Audio permission result: $granted")

            if (granted) {
                // Permission granted, proceed with recording
                proceedWithRecording()
            } else {
                // Permission denied
                val context = requireContext()
                Log.e(TAG, "❌ Audio permission denied")
                Toast.makeText(context, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show()

                // Dispatch cancelled event
                val cancelEvent = "NativePHP\\Microphone\\Events\\MicrophoneCancelled"
                val payload = JSONObject().apply {
                    put("cancelled", true)
                    put("reason", "permission_denied")
                    if (pendingRecordingId != null) {
                        put("id", pendingRecordingId)
                    }
                }
                dispatch(cancelEvent, payload.toString())

                // Clean up
                pendingRecordingId = null
                pendingRecordingEvent = null
            }
        }

    /**
     * Start microphone recording with permission handling
     */
    fun launchMicrophoneRecorder(id: String? = null, event: String? = null, backgroundRecording: Boolean = false) {
        val context = requireContext()

        Log.d(TAG, "🎤 launchMicrophoneRecorder called - id=$id, event=$event, background=$backgroundRecording")

        // Store parameters for later use
        pendingRecordingId = id
        pendingRecordingEvent = event
        enableBackgroundRecording = backgroundRecording

        // Store id and event in SharedPreferences for later retrieval in Stop
        val prefs = context.getSharedPreferences("microphone_recording", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("pending_id", id)
            .putString("pending_event", event ?: "NativePHP\\Microphone\\Events\\MicrophoneRecorded")
            .apply()

        // Check audio permission first
        val audioPermissionGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!audioPermissionGranted) {
            Log.d(TAG, "🔒 Audio permission not granted, requesting permission")
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }

        // Permission already granted, proceed with recording
        proceedWithRecording()
    }

    private fun proceedWithRecording() {
        val context = requireContext()

        Log.d(TAG, "🎤 proceedWithRecording - starting MicrophoneRecorder")

        // Check if already recording - don't show error toast for duplicate requests
        val currentStatus = MicrophoneFunctions.microphoneRecorder?.getStatus()
        if (currentStatus == "recording" || currentStatus == "paused") {
            Log.d(TAG, "🎤 Already recording (status=$currentStatus), ignoring duplicate start request")
            return
        }

        try {
            // Initialize recorder if needed
            if (MicrophoneFunctions.microphoneRecorder == null) {
                MicrophoneFunctions.microphoneRecorder = MicrophoneRecorder(context, enableBackgroundRecording)
            }

            // Start recording
            val success = microphoneRecorder?.start() ?: false

            if (success) {
                Log.d(TAG, "✅ Recording started successfully")
            } else {
                Log.e(TAG, "❌ Failed to start recording")
                Toast.makeText(context, "Failed to start recording", Toast.LENGTH_SHORT).show()

                // Dispatch error event
                val cancelEvent = "NativePHP\\Microphone\\Events\\MicrophoneCancelled"
                val payload = JSONObject().apply {
                    put("cancelled", true)
                    put("reason", "start_failed")
                    if (pendingRecordingId != null) {
                        put("id", pendingRecordingId)
                    }
                }
                dispatch(cancelEvent, payload.toString())
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error starting recording: ${e.message}", e)
            Toast.makeText(context, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        // Clean up pending state (id/event are stored in SharedPreferences for Stop to use)
        pendingRecordingId = null
        pendingRecordingEvent = null
    }

    /**
     * Stop microphone recording and dispatch event with result
     */
    fun stopRecording(): String? {
        Log.d(TAG, "⏹️ stopRecording called")

        val path = microphoneRecorder?.stop()

        if (path != null) {
            val context = requireContext()

            // Retrieve stored id and event class from SharedPreferences
            val prefs = context.getSharedPreferences("microphone_recording", Context.MODE_PRIVATE)
            val id = prefs.getString("pending_id", null)
            val eventClass = prefs.getString("pending_event", null)
                ?: "NativePHP\\Microphone\\Events\\MicrophoneRecorded"

            Log.d(TAG, "📤 Dispatching $eventClass with path=$path, id=$id")

            // Clean up stored values
            prefs.edit()
                .remove("pending_id")
                .remove("pending_event")
                .apply()

            // Create payload JSON
            val payload = JSONObject().apply {
                put("path", path)
                put("mimeType", "audio/m4a")
                if (id != null) {
                    put("id", id)
                }
            }

            // Dispatch event (must be on main thread)
            Handler(Looper.getMainLooper()).post {
                dispatch(eventClass, payload.toString())
            }
        }

        return path
    }

    /**
     * Pause microphone recording
     */
    fun pauseRecording() {
        Log.d(TAG, "⏸️ pauseRecording called")
        microphoneRecorder?.pause()
    }

    /**
     * Resume microphone recording
     */
    fun resumeRecording() {
        Log.d(TAG, "▶️ resumeRecording called")
        microphoneRecorder?.resume()
    }

    /**
     * Get current recording status
     */
    fun getStatus(): String {
        return microphoneRecorder?.getStatus() ?: "idle"
    }

    /**
     * Get path to last recording
     */
    fun getLastRecording(): String? {
        return microphoneRecorder?.getLastRecording()
    }

    /**
     * Initialize the recorder without starting
     */
    fun ensureRecorderInitialized(context: Context) {
        if (MicrophoneFunctions.microphoneRecorder == null) {
            MicrophoneFunctions.microphoneRecorder = MicrophoneRecorder(context, false)
        }
    }

    private fun dispatch(event: String, payloadJson: String) {
        Log.d(TAG, "📢 Dispatching event: $event")
        Log.d(TAG, "📦 Payload: $payloadJson")

        val eventForJs = event.replace("\\", "\\\\")
        val js = """
            (function () {
                const payload = $payloadJson;

                const detail = { event: "$eventForJs", payload };

                document.dispatchEvent(new CustomEvent("native-event", { detail }));

                if (window.Livewire && typeof window.Livewire.dispatch === 'function') {
                    window.Livewire.dispatch("native:$eventForJs", payload);
                }

                fetch('/_native/api/events', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: JSON.stringify({
                        event: "$eventForJs",
                        payload: payload
                    })
                }).then(response => response.json())
                  .then(data => {
                      console.log("Microphone Event Dispatch Success");
                  })
                  .catch(error => console.error("Microphone Event Dispatch Error:", error.message));
            })();
        """.trimIndent()

        (activity as? WebViewProvider)?.getWebView()?.evaluateJavascript(js, null)
    }

    companion object {
        private const val TAG = "MicrophoneCoordinator"

        fun install(activity: FragmentActivity): MicrophoneCoordinator =
            activity.supportFragmentManager.findFragmentByTag("MicrophoneCoordinator") as? MicrophoneCoordinator
                ?: MicrophoneCoordinator().also {
                    activity.supportFragmentManager.beginTransaction()
                        .add(it, "MicrophoneCoordinator")
                        .commitNow()
                }

        /**
         * Dispatch an event to PHP from anywhere in the app
         */
        fun dispatchEvent(activity: FragmentActivity, event: String, payloadJson: String) {
            Log.d(TAG, "📢 Static dispatch event: $event")
            val coordinator = install(activity)
            coordinator.dispatch(event, payloadJson)
        }
    }
}