package com.example.dumbphonelauncher.model

data class Folder(
    val name: String,
    val apps: MutableList<AppInfo> = mutableListOf()
) 