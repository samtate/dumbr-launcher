package com.example.dumbphonelauncher.adapter

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.R
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem

/**
 * Adapter for app drawer grid with cursor navigation focus
 */
class AppDrawerGridAdapter(
    private var items: List<DrawerItem>,
    private val columnCount: Int,
    private val onAppClick: (AppInfo) -> Unit,
    private val onFolderClick: (DrawerItem.FolderItem) -> Unit,
    private val onItemLongPress: (View, DrawerItem, Int) -> Unit
) : RecyclerView.Adapter<AppDrawerGridAdapter.ViewHolder>() {

    var currentFocusPosition = 0
    
    // Abstract class for different types of ViewHolders
    abstract class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(item: DrawerItem, position: Int)
    }
    
    // ViewHolder for app items
    inner class AppViewHolder(view: View) : ViewHolder(view) {
        private val container: ConstraintLayout = view.findViewById(R.id.container)
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val label: TextView = view.findViewById(R.id.app_label)

        // Drag state
        var isDragging = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        var touchOffsetX = 0f
        var touchOffsetY = 0f

        override fun bind(item: DrawerItem, position: Int) {
            if (item is DrawerItem.AppItem) {
                icon.setImageDrawable(item.appInfo.icon)
                label.text = item.appInfo.appName

                container.setOnClickListener {
                    container.requestFocus()
                    currentFocusPosition = position
                    onAppClick(item.appInfo)
                }

                container.setOnLongClickListener { v ->
                    isDragging = true
                    (container.context as? com.example.dumbphonelauncher.AppDrawerActivity)?.setAppIconDragging(true)

                    // Record the original position of the container
                    val loc = IntArray(2)
                    container.getLocationOnScreen(loc)
                    dragStartX = container.x
                    dragStartY = container.y
                    // We'll get the touch offset on the first ACTION_MOVE
                    touchOffsetX = 0f
                    touchOffsetY = 0f

                    // Visual feedback
                    container.alpha = 0.7f
                    container.scaleX = 1.15f
                    container.scaleY = 1.15f
                    container.elevation = 16f
                    true
                }

                container.setOnTouchListener { v, event ->
                    if (!isDragging) return@setOnTouchListener false
                    when (event.action) {
                        android.view.MotionEvent.ACTION_MOVE -> {
                            // On first move, calculate offset from touch to container top-left
                            if (touchOffsetX == 0f && touchOffsetY == 0f) {
                                val loc = IntArray(2)
                                container.getLocationOnScreen(loc)
                                touchOffsetX = event.rawX - loc[0]
                                touchOffsetY = event.rawY - loc[1]
                            }
                            // Move the container so its top-left follows the finger (minus offset)
                            val parent = container.parent as? View
                            if (parent != null) {
                                val parentLoc = IntArray(2)
                                parent.getLocationOnScreen(parentLoc)
                                val newX = event.rawX - parentLoc[0] - touchOffsetX
                                val newY = event.rawY - parentLoc[1] - touchOffsetY
                                container.translationX = newX - container.left
                                container.translationY = newY - container.top
                            }
                            return@setOnTouchListener true
                        }
                        android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                            isDragging = false
                            (container.context as? com.example.dumbphonelauncher.AppDrawerActivity)?.setAppIconDragging(false)
                            // Reset visuals and position
                            container.alpha = 1f
                            container.scaleX = 1f
                            container.scaleY = 1f
                            container.elevation = 0f
                            container.translationX = 0f
                            container.translationY = 0f
                            touchOffsetX = 0f
                            touchOffsetY = 0f
                            return@setOnTouchListener true
                        }
                    }
                    false
                }

                // Setup focus handling
                setupFocus(container, position)
            }
        }
    }
    
    // ViewHolder for folder items
    inner class FolderViewHolder(view: View) : ViewHolder(view) {
        private val container: ConstraintLayout = view.findViewById(R.id.container)
        private val icon: ImageView = view.findViewById(R.id.folder_icon)
        private val label: TextView = view.findViewById(R.id.folder_label)
        
        override fun bind(item: DrawerItem, position: Int) {
            if (item is DrawerItem.FolderItem) {
                // Set folder icon
                icon.setImageResource(R.drawable.ic_folder)
                
                // Set folder name and app count
                label.text = "${item.folder.name} (${item.folder.apps.size})"
                
                // Setup click listener - directly open folder on first touch
                container.setOnClickListener {
                    // First request focus for visual feedback
                    container.requestFocus()
                    currentFocusPosition = position
                    // Then open the folder
                    onFolderClick(item)
                }
                
                // Setup long press listener
                container.setOnLongClickListener {
                    onItemLongPress(it, item, position)
                    true
                }
                
                // Setup focus handling
                setupFocus(container, position)
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Use layout resource IDs directly for faster inflation
        val view = when (viewType) {
            VIEW_TYPE_APP -> {
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_app, parent, false).apply {
                        // Pre-setup common view properties for better performance
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }
            }
            VIEW_TYPE_FOLDER -> {
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_folder, parent, false).apply {
                        // Pre-setup common view properties for better performance
                        isFocusable = true
                        isFocusableInTouchMode = true
                    }
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
        
        return when (viewType) {
            VIEW_TYPE_APP -> AppViewHolder(view)
            VIEW_TYPE_FOLDER -> FolderViewHolder(view)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }
    
    override fun getItemCount(): Int = items.size
    
    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is DrawerItem.AppItem -> VIEW_TYPE_APP
            is DrawerItem.FolderItem -> VIEW_TYPE_FOLDER
        }
    }
    
    /**
     * Update the adapter with new items
     */
    fun updateItems(newItems: List<DrawerItem>) {
        val diffCallback = DrawerItemDiffCallback(items, newItems)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
    
    /**
     * Setup proper d-pad navigation focus
     */
    private fun setupFocus(view: View, position: Int) {
        view.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            
            // Clear any previous id (important for recycled views)
            id = View.NO_ID
            
            // Set a stable, unique ID for this view for focus navigation
            id = (position + 1).toInt()
            
            // Force focus on container and not child elements
            if (this is ViewGroup) {
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            
            // Set background based on focus state
            if (position == currentFocusPosition) {
                setBackgroundResource(R.drawable.bg_item_focused)
            } else {
                setBackgroundColor(Color.TRANSPARENT)
            }
            
            // Calculate neighbor positions for d-pad navigation
            val row = position / columnCount
            val col = position % columnCount
            
            // Calculate up neighbor
            val upPos = position - columnCount
            if (upPos >= 0) {
                nextFocusUpId = getItemId(upPos).toInt()
            }
            
            // Calculate down neighbor
            val downPos = position + columnCount
            if (downPos < items.size) {
                nextFocusDownId = getItemId(downPos).toInt()
            }
            
            // Calculate left neighbor
            if (col > 0) {
                nextFocusLeftId = getItemId(position - 1).toInt()
            }
            
            // Calculate right neighbor
            if (col < columnCount - 1 && position + 1 < items.size) {
                nextFocusRightId = getItemId(position + 1).toInt()
            }
            
            // Prevent focus from leaving grid at boundaries
            if (col == 0) {
                nextFocusLeftId = id  // Stay on same item when at left edge
            }
            if (col == columnCount - 1 || position + 1 >= items.size) {
                nextFocusRightId = id  // Stay on same item when at right edge
            }
            if (row == 0) {
                nextFocusUpId = id  // Stay on same item when at top edge
            }
            if (row == (items.size - 1) / columnCount || downPos >= items.size) {
                nextFocusDownId = id  // Stay on same item when at bottom edge
            }
            
            // Use a simpler focus change listener to reduce visual glitches
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    currentFocusPosition = position
                    setBackgroundResource(R.drawable.bg_item_focused)
                } else {
                    setBackgroundColor(Color.TRANSPARENT)
                }
            }
        }
    }
    
    /**
     * Get item ID used for focus navigation
     */
    override fun getItemId(position: Int): Long {
        return (position + 1).toLong()
    }
    
    /**
     * Calculate position on page from global position
     */
    fun getPositionOnPage(globalPosition: Int, itemsPerPage: Int, page: Int): Int {
        val startPosition = page * itemsPerPage
        return globalPosition - startPosition
    }
    
    /**
     * Get column index for a position
     */
    fun getColumnIndex(position: Int): Int {
        return position % columnCount
    }
    
    /**
     * Get row index for a position
     */
    fun getRowIndex(position: Int): Int {
        return position / columnCount
    }
    
    /**
     * Clear focus from all items
     */
    fun clearFocus() {
        val previousFocus = currentFocusPosition
        currentFocusPosition = RecyclerView.NO_POSITION
        
        // Explicitly clear focus from all visible items
        (0 until itemCount).forEach { position ->
            val holder = getViewHolderForPosition(position)
            holder?.itemView?.apply {
                setBackgroundColor(Color.TRANSPARENT)
                clearFocus()
            }
        }
        
        // If we couldn't directly update view holders, make sure to notify change for the previously focused item
        if (previousFocus != RecyclerView.NO_POSITION) {
            notifyItemChanged(previousFocus)
        }
    }
    
    /**
     * Get a view holder for a position if it's visible
     */
    private fun getViewHolderForPosition(position: Int): RecyclerView.ViewHolder? {
        // The adapter doesn't have direct access to the RecyclerView or its ViewHolders
        // This is a placeholder - the actual view holder access happens in AppDrawerActivity
        // when it uses recyclerView.findViewHolderForAdapterPosition()
        return null
    }
    
    companion object {
        const val VIEW_TYPE_APP = 1
        const val VIEW_TYPE_FOLDER = 2
    }
    
    /**
     * DiffUtil callback to efficiently update the adapter
     */
    private class DrawerItemDiffCallback(
        private val oldList: List<DrawerItem>,
        private val newList: List<DrawerItem>
    ) : DiffUtil.Callback() {
        
        override fun getOldListSize() = oldList.size
        
        override fun getNewListSize() = newList.size
        
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return when {
                oldItem is DrawerItem.AppItem && newItem is DrawerItem.AppItem ->
                    oldItem.appInfo.packageName == newItem.appInfo.packageName
                oldItem is DrawerItem.FolderItem && newItem is DrawerItem.FolderItem ->
                    oldItem.folder.name == newItem.folder.name
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            
            return when {
                oldItem is DrawerItem.AppItem && newItem is DrawerItem.AppItem ->
                    oldItem.appInfo.appName == newItem.appInfo.appName
                oldItem is DrawerItem.FolderItem && newItem is DrawerItem.FolderItem -> {
                    val oldApps = oldItem.folder.apps.map { it.packageName }.toSet()
                    val newApps = newItem.folder.apps.map { it.packageName }.toSet()
                    oldItem.folder.name == newItem.folder.name && oldApps == newApps
                }
                else -> false
            }
        }
    }
    
    /**
     * Prepare the adapter for view recycling by setting stable IDs
     */
    init {
        // Enable stable IDs for better RecyclerView performance
        setHasStableIds(true)
        
        // Pre-calculate IDs for faster focus navigation
        for (i in 0 until 30) { // Reasonable upper limit for items per page
            getItemId(i)
        }
    }
}