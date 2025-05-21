package com.example.dumbphonelauncher.model

sealed class DrawerItem {
    data class AppItem(val appInfo: AppInfo) : DrawerItem()
    data class FolderItem(val folder: Folder) : DrawerItem()
} 