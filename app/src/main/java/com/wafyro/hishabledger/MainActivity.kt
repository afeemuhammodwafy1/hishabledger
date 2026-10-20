package com.wafyro.hishabledger

import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    override fun onCreate(state: Bundle?) {
        installSplashScreen()
        super.onCreate(state)
        migrateLegacyStorage(this)
        // Load preferences before the first frame so the splash and first screen already use the chosen theme.
        AppThemePreference.load(this)
        AppLanguage.load(this)
        LockGate.init(this)
        val dark = when (AppThemePreference.mode) {
            "dark" -> true
            "light" -> false
            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        window.setBackgroundDrawable(ColorDrawable((if (dark) 0xFF101713 else 0xFFF7FAF8).toInt()))
        if (state == null) ShortcutRouter.handle(intent)
        setContent { HishabTheme { HishabApp() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        ShortcutRouter.handle(intent)
    }
}
