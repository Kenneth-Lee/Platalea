package com.kenny.localmanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.kenny.localmanager.ui.FileBrowserApp
import com.kenny.localmanager.ui.theme.LocalManagerTheme

class MainActivity : ComponentActivity() {

    companion object {
        const val LAUNCH_TARGET_EXTRA = "launch_target"
    }

    private var initialFileUriState: MutableState<String?>? = null
    private var initialLaunchTargetState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.let { uri ->
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val launchTarget = intent?.getStringExtra(LAUNCH_TARGET_EXTRA)
        setContent {
            val state = remember { mutableStateOf<String?>(intent?.data?.toString()) }
            initialFileUriState = state
            val launchTargetState = remember { mutableStateOf(launchTarget) }
            initialLaunchTargetState = launchTargetState
            LocalManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileBrowserApp(initialFileUri = state, initialLaunchTarget = launchTargetState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialFileUriState?.value = intent.data?.toString()
        initialLaunchTargetState?.value = intent.getStringExtra(LAUNCH_TARGET_EXTRA)
    }
}
