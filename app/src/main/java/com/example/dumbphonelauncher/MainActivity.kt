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
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.dumbphonelauncher.adapter.AppsSelectionAdapter
import com.example.dumbphonelauncher.adapter.PinnedAppsAdapter
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import com.example.dumbphonelauncher.util.AppUtils
import com.example.dumbphonelauncher.util.PinnedAppsDragDropCallback
import com.example.dumbphonelauncher.view.WallpaperView

class MainActivity : BaseActivity() {
    
    private lateinit var pinnedAppsRecycler: RecyclerView
    private lateinit var pinnedAppsAdapter: PinnedAppsAdapter
    private lateinit var appButton: TextView
    private lateinit var rightAppButton: TextView
    private lateinit var notificationArea: View
    private lateinit var clockView: TextClock
    private lateinit var dateView: TextClock
    private lateinit var deleteIcon: ImageView
    private lateinit var wallpaperView: com.example.dumbphonelauncher.view.WallpaperView
    
    private val numberBuffer = StringBuilder()
    private val maxPinnedApps = 4
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
        
        pinnedAppsRecycler = findViewById(R.id.pinned_apps_recycler)
        appButton = findViewById(R.id.app_button)
        rightAppButton = findViewById(R.id.right_app_button)
        notificationArea = findViewById(R.id.notification_area)
        clockView = findViewById(R.id.clock)
        dateView = findViewById(R.id.date)
        deleteIcon = findViewById(R.id.delete_icon)
        wallpaperView = findViewById(R.id.wallpaper_view)
        
        setupPinnedApps()
        setupClickListeners()
        setupRightAppButton()
        
        // Request focus on pinned apps to start with
        pinnedAppsRecycler.post {
            pinnedAppsRecycler.requestFocus()
            
            // If pinned apps has children, focus on the first one
            if (pinnedAppsRecycler.childCount > 0) {
                pinnedAppsRecycler.getChildAt(0)?.requestFocus()
            }
        }
    }
    
    // Add this method to handle the case when user presses home while already on home screen
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        if (intent.categories?.contains(Intent.CATEGORY_HOME) == true) {
            // Override transition animation for home button press
            overridePendingTransition(R.anim.home_enter, R.anim.home_exit)
        }
        
        // Reset UI state if needed
        pinnedAppsRecycler.post {
            pinnedAppsRecycler.requestFocus()
            if (pinnedAppsRecycler.childCount > 0) {
                pinnedAppsRecycler.getChildAt(0)?.requestFocus()
            }
        }
    }
    
    private fun setupWallpaper() {
        // Find the wallpaper view
        wallpaperView = findViewById(R.id.wallpaper_view)
        // No additional setup needed, the custom WallpaperView handles everything
        // Just make sure the window background is transparent
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }
    
    private fun setupPinnedApps() {
        // Optimize recyclerView
        pinnedAppsRecycler.setHasFixedSize(true)
        (pinnedAppsRecycler.itemAnimator as? DefaultItemAnimator)?.supportsChangeAnimations = false
        
        // Get installed apps
        val installedApps = AppUtils.getInstalledApps(packageManager)
        
        // Get pinned items from preferences
        val pinnedItems = prefManager.getPinnedItems(installedApps)
        
        pinnedAppsAdapter = PinnedAppsAdapter(
            pinnedItems.toMutableList(), 
            maxPinnedApps,
            onAppClick = { appInfo ->
                startActivity(appInfo.launchIntent)
            },
            onFolderClick = { folder ->
                showFolderContents(folder)
            },
            onEmptySlotClick = { position ->
                showAppSelectionDialog { selectedApp ->
                    addPinnedApp(selectedApp, position)
                }
            },
            onItemLongClick = { view ->
                // Get the adapter position
                val position = pinnedAppsRecycler.getChildAdapterPosition(view)
                if (position != RecyclerView.NO_POSITION && position < pinnedItems.size) {
                    when (val item = pinnedItems[position]) {
                        is DrawerItem.AppItem -> showAppOptionsPopup(view, item.appInfo)
                        is DrawerItem.FolderItem -> {
                            // For folders, just show the drag hint
                            // TODO: Implement folder options if needed
                        }
                    }
                }
            },
            onRearrange = { items ->
                // Save the updated order and folders to preferences
                prefManager.savePinnedItems(items)
            }
        )
        
        pinnedAppsRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = pinnedAppsAdapter
            
            // Make sure the RecyclerView has focus to start
            isFocusable = true
            isFocusableInTouchMode = true
        }
        
        // Set up drag and drop
        setupDragAndDrop()
    }
    
    private fun setupDragAndDrop() {
        val callback = PinnedAppsDragDropCallback { fromPosition, toPosition ->
            // Handle item reordering
            pinnedAppsAdapter.moveItem(fromPosition, toPosition)
        }
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(pinnedAppsRecycler)
    }
    
    // Folder contents are handled by the base class
    
    // Preference management is now handled by PreferenceManager
    
    private fun addPinnedApp(app: AppInfo, position: Int) {
        // Get installed apps
        val installedApps = AppUtils.getInstalledApps(packageManager)
        
        // Get current items
        val currentItems = prefManager.getPinnedItems(installedApps).toMutableList()
        
        // Ensure we have enough slots
        while (currentItems.size <= position) {
            currentItems.add(DrawerItem.AppItem(app)) // Add placeholder that will be overwritten
        }
        
        // Update the position
        currentItems[position] = DrawerItem.AppItem(app)
        
        // Save to preferences
        prefManager.savePinnedItems(currentItems)
        
        // Refresh UI
        setupPinnedApps()
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
        
        notificationArea.setOnClickListener {
            openNotificationPanel()
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
        // Handle notification panel open on D-pad down when notification area has focus
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && notificationArea.hasFocus()) {
            openNotificationPanel()
            return true
        }
        
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
        
        // Refresh pinned apps in case they changed
        setupPinnedApps()
        
        // Clear number buffer
        numberBuffer.clear()
        
        // Cancel any pending dialer launch
        dialerRunnable?.let { dialerHandler.removeCallbacks(it) }
        
        // Request focus on pinned apps
        pinnedAppsRecycler.post {
            pinnedAppsRecycler.requestFocus()
            if (pinnedAppsRecycler.childCount > 0) {
                pinnedAppsRecycler.getChildAt(0)?.requestFocus()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Cancel any pending dialer launch
        dialerRunnable?.let { dialerHandler.removeCallbacks(it) }
    }
    
    // App options popup is now handled by AppOptionsPopupHelper
    
    override fun onAppHidden(packageName: String) {
        // Refresh pinned apps if an app is hidden
        setupPinnedApps()
    }
    
    companion object {
        const val REQUEST_PIN_APP = 100
    }
} 