package com.example.dumbphonelauncher.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import com.example.dumbphonelauncher.R
import com.example.dumbphonelauncher.model.AppInfo

/**
 * Helper class for showing app options popup (Info/Hide/Uninstall)
 */
class AppOptionsPopupHelper(
    private val context: Context,
    private val prefManager: PreferenceManager,
    private val onAppHidden: (String) -> Unit,
    private val onUninstallRequested: (String) -> Unit // <-- add this
) {

    /**
     * Show app options popup
     */
    fun showAppOptionsPopup(anchorView: View, appInfo: AppInfo) {
        // Inflate the popup layout
        val popupView = LayoutInflater.from(context).inflate(R.layout.app_options_popup, null)
        
        // Create the popup window
        val popupWindow = PopupWindow(
            popupView,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Add elevation for shadow
        popupWindow.elevation = 10f
        
        // Set up button click listeners
        val btnInfo = popupView.findViewById<Button>(R.id.btn_app_info)
        val btnHide = popupView.findViewById<Button>(R.id.btn_hide_app)
        val btnUninstall = popupView.findViewById<Button>(R.id.btn_uninstall)
        // Hide uninstall button for system apps that cannot be uninstalled
        try {
            val pm = context.packageManager
            val appFlags = pm.getApplicationInfo(appInfo.packageName, 0).flags
            val isSystemApp = (appFlags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appFlags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystemApp && !isUpdatedSystemApp) {
                btnUninstall.visibility = View.GONE
            } else {
                btnUninstall.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            btnUninstall.visibility = View.VISIBLE // fallback: show if unsure
        }
        
        // Hide 'Hide App' button for system apps that cannot be hidden
        try {
            val pm = context.packageManager
            val appFlags = pm.getApplicationInfo(appInfo.packageName, 0).flags
            val isSystemApp = (appFlags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appFlags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystemApp && !isUpdatedSystemApp) {
                btnHide.visibility = View.GONE
            } else {
                btnHide.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            btnHide.visibility = View.VISIBLE // fallback: show if unsure
        }
        
        // Make buttons respond to clicks immediately without requiring focus first
        btnInfo.isFocusableInTouchMode = false
        btnHide.isFocusableInTouchMode = false
        btnUninstall.isFocusableInTouchMode = false
        
        // Set selected state for immediate visual feedback
        btnInfo.isSelected = true
        btnHide.isSelected = true
        btnUninstall.isSelected = true
        
        // App Info button - open system app details
        btnInfo.setOnClickListener { view ->
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:${appInfo.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open app info: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                popupWindow.dismiss()
            }
        }
        
        // Hide app button - add to hidden apps preference
        btnHide.setOnClickListener { view ->
            var hideSucceeded = false
            try {
                // Always attempt to hide, even if already hidden
                prefManager.hideApp(appInfo.packageName)
                hideSucceeded = true
            } catch (e: Exception) {
                // Log error for debugging
                android.util.Log.e("AppOptionsPopupHelper", "Error hiding app: ${e.message}", e)
            } finally {
                // Always call callback to update UI, even if hiding failed
                onAppHidden(appInfo.packageName)
                // Show toast only if succeeded, else show error
                if (hideSucceeded) {
                    Toast.makeText(context, "${appInfo.appName} hidden", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not hide app", Toast.LENGTH_SHORT).show()
                }
                popupWindow.dismiss()
            }
        }
        
        // Uninstall button - launch uninstall intent
        btnUninstall.setOnClickListener { view ->
            try {
                onUninstallRequested(appInfo.packageName)
            } catch (e: Exception) {
                Toast.makeText(context, "Could not uninstall app: e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                popupWindow.dismiss()
            }
        }
        
        // Configure popup view for instant interaction
        popupView.isFocusableInTouchMode = false
        popupView.isFocusable = true
        
        // Set the content view to be clickable but not focusable to prevent focus-first behavior
        popupWindow.isOutsideTouchable = true
        popupWindow.isTouchable = true
        
        // Show the popup window
        popupWindow.showAsDropDown(anchorView, 0, -anchorView.height * 2)
    }
}