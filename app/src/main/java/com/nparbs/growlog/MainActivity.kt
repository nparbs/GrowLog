package com.nparbs.growlog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.nparbs.growlog.ui.GrowLogRoot
import com.nparbs.growlog.ui.GrowLogTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch {
            (application as GrowLogApp).container.repository.rescheduleAll()
        }
        setContent {
            GrowLogTheme {
                GrowLogRoot()
            }
        }
    }
}
