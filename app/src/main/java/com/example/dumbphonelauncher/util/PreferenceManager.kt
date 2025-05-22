package com.example.dumbphonelauncher.util

import android.content.Context
import android.content.SharedPreferences
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import com.example.dumbphonelauncher.model.Folder
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages all shared preferences for the launcher
 */
class PreferenceManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "launcher_prefs"
        private const val KEY_PINNED_ITEMS = "pinned_items"
        private const val KEY_DRAWER_ITEMS = "drawer_items"
        private const val KEY_HIDDEN_APPS = "hidden_apps"
        private const val KEY_RIGHT_APP_PACKAGE = "right_app_package"
        private const val KEY_RIGHT_APP_NAME = "right_app_name"
        
        // JSON keys
        private const val JSON_TYPE = "type"
        private const val JSON_PACKAGE = "package"
        private const val JSON_APP_TYPE = "app"
        private const val JSON_FOLDER_TYPE = "folder"
        private const val JSON_NAME = "name"
        private const val JSON_APPS = "apps"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Right App Button preferences
    fun setRightApp(packageName: String, appName: String) {
        prefs.edit()
            .putString(KEY_RIGHT_APP_PACKAGE, packageName)
            .putString(KEY_RIGHT_APP_NAME, appName)
            .apply()
    }

    fun getRightAppPackage(): String = prefs.getString(KEY_RIGHT_APP_PACKAGE, "") ?: ""

    fun getRightAppName(): String = prefs.getString(KEY_RIGHT_APP_NAME, "Contacts") ?: "Contacts"

    // Hidden apps preferences
    fun getHiddenApps(): Set<String> = prefs.getStringSet(KEY_HIDDEN_APPS, setOf()) ?: setOf()

    fun hideApp(packageName: String) {
        val hiddenApps = getHiddenApps().toMutableSet()
        hiddenApps.add(packageName)
        saveHiddenApps(hiddenApps)
    }

    fun unhideApp(packageName: String) {
        val hiddenApps = getHiddenApps().toMutableSet()
        hiddenApps.remove(packageName)
        saveHiddenApps(hiddenApps)
    }

    fun saveHiddenApps(hiddenApps: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_HIDDEN_APPS, hiddenApps)
            .apply()
    }

    // Pinned items preferences using JSON
    fun savePinnedItems(items: List<DrawerItem>) {
        saveItemsToJson(items, KEY_PINNED_ITEMS)
    }

    fun getPinnedItems(installedApps: List<AppInfo>): List<DrawerItem> {
        return loadItemsFromJson(KEY_PINNED_ITEMS, installedApps)
    }
    
    // App drawer items preferences using JSON
    fun saveDrawerItems(items: List<DrawerItem>) {
        saveItemsToJson(items, KEY_DRAWER_ITEMS)
    }

    fun getDrawerItems(installedApps: List<AppInfo>): List<DrawerItem> {
        return loadItemsFromJson(KEY_DRAWER_ITEMS, installedApps)
    }
    
    // Helper method to save items using JSON
    private fun saveItemsToJson(items: List<DrawerItem>, key: String) {
        try {
            val jsonArray = JSONArray()
            
            for (item in items) {
                val jsonObject = JSONObject()
                
                when (item) {
                    is DrawerItem.AppItem -> {
                        jsonObject.put(JSON_TYPE, JSON_APP_TYPE)
                        jsonObject.put(JSON_PACKAGE, item.appInfo.packageName)
                    }
                    is DrawerItem.FolderItem -> {
                        jsonObject.put(JSON_TYPE, JSON_FOLDER_TYPE)
                        jsonObject.put(JSON_NAME, item.folder.name)
                        
                        // Save apps in folder
                        val appsArray = JSONArray()
                        for (app in item.folder.apps) {
                            appsArray.put(app.packageName)
                        }
                        jsonObject.put(JSON_APPS, appsArray)
                    }
                }
                
                jsonArray.put(jsonObject)
            }
            
            // Save JSON string to preferences
            prefs.edit()
                .putString(key, jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            // Log error or handle exception
        }
    }
    
    // Helper method to load items using JSON
    private fun loadItemsFromJson(key: String, installedApps: List<AppInfo>): List<DrawerItem> {
        val jsonString = prefs.getString(key, "")
        val items = mutableListOf<DrawerItem>()
        
        if (jsonString.isNullOrEmpty()) {
            return emptyList()
        }
        
        try {
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                val type = jsonObject.getString(JSON_TYPE)
                
                when (type) {
                    JSON_APP_TYPE -> {
                        val packageName = jsonObject.getString(JSON_PACKAGE)
                        val app = installedApps.find { it.packageName == packageName }
                        if (app != null) {
                            items.add(DrawerItem.AppItem(app))
                        }
                    }
                    JSON_FOLDER_TYPE -> {
                        val folderName = jsonObject.getString(JSON_NAME)
                        val appsArray = jsonObject.getJSONArray(JSON_APPS)
                        val folderApps = mutableListOf<AppInfo>()
                        
                        for (j in 0 until appsArray.length()) {
                            val packageName = appsArray.getString(j)
                            val app = installedApps.find { it.packageName == packageName }
                            if (app != null) {
                                folderApps.add(app)
                            }
                        }
                        
                        if (folderApps.isNotEmpty()) {
                            items.add(DrawerItem.FolderItem(Folder(folderName, folderApps)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return empty list
            return emptyList()
        }
        
        return items
    }
}