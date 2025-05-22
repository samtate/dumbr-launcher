package com.example.dumbphonelauncher.util

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import com.example.dumbphonelauncher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility class for app-related operations.
 */
object AppUtils {
    private const val TAG = "AppUtils"
    
    // Cache of installed apps to avoid repeated loading
    private var cachedApps: List<AppInfo>? = null
    private var lastLoadTime: Long = 0
    private const val CACHE_TTL = 60000 // 60 seconds cache TTL
    
    // Package icon cache to reduce drawable loading overhead
    private val iconCache = ConcurrentHashMap<String, WeakReference<android.graphics.drawable.Drawable>>()
    
    /**
     * Get all installed apps on the device.
     * This is a synchronous call and should be performed on a background thread.
     */
    fun getInstalledApps(packageManager: PackageManager): List<AppInfo> {
        // Check if we have a recent cache
        val currentTime = System.currentTimeMillis()
        cachedApps?.let {
            if (currentTime - lastLoadTime < CACHE_TTL) {
                return it
            }
        }
        
        return loadInstalledApps(packageManager)
    }
    
    /**
     * Asynchronously get installed apps
     */
    suspend fun getInstalledAppsAsync(packageManager: PackageManager): List<AppInfo> {
        return withContext(Dispatchers.IO) {
            getInstalledApps(packageManager)
        }
    }
    
    /**
     * Load installed apps from package manager
     */
    private fun loadInstalledApps(packageManager: PackageManager): List<AppInfo> {
        try {
            // Create intent for getting all apps with launcher intent
            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            
            // Query package manager for all apps with launcher intent
            val resolveInfoList: List<ResolveInfo> = packageManager.queryIntentActivities(intent, 0)
            
            // Get our own package name to filter it out
            val ourPackageName = "com.example.dumbphonelauncher"
            
            // Convert to list of AppInfo objects
            val appList = resolveInfoList
                .filter { it.activityInfo.packageName != ourPackageName } // Filter out our own launcher
                .map { resolveInfo ->
                    val appLabel = resolveInfo.loadLabel(packageManager).toString()
                    val packageName = resolveInfo.activityInfo.packageName
                    
                    // Check if icon is in cache, if not load and cache it
                    val icon = iconCache[packageName]?.get() ?: run {
                        val loadedIcon = resolveInfo.loadIcon(packageManager)
                        // Cache the icon
                        iconCache[packageName] = WeakReference(loadedIcon)
                        loadedIcon
                    }
                    
                    // Create launch intent
                    val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: Intent()
                    
                    AppInfo(appLabel, packageName, icon, launchIntent)
                }.sortedBy { it.appName.lowercase() } // Sort by app name
            
            // Update cache
            cachedApps = appList
            lastLoadTime = System.currentTimeMillis()
            
            return appList
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading installed apps", e)
            // Return cached apps if available, otherwise empty list
            return cachedApps ?: emptyList()
        }
    }
    
    /**
     * Clear the app cache to force a reload on next query.
     */
    fun clearCache() {
        cachedApps = null
        iconCache.clear()
    }
    
    /**
     * Get a cached app info by package name, or null if not found
     */
    fun getCachedAppInfo(packageName: String): AppInfo? {
        return cachedApps?.find { it.packageName == packageName }
    }
    
    /**
     * Check if app cache is initialized
     */
    fun isCacheInitialized(): Boolean {
        return cachedApps != null
    }
}