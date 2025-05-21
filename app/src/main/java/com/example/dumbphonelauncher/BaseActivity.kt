package com.example.dumbphonelauncher

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.adapter.AppsSelectionAdapter
import com.example.dumbphonelauncher.adapter.FolderAppsAdapter
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.Folder
import com.example.dumbphonelauncher.util.AppOptionsPopupHelper
import com.example.dumbphonelauncher.util.AppUtils
import com.example.dumbphonelauncher.util.PreferenceManager

/**
 * Base activity with common launcher functionality
 */
abstract class BaseActivity : AppCompatActivity() {
    
    protected lateinit var prefManager: PreferenceManager
    protected lateinit var appOptionsHelper: AppOptionsPopupHelper
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        prefManager = PreferenceManager(this)
        appOptionsHelper = AppOptionsPopupHelper(
            this,
            prefManager, 
            onAppHidden = { packageName ->
                onAppHidden(packageName)
            }
        )
        
        // Warm up the rendering pipeline to reduce first frame lag
        warmUpRenderingPipeline()
    }
    
    /**
     * Show folder contents dialog
     */
    protected fun showFolderContents(folder: Folder) {
        val dialog = Dialog(this, R.style.Theme_DumbPhoneLauncher)
        dialog.setContentView(R.layout.dialog_folder_content)
        
        val folderTitle = dialog.findViewById<TextView>(R.id.folder_title)
        folderTitle.text = folder.name
        
        val recyclerView = dialog.findViewById<RecyclerView>(R.id.folder_apps_recycler)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.setHasFixedSize(true)
        
        val adapter = FolderAppsAdapter(folder.apps) { appInfo ->
            startActivity(appInfo.launchIntent)
            dialog.dismiss()
        }
        
        recyclerView.adapter = adapter
        
        // Configure close button
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_folder)
        closeButton.setOnClickListener {
            dialog.dismiss()
        }
        
        // Set focus on the first app in the folder when the dialog opens
        dialog.setOnShowListener {
            if (recyclerView.childCount > 0) {
                recyclerView.getChildAt(0)?.requestFocus()
            }
        }
        
        // Ensure folder dialog can be dismissed via back button
        dialog.setCancelable(true)
        dialog.show()
    }
    
    /**
     * Show app selection dialog
     */
    protected fun showAppSelectionDialog(onAppSelected: (AppInfo) -> Unit) {
        val dialog = AlertDialog.Builder(this, R.style.AppSelectionDialog)
            .setTitle(getString(R.string.select_app))
            .create()
        
        val allApps = AppUtils.getInstalledApps(packageManager)
        val selectionView = layoutInflater.inflate(R.layout.dialog_app_selection, null)
        val recyclerView = selectionView.findViewById<RecyclerView>(R.id.apps_selection_recycler)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = AppsSelectionAdapter(allApps) { appInfo ->
            onAppSelected(appInfo)
            dialog.dismiss()
        }
        recyclerView.adapter = adapter
        
        dialog.setView(selectionView)
        dialog.show()
    }
    
    /**
     * Show app options popup (Info/Hide/Uninstall)
     */
    protected fun showAppOptionsPopup(anchorView: View, appInfo: AppInfo) {
        appOptionsHelper.showAppOptionsPopup(anchorView, appInfo)
    }
    
    /**
     * Handle hidden app - to be overridden by subclasses
     */
    protected open fun onAppHidden(packageName: String) {
        // Override in subclasses
    }
    
    /**
     * Open the app drawer activity
     */
    protected fun openAppDrawer() {
        try {
            if (this !is AppDrawerActivity) {
                val intent = Intent(this, AppDrawerActivity::class.java)
                startActivity(intent)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Simple fallback
            try {
                val intent = Intent(this, AppDrawerActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }
    
    /**
     * Helper to open system calendar app
     */
    protected fun openCalendarApp() {
        try {
            // Try to open the default calendar app
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory("android.intent.category.APP_CALENDAR")
            }
            
            // If no calendar app found, try common calendar packages
            val calendarPackages = listOf(
                "com.android.calendar",
                "com.google.android.calendar",
                "com.samsung.android.calendar"
            )
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Try known calendar package names
                for (pkg in calendarPackages) {
                    try {
                        val pkgIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (pkgIntent != null) {
                            startActivity(pkgIntent)
                            return
                        }
                    } catch (e: Exception) {
                        // Continue to next package
                    }
                }
                
                // If all fails, fall back to date view in Settings
                try {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_DATE_SETTINGS)
                    startActivity(settingsIntent)
                } catch (e: Exception) {
                    // If that also fails, show app drawer
                    openAppDrawer()
                }
            }
        } catch (e: Exception) {
            // If all fails, show app drawer
            openAppDrawer()
        }
    }
    
    /**
     * Helper to open system clock app
     */
    protected fun openClockApp() {
        try {
            // Try to open the default clock app
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory("android.intent.category.APP_CLOCK")
            }
            
            // If no clock app found, try common clock packages
            val clockPackages = listOf(
                "com.android.deskclock",
                "com.google.android.deskclock",
                "com.sec.android.app.clockpackage"
            )
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // Try known clock package names
                for (pkg in clockPackages) {
                    try {
                        val pkgIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (pkgIntent != null) {
                            startActivity(pkgIntent)
                            return
                        }
                    } catch (e: Exception) {
                        // Continue to next package
                    }
                }
                
                // If all fails, show app drawer
                openAppDrawer()
            }
        } catch (e: Exception) {
            // If all fails, show app drawer
            openAppDrawer()
        }
    }
    
    /**
     * Open the notification panel
     */
    protected fun openNotificationPanel() {
        // Open notification panel by sending system UI visibility event
        try {
            val statusBarService = getSystemService("statusbar")
            val statusBarManager = Class.forName("android.app.StatusBarManager")
            val method = statusBarManager.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
        } catch (e: Exception) {
            // If the above doesn't work, try using reflection
            try {
                // For Android 10+
                val statusBarService = getSystemService("statusbar")
                val statusBarManager = statusBarService.javaClass
                val method = statusBarManager.getMethod("expandNotificationsPanel")
                method.invoke(statusBarService)
            } catch (e: Exception) {
                // If all fails, just open system settings as a fallback
                startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        }
    }
    
    /**
     * Warm up the rendering pipeline to reduce first frame lag
     * Call this from onCreate in activities that need smooth animations
     */
    protected fun warmUpRenderingPipeline() {
        // Create a small throwaway view and force a draw cycle to warm up rendering
        val view = View(this)
        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // Create a Canvas to warm up the rendering pipeline
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // Draw the view to warm up shader compilation
        view.layout(0, 0, 1, 1)
        view.draw(canvas)
        
        // Recycle bitmap since we don't need it anymore
        bitmap.recycle()
        
        // Also pre-create a BlurMaskFilter to warm up that pipeline
        val blurMaskFilter = android.graphics.BlurMaskFilter(25f, android.graphics.BlurMaskFilter.Blur.NORMAL)
        val paint = android.graphics.Paint()
        paint.maskFilter = blurMaskFilter
        canvas.drawRect(0f, 0f, 1f, 1f, paint)
    }
} 