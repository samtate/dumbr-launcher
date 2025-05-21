package com.example.dumbphonelauncher

import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.dumbphonelauncher.util.AppUtils
import com.example.dumbphonelauncher.view.WallpaperView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    
    private lateinit var appButton: TextView
    private lateinit var rightAppButton: TextView
    private lateinit var clockView: TextClock
    private lateinit var dateView: TextClock
    private lateinit var deleteIcon: ImageView
    private lateinit var wallpaperView: com.example.dumbphonelauncher.view.WallpaperView
    
    private val numberBuffer = StringBuilder()
    private val dialerHandler = Handler(Looper.getMainLooper())
    private var dialerRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        // Check if we're coming from a HOME intent
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            // Use the correct animation when returning home
            overridePendingTransition(R.anim.home_enter, R.anim.home_exit)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        setupWallpaper()
        
        appButton = findViewById(R.id.app_button)
        rightAppButton = findViewById(R.id.right_app_button)
        clockView = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        deleteIcon = findViewById(R.id.delete_icon)
        wallpaperView = findViewById(R.id.wallpaper_view)
        
        setupClickListeners()
        setupRightAppButton()

        // Preload app drawer icons in the background for instant drawer open
        lifecycleScope.launch(Dispatchers.IO) {
            com.example.dumbphonelauncher.util.AppUtils.getInstalledApps(packageManager)
        }
    }
    
    // Add this method to handle the case when user presses home while already on home screen
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            // Override transition animation for home button press
            overridePendingTransition(R.anim.home_enter, R.anim.home_exit)
        }
    }
    
    private fun setupWallpaper() {
        // Find the wallpaper view
        wallpaperView = findViewById(R.id.wallpaper_view)
        // No additional setup needed, the custom WallpaperView handles everything
        // Just make sure the window background is transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    private fun setupRightAppButton() {
        // Load the right app button configuration
        val rightAppPackage = prefManager.getRightAppPackage()
        val rightAppName = prefManager.getRightAppName()
        
        // Set the button text
        rightAppButton.text = rightAppName
        
        // Set click listener
        rightAppButton.setOnClickListener {
            val packageName = prefManager.getRightAppPackage()
            if (packageName.isEmpty()) {
                // Default to contacts app
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("content://contacts/people")
                }
                startActivity(intent)
            } else {
                // Launch configured app
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                }
            }
        }
        
        // Set long click listener to change app
        rightAppButton.setOnLongClickListener {
            showAppSelectionDialog { selectedApp ->
                // Update right app button
                prefManager.setRightApp(selectedApp.packageName, selectedApp.appName)
                rightAppButton.text = selectedApp.appName
            }
            true
        }
    }
    
    private fun setupClickListeners() {
        appButton.setOnClickListener {
            openAppDrawerByTouch()
        }
        
        // Set up clock click to open Clock app
        clockView.setOnClickListener {
            openClockApp()
        }
        
        // Set up date click to open Calendar app
        dateView.setOnClickListener {
            openCalendarApp()
        }
    }
    
    /**
     * Open the app drawer with touch mode flag set
     */
    private fun openAppDrawerByTouch() {
        try {
            // Start fade out animation
            overridePendingTransition(0, android.R.anim.fade_out)
            
            val intent = Intent(this, AppDrawerActivity::class.java)
            intent.putExtra("opened_by_touch", true)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Simple fallback
            openAppDrawer()
        }
    }
    
    /**
     * Open the app drawer with D-pad mode (non-touch)
     * Use this instead of directly calling the base class method
     */
    private fun openAppDrawerWithDPad() {
        try {
            // Start fade out animation
            overridePendingTransition(0, android.R.anim.fade_out)
            
            val intent = Intent(this, AppDrawerActivity::class.java)
            intent.putExtra("opened_by_touch", false)
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Simple fallback
            super.openAppDrawer()
        }
    }
    
    // Using the openClockApp method from BaseActivity
    
    // Using the openCalendarApp method from BaseActivity
    
    // Using the openNotificationPanel method from BaseActivity
    
    // Using the showAppSelectionDialog method from BaseActivity
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle ENDCALL key on the home screen to turn off the screen
        if (keyCode == KeyEvent.KEYCODE_ENDCALL) {
            // We're on the home screen, so turn off the screen
            val devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            try {
                // Try to use DevicePolicyManager first if we have permission
                devicePolicyManager.lockNow()
            } catch (e: Exception) {
                // If that fails, try setting screen timeout to the minimum
                try {
                    val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
                    val wakeLock = powerManager.newWakeLock(android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "DumbPhoneLauncher:screenOff")
                    
                    // Acquire then immediately release the wake lock
                    wakeLock.acquire(1) // Acquire for 1ms
                    wakeLock.release()
                } catch (e: Exception) {
                    // If all else fails, just go to home (redundant but harmless)
                    val homeIntent = Intent(Intent.ACTION_MAIN)
                    homeIntent.addCategory(Intent.CATEGORY_HOME)
                    startActivity(homeIntent)
                }
            }
            return true
        }
        
        // Handle T9 keypad key for app drawer (usually KEYCODE_MENU or KEYCODE_STAR)
        when (keyCode) {
            KeyEvent.KEYCODE_MENU, // Standard menu key
            KeyEvent.KEYCODE_STAR, // * key, common for app drawer on feature phones
            KeyEvent.KEYCODE_T,    // T key, might be mapped on some devices
            KeyEvent.KEYCODE_TAB,  // Tab key, might be mapped on some devices
            KeyEvent.KEYCODE_BOOKMARK, // Bookmark key, might be mapped on some phones
            KeyEvent.KEYCODE_META_LEFT -> { // Meta key, sometimes used
                openAppDrawerWithDPad()
                return true
            }
            
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_POUND -> { // # key often mapped to right T9 key
                // Launch right app
                rightAppButton.performClick()
                return true
            }
        }
        
        // Handle numeric key presses for dialer
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            val digit = (keyCode - KeyEvent.KEYCODE_0).toString()
            
            // Cancel any pending dialer launch
            dialerRunnable?.let { dialerHandler.removeCallbacks(it) }
            
            // Append the new digit
            numberBuffer.append(digit)
            
            // Schedule dialer launch
            dialerRunnable = Runnable {
                openDialer()
            }
            
            // Launch dialer after a short delay (200ms)
            dialerHandler.postDelayed(dialerRunnable!!, 200)
            
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    private fun openDialer() {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$numberBuffer")
        startActivity(intent)
        numberBuffer.clear()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Use delayed fade-in animation when returning from app drawer
        overridePendingTransition(R.anim.home_fade_in_delayed, 0)
        
        // Clear number buffer
        numberBuffer.clear()
        
        // Cancel any pending dialer launch
        dialerRunnable?.let { dialerHandler.removeCallbacks(it) }
        
        // Request focus on app button
        appButton.requestFocus()
    }
    
    override fun onPause() {
        super.onPause()
        // Cancel any pending dialer launch
        dialerRunnable?.let { dialerHandler.removeCallbacks(it) }
    }
    
    // App options popup is now handled by AppOptionsPopupHelper
    
    override fun onAppHidden(packageName: String) {
        // No pinned apps logic, so nothing to refresh
    }
    
    companion object {
        const val REQUEST_PIN_APP = 100
    }
}