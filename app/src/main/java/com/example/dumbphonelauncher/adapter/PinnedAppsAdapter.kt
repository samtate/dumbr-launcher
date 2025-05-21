package com.example.dumbphonelauncher.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.R
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import com.example.dumbphonelauncher.model.Folder

class PinnedAppsAdapter(
    private val items: MutableList<DrawerItem>,
    private val maxItems: Int,
    private val onAppClick: (AppInfo) -> Unit,
    private val onFolderClick: (Folder) -> Unit,
    private val onEmptySlotClick: (Int) -> Unit,
    private val onItemLongClick: (View) -> Unit,
    private val onRearrange: (List<DrawerItem>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_APP = 0
        private const val VIEW_TYPE_FOLDER = 1
        private const val VIEW_TYPE_EMPTY = 2
        private const val TOUCH_SLOP = 20f // Minimum movement to be considered a drag
    }

    // Touch tracking variables
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var hasMoved = false

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
    }
    
    class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val folderName: TextView = view.findViewById(R.id.folder_name)
        val folderIcon1: ImageView = view.findViewById(R.id.folder_icon_1)
        val folderIcon2: ImageView = view.findViewById(R.id.folder_icon_2)
        val folderIcon3: ImageView = view.findViewById(R.id.folder_icon_3)
        val folderIcon4: ImageView = view.findViewById(R.id.folder_icon_4)
    }

    class EmptySlotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val plusIcon: ImageView = view.findViewById(R.id.plus_icon)
    }

    override fun getItemCount(): Int = maxItems

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) {
            when (items[position]) {
                is DrawerItem.AppItem -> VIEW_TYPE_APP
                is DrawerItem.FolderItem -> VIEW_TYPE_FOLDER
            }
        } else {
            VIEW_TYPE_EMPTY
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_APP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_pinned_app, parent, false)
                AppViewHolder(view)
            }
            VIEW_TYPE_FOLDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder, parent, false)
                FolderViewHolder(view)
            }
            VIEW_TYPE_EMPTY -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_empty_slot, parent, false)
                EmptySlotViewHolder(view)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is AppViewHolder -> {
                val item = items[position] as DrawerItem.AppItem
                val app = item.appInfo
                
                holder.appIcon.setImageDrawable(app.icon)
                holder.appName.text = app.appName
                
                // Track touch position to differentiate between long-press and drag
                hasMoved = false
                
                holder.itemView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Record starting position
                            touchStartX = event.rawX
                            touchStartY = event.rawY
                            hasMoved = false
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Check if we've moved enough to consider it a drag
                            val dx = Math.abs(event.rawX - touchStartX)
                            val dy = Math.abs(event.rawY - touchStartY)
                            if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                                hasMoved = true
                            }
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Only trigger click if the finger is still on the view
                            if (isPointInsideView(event.rawX, event.rawY, v)) {
                                onAppClick(app)
                                return@setOnTouchListener true
                            }
                            false
                        }
                        else -> false
                    }
                }
                
                holder.itemView.setOnClickListener {
                    onAppClick(app)
                }
                
                holder.itemView.setOnLongClickListener {
                    // Only trigger the long click popup if we haven't moved significantly
                    if (!hasMoved) {
                        onItemLongClick(it)
                    }
                    true
                }
                
                // Set focus for d-pad navigation
                holder.itemView.isFocusable = true
                holder.itemView.isFocusableInTouchMode = true
                
                // Set content description for accessibility
                holder.itemView.contentDescription = "Pinned app: ${app.appName}"
            }
            is FolderViewHolder -> {
                val item = items[position] as DrawerItem.FolderItem
                val folder = item.folder
                
                holder.folderName.text = folder.name
                
                // Set up to 4 app icons in the folder preview
                val apps = folder.apps
                if (apps.isNotEmpty()) {
                    holder.folderIcon1.setImageDrawable(apps[0].icon)
                    holder.folderIcon1.visibility = View.VISIBLE
                } else {
                    holder.folderIcon1.visibility = View.INVISIBLE
                }
                
                if (apps.size > 1) {
                    holder.folderIcon2.setImageDrawable(apps[1].icon)
                    holder.folderIcon2.visibility = View.VISIBLE
                } else {
                    holder.folderIcon2.visibility = View.INVISIBLE
                }
                
                if (apps.size > 2) {
                    holder.folderIcon3.setImageDrawable(apps[2].icon)
                    holder.folderIcon3.visibility = View.VISIBLE
                } else {
                    holder.folderIcon3.visibility = View.INVISIBLE
                }
                
                if (apps.size > 3) {
                    holder.folderIcon4.setImageDrawable(apps[3].icon)
                    holder.folderIcon4.visibility = View.VISIBLE
                } else {
                    holder.folderIcon4.visibility = View.INVISIBLE
                }
                
                // Track touch position to differentiate between long-press and drag
                hasMoved = false
                
                holder.itemView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Record starting position
                            touchStartX = event.rawX
                            touchStartY = event.rawY
                            hasMoved = false
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Check if we've moved enough to consider it a drag
                            val dx = Math.abs(event.rawX - touchStartX)
                            val dy = Math.abs(event.rawY - touchStartY)
                            if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                                hasMoved = true
                            }
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Only trigger click if the finger is still on the view
                            if (isPointInsideView(event.rawX, event.rawY, v)) {
                                onFolderClick(folder)
                                return@setOnTouchListener true
                            }
                            false
                        }
                        else -> false
                    }
                }
                
                holder.itemView.setOnClickListener {
                    onFolderClick(folder)
                }
                
                holder.itemView.setOnLongClickListener {
                    // Only trigger the long click popup if we haven't moved significantly
                    if (!hasMoved) {
                        onItemLongClick(it)
                    }
                    true
                }
                
                // Set focus for d-pad navigation
                holder.itemView.isFocusable = true
                holder.itemView.isFocusableInTouchMode = true
                
                // Set content description for accessibility
                holder.itemView.contentDescription = "Folder: ${folder.name} with ${folder.apps.size} apps"
            }
            is EmptySlotViewHolder -> {
                // Track touch position to differentiate between long-press and drag
                hasMoved = false
                
                holder.itemView.setOnTouchListener { v, event ->
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            // Record starting position
                            touchStartX = event.rawX
                            touchStartY = event.rawY
                            hasMoved = false
                            false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            // Check if we've moved enough to consider it a drag
                            val dx = Math.abs(event.rawX - touchStartX)
                            val dy = Math.abs(event.rawY - touchStartY)
                            if (dx > TOUCH_SLOP || dy > TOUCH_SLOP) {
                                hasMoved = true
                            }
                            false
                        }
                        MotionEvent.ACTION_UP -> {
                            // Only trigger click if the finger is still on the view
                            if (isPointInsideView(event.rawX, event.rawY, v)) {
                                onEmptySlotClick(position)
                                return@setOnTouchListener true
                            }
                            false
                        }
                        else -> false
                    }
                }
                
                holder.itemView.setOnClickListener {
                    onEmptySlotClick(position)
                }
                
                // Set focus for d-pad navigation
                holder.itemView.isFocusable = true
                holder.itemView.isFocusableInTouchMode = true
                
                // Set content description for accessibility
                holder.itemView.contentDescription = "Empty slot, tap to add app"
            }
        }
    }
    
    // Helper method to check if touch event is inside the view
    private fun isPointInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        return (rawX >= x && rawX <= x + view.width &&
                rawY >= y && rawY <= y + view.height)
    }
    
    // Method for handling drag and drop
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < items.size && toPosition < items.size) {
            val fromItem = items[fromPosition]
            items.removeAt(fromPosition)
            items.add(toPosition, fromItem)
            
            notifyItemMoved(fromPosition, toPosition)
            
            // Notify the parent about the rearrangement
            onRearrange(items)
        }
    }
    
    // Method to create a folder
    fun createFolder(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition >= items.size || toPosition >= items.size) {
            return false
        }
        
        val fromItem = items[fromPosition]
        val toItem = items[toPosition]
        
        // Creating a folder by dropping an app onto another app
        if (fromItem is DrawerItem.AppItem && toItem is DrawerItem.AppItem) {
            val folder = Folder(
                name = "Folder",
                apps = mutableListOf(fromItem.appInfo, toItem.appInfo)
            )
            
            // Replace the target item with the folder
            items[toPosition] = DrawerItem.FolderItem(folder)
            
            // Remove the source item
            if (fromPosition > toPosition) {
                items.removeAt(fromPosition)
                notifyItemRemoved(fromPosition)
            } else {
                items.removeAt(fromPosition)
                notifyItemRemoved(fromPosition)
            }
            
            // Update the folder item
            notifyItemChanged(toPosition)
            
            // Notify the parent about the rearrangement
            onRearrange(items)
            return true
        }
        
        // Adding to an existing folder
        else if (fromItem is DrawerItem.AppItem && toItem is DrawerItem.FolderItem) {
            toItem.folder.apps.add(fromItem.appInfo)
            
            // Remove the app item now in the folder
            items.removeAt(fromPosition)
            notifyItemRemoved(fromPosition)
            
            // Update the folder item
            notifyItemChanged(toPosition)
            
            // Notify the parent about the rearrangement
            onRearrange(items)
            return true
        }
        
        return false
    }
    
    // Method to remove an item
    fun removeItem(position: Int) {
        if (position < items.size) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size - position)
            
            // Notify the parent about the rearrangement
            onRearrange(items)
        }
    }
} 