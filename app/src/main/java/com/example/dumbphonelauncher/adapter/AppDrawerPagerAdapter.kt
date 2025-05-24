package com.example.dumbphonelauncher.adapter

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.R
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import kotlin.math.min

/**
 * Adapter for ViewPager2 with grid layouts for app drawer pages
 */
class AppDrawerPagerAdapter(
    private val context: Context,
    private val allItems: List<DrawerItem>,
    private val columnCount: Int,
    private val rowCount: Int,
    private val onAppClick: (AppInfo) -> Unit,
    private val onFolderClick: (DrawerItem.FolderItem) -> Unit,
    private val onItemLongPress: (View, DrawerItem, Int) -> Unit,
    private val onPageSelected: (Int) -> Unit,
    private val onItemFocused: (Int, Int) -> Unit // position, column
) : RecyclerView.Adapter<AppDrawerPagerAdapter.PageViewHolder>() {

    // Items per page based on grid dimensions
    private val itemsPerPage = columnCount * rowCount
    
    // Track recycler views by page position
    private val recyclerViews = mutableMapOf<Int, RecyclerView>()
    
    // Track currently focused page and position
    var currentFocusPage = 0
        private set
    private var currentFocusPosition = 0
    
    inner class PageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val recyclerView: RecyclerView = view.findViewById(R.id.grid_recycler_view)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.page_app_drawer_grid, parent, false)
        return PageViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Calculate start and end indices for this page
        val startIndex = position * itemsPerPage
        val endIndex = min(startIndex + itemsPerPage, allItems.size)
        
        // Get items for this page
        val pageItems = if (startIndex < allItems.size) {
            allItems.subList(startIndex, endIndex)
        } else {
            listOf()
        }
        
        // Configure recycler view with performance optimizations
        holder.recyclerView.apply {
            // Set fixed size for better performance if layout size doesn't change
            setHasFixedSize(true)
            
            // Suppress layout animations during initial setup
            suppressLayout(true)
            
            // Disable item change animations for better performance
            itemAnimator = null
            
            // Use pre-initialized layout manager to avoid performance hit during transitions
            if (layoutManager == null) {
                layoutManager = GridLayoutManager(context, columnCount)
            }
            
            // Create adapter for this page
            val adapter = AppDrawerGridAdapter(
                pageItems,
                columnCount,
                onAppClick = onAppClick,
                onFolderClick = onFolderClick,
                onItemLongPress = { view, item, positionOnPage -> 
                    // Convert to global position
                    val globalPosition = startIndex + positionOnPage
                    onItemLongPress(view, item, globalPosition)
                }
            )
            
            // If this is the current focus page, restore focus position
            if (position == currentFocusPage) {
                adapter.currentFocusPosition = currentFocusPosition % itemsPerPage
            }
            
            this.adapter = adapter
            
            // Re-enable layouts after setup
            suppressLayout(false)
            
            // Setup key handling for page transitions
            setupKeyHandling(this, position)
            
            // Store recycler view reference
            recyclerViews[position] = this
        }
        
        // Block RecyclerView touch events during app icon drag
        holder.recyclerView.setOnTouchListener { _, event ->
            val activity = holder.recyclerView.context as? com.example.dumbphonelauncher.AppDrawerActivity
            if (activity?.isDraggingAppIcon == true) {
                // Block all touch events while dragging
                true
            } else {
                // Let RecyclerView handle events normally
                false
            }
        }
    }
    
    override fun getItemCount(): Int {
        return if (allItems.isEmpty()) {
            0
        } else {
            (allItems.size + itemsPerPage - 1) / itemsPerPage
        }
    }
    
    /**
     * Request focus on a specific column in the specified page
     */
    fun focusOnColumn(pagePosition: Int, column: Int) {
        val recyclerView = recyclerViews[pagePosition] ?: return
        val gridAdapter = recyclerView.adapter as? AppDrawerGridAdapter ?: return
        
        // Get items on this page
        val itemsOnPage = getItemCountForPage(pagePosition)
        
        // Try to maintain the same row when moving between pages
        val currentRow = if (gridAdapter.currentFocusPosition > 0) {
            gridAdapter.currentFocusPosition / columnCount
        } else {
            getCurrentFocusRow()
        }
        
        // Calculate target position on this page
        var targetPosition = currentRow * columnCount + column
        
        // Adjust if beyond page bounds
        if (targetPosition >= itemsOnPage) {
            // Find the last valid position in this column
            val lastRow = (itemsOnPage - 1) / columnCount
            targetPosition = min(lastRow * columnCount + column, itemsOnPage - 1)
        }
        
        // Update focus position
        gridAdapter.currentFocusPosition = targetPosition
        currentFocusPosition = targetPosition
        currentFocusPage = pagePosition
        
        // Request focus with a delay to ensure UI is ready
        recyclerView.post {
            // Ensure the item is visible by scrolling if needed
            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(
                targetPosition, 0
            )
            
            // Find the view holder and request focus
            recyclerView.postDelayed({
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(targetPosition)
                viewHolder?.itemView?.requestFocus()
                
                // Notify that the item is focused
                onItemFocused(targetPosition, column)
            }, 100)
        }
    }
    
    /**
     * Setup key handling for page transitions
     */
    private fun setupKeyHandling(recyclerView: RecyclerView, pagePosition: Int) {
        recyclerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                val adapter = recyclerView.adapter as? AppDrawerGridAdapter ?: return@setOnKeyListener false
                
                // Local position on this page
                val localPosition = adapter.currentFocusPosition
                
                // Calculate column
                val col = localPosition % columnCount
                
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (col == 0 && pagePosition > 0) {
                            // At leftmost column, move to previous page
                            onPageSelected(pagePosition - 1)
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (col == columnCount - 1 && pagePosition < getItemCount() - 1) {
                            // At rightmost column, move to next page
                            onPageSelected(pagePosition + 1)
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // Track focus changes after key navigation
                        recyclerView.postDelayed({
                            val focusedView = recyclerView.findFocus()
                            val viewHolder = focusedView?.let { recyclerView.findContainingViewHolder(it) }
                            if (viewHolder != null && viewHolder.bindingAdapterPosition != RecyclerView.NO_POSITION) {
                                // Update local focus tracking
                                adapter.currentFocusPosition = viewHolder.bindingAdapterPosition
                                currentFocusPosition = viewHolder.bindingAdapterPosition
                                
                                // Notify about focus change
                                val newCol = viewHolder.bindingAdapterPosition % columnCount
                                onItemFocused(viewHolder.bindingAdapterPosition, newCol)
                            }
                        }, 50)
                    }
                }
            }
            false
        }
    }
    
    /**
     * Get the RecyclerView for a specific page
     */
    fun getRecyclerViewAt(position: Int): RecyclerView? {
        return recyclerViews[position]
    }
    
    /**
     * Get the number of items on a specific page
     */
    fun getItemCountForPage(pagePosition: Int): Int {
        val startIndex = pagePosition * itemsPerPage
        return min(itemsPerPage, allItems.size - startIndex).coerceAtLeast(0)
    }
    
    /**
     * Get the current focus row
     */
    fun getCurrentFocusRow(): Int {
        val adapter = recyclerViews[currentFocusPage]?.adapter as? AppDrawerGridAdapter
        return adapter?.currentFocusPosition?.div(columnCount) ?: 0
    }
    
    /**
     * Get the current focus column
     */
    fun getCurrentFocusColumn(): Int {
        val adapter = recyclerViews[currentFocusPage]?.adapter as? AppDrawerGridAdapter
        return adapter?.currentFocusPosition?.rem(columnCount) ?: 0
    }
    
    /**
     * Focus on a specific position in a page
     */
    fun focusOnPosition(pagePosition: Int, position: Int) {
        val recyclerView = recyclerViews[pagePosition] ?: return
        val gridAdapter = recyclerView.adapter as? AppDrawerGridAdapter ?: return
        
        // Get total items on this page
        val itemsOnPage = getItemCountForPage(pagePosition)
        if (itemsOnPage == 0) return
        
        // Calculate the maximum valid row and column for this page
        val maxRow = (itemsOnPage - 1) / columnCount
        val lastRowItems = itemsOnPage - (maxRow * columnCount)
        val maxColInLastRow = if (lastRowItems > 0) lastRowItems - 1 else columnCount - 1
        
        // Extract requested row and column
        val requestedRow = position / columnCount
        val requestedCol = position % columnCount
        
        // Adjust row to be within valid range
        val validRow = requestedRow.coerceAtMost(maxRow).coerceAtLeast(0)
        
        // Adjust column if we're on the last row and it's partially filled
        val validCol = if (validRow == maxRow && lastRowItems > 0) 
            requestedCol.coerceAtMost(maxColInLastRow) 
        else 
            requestedCol
        
        // Calculate final valid position
        val validPosition = (validRow * columnCount) + validCol
        
        // Make sure position is within bounds (double-check)
        val finalPosition = validPosition.coerceIn(0, itemsOnPage - 1)
        
        // Update tracking
        gridAdapter.currentFocusPosition = finalPosition
        currentFocusPosition = finalPosition
        currentFocusPage = pagePosition
        
        // Request focus with a delay to ensure UI is ready
        recyclerView.post {
            // Ensure the item is visible by scrolling if needed
            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(
                finalPosition, 0
            )
            
            // Get the viewholder now to ensure recycling doesn't change it
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(finalPosition)
            
            // Find the view holder and request focus
            recyclerView.postDelayed({
                // Prevent other items from stealing focus during page transitions
                recyclerView.descendants.forEach { view ->
                    if (view != null && view != recyclerView && view != viewHolder?.itemView) {
                        view.isFocusable = false
                        if (view is ViewGroup) {
                            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                    }
                }
                
                // Now find our target item and make it focusable
                viewHolder?.itemView?.apply {
                    // Ensure this container blocks child views from getting focus
                    if (this is ViewGroup) {
                        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                    
                    isFocusable = true
                    isFocusableInTouchMode = true
                    
                    // Force focus on this item
                    clearFocus()
                    requestFocusFromTouch()
                }
                
                // Re-enable focus for all items after a delay
                recyclerView.postDelayed({
                    recyclerView.descendants.forEach { view ->
                        if (view != null && view != recyclerView) {
                            if (view is ViewGroup) {
                                // Keep blocking descendants to maintain focus on containers
                                view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            }
                            // But re-enable focusability for the containers
                            view.isFocusable = true
                            view.isFocusableInTouchMode = true
                        }
                    }
                }, 300)
                
                // Notify that the item is focused
                val col = finalPosition % columnCount
                onItemFocused(finalPosition, col)
            }, 100)
        }
    }
    
    /**
     * Ensure initial focus on the first item when the drawer opens
     */
    fun ensureInitialFocus() {
        if (itemCount > 0) {
            // Focus on the first item (top-left) of the first page
            val firstRecyclerView = recyclerViews[0]
            firstRecyclerView?.let { recyclerView ->
                val adapter = recyclerView.adapter as? AppDrawerGridAdapter
                adapter?.let {
                    // Set focus position to 0 (first item)
                    it.currentFocusPosition = 0
                    currentFocusPosition = 0
                    currentFocusPage = 0
                    
                    // Find and focus the first item
                    recyclerView.post {
                        // Ensure all other items aren't focusable yet
                        recyclerView.descendants.forEach { view ->
                            if (view != null && view != recyclerView) {
                                view.isFocusable = false
                                if (view is ViewGroup) {
                                    view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                }
                            }
                        }
                        
                        // Now find the first item and make it focusable
                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0)
                        viewHolder?.itemView?.apply {
                            // Ensure container blocks child views from getting focus
                            if (this is ViewGroup) {
                                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                            }
                            
                            // Make this item focusable
                            isFocusable = true
                            isFocusableInTouchMode = true
                            
                            // Force focus on this item
                            clearFocus()
                            requestFocusFromTouch()
                            setBackgroundResource(R.drawable.bg_item_focused)
                        }
                        
                        // Re-enable focus for all items after a delay
                        recyclerView.postDelayed({
                            recyclerView.descendants.forEach { view ->
                                if (view != null && view != recyclerView) {
                                    if (view is ViewGroup) {
                                        // Keep blocking descendants
                                        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                                    }
                                    // But re-enable the containers
                                    view.isFocusable = true
                                    view.isFocusableInTouchMode = true
                                }
                            }
                        }, 300)
                    }
                }
            }
        }
    }
    
    /**
     * Extension property to get all descendant views
     */
    private val ViewGroup.descendants: Sequence<View?>
        get() = sequence {
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                yield(child)
                if (child is ViewGroup) {
                    yieldAll(child.descendants)
                }
            }
        }
    
    /**
     * Focus on position using global absolute position for tracking
     */
    fun focusOnAbsolutePosition(pagePosition: Int, localPosition: Int, globalPosition: Int) {
        val recyclerView = recyclerViews[pagePosition] ?: return
        val gridAdapter = recyclerView.adapter as? AppDrawerGridAdapter ?: return
        
        // Make sure position is within bounds
        val itemsOnPage = getItemCountForPage(pagePosition)
        val validPosition = localPosition.coerceIn(0, itemsOnPage - 1)
        
        // Update tracking
        gridAdapter.currentFocusPosition = validPosition
        currentFocusPosition = validPosition
        currentFocusPage = pagePosition
        
        // Force all items to be unfocusable temporarily
        recyclerView.post {
            // Make all items unfocusable first
            recyclerView.descendants.forEach { view ->
                if (view != null && view != recyclerView) {
                    view.isFocusable = false
                    if (view is ViewGroup) {
                        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    }
                }
            }
            
            // Scroll to position if needed
            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(
                validPosition, 0
            )
            
            // Find the target view and make it focusable
            val viewHolder = recyclerView.findViewHolderForAdapterPosition(validPosition)
            viewHolder?.itemView?.apply {
                // Make this item focusable
                isFocusable = true
                isFocusableInTouchMode = true
                
                // Ensure container behavior
                if (this is ViewGroup) {
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                }
                
                // Clear any existing focus first
                val focusedView = recyclerView.findFocus()
                if (focusedView != null && focusedView != this) {
                    focusedView.clearFocus()
                }
                
                // Apply focus on target item
                post {
                    requestFocusFromTouch()
                    setBackgroundResource(R.drawable.bg_item_focused)
                }
            }
            
            // Re-enable other items after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                recyclerView.descendants.forEach { view ->
                    if (view != null && view != recyclerView) {
                        view.isFocusable = true
                        if (view is ViewGroup) {
                            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        }
                    }
                }
                
                // Re-apply focus to target if needed
                val viewHolder = recyclerView.findViewHolderForAdapterPosition(validPosition)
                viewHolder?.itemView?.requestFocusFromTouch()
            }, 200)
        }
    }
}