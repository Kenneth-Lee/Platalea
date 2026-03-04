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

    private var initialFileUriState: MutableState<String?>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        intent?.data?.let { uri ->
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setContent {
            val state = remember { mutableStateOf<String?>(intent?.data?.toString()) }
            initialFileUriState = state
            LocalManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FileBrowserApp(initialFileUri = state)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialFileUriState?.value = intent.data?.toString()
    }
}
