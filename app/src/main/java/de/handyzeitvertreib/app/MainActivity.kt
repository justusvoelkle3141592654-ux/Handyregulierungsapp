package de.handyzeitvertreib.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import de.handyzeitvertreib.app.ui.HzvRoot
import de.handyzeitvertreib.app.ui.navigation.TopLevel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as HzvApplication).container
        val start = if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_LIMITS) TopLevel.LIMITS else TopLevel.TODAY
        setContent { HzvRoot(container, start) }
    }

    companion object {
        private const val EXTRA_OPEN = "open"
        private const val OPEN_LIMITS = "limits"

        fun limitsIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, OPEN_LIMITS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
