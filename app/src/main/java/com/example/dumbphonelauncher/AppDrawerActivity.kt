package com.example.dumbphonelauncher

import android.graphics.Canvas
import android.graphics.Point
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.GridLayoutManager
import com.example.dumbphonelauncher.adapter.AppDrawerGridAdapter
import com.example.dumbphonelauncher.adapter.AppDrawerPagerAdapter
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import com.example.dumbphonelauncher.model.Folder
import com.example.dumbphonelauncher.util.AppDrawerDragHelper
import com.example.dumbphonelauncher.util.AppUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator

class AppDrawerActivity : BaseActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var pageIndicator: LinearLayout
    private lateinit var backgroundOverlay: View
    private lateinit var upArrow: ImageView
    private lateinit var pagerAdapter: AppDrawerPagerAdapter
    private lateinit var dragHelper: AppDrawerDragHelper

    private val drawerItems = mutableListOf<DrawerItem>()
    private val indicators = mutableListOf<View>()
    
    // Grid dimensions
    private var columnCount = 3
    private var rowCount = 4
    private var itemsPerPage = columnCount * rowCount
    
    // Global cursor position tracking
    private var globalCursorPosition = 0
    private var isTransitioning = false
    private var allowPositionUpdates = true
    
    // Track touch events for drag and drop
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    private var touchedItem: DrawerItem? = null
    private var touchedItemPosition = -1
    private var touchedView: View? = null
    private var isLongPress = false
    private lateinit var currentTouchEvent: MotionEvent
    
    // Track input mode (touch vs d-pad)
    private var isTouchMode = false
    private var wasOpenedByTouch = false
    private var lastTouchTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Capture if the app drawer was opened by touch
        wasOpenedByTouch = intent.getBooleanExtra("opened_by_touch", false)
        isTouchMode = wasOpenedByTouch
        
        // Make status bar fully transparent
        makeStatusBarTransparent()
        
        setContentView(R.layout.activity_app_drawer)
        
        // Start fade in animation
        overridePendingTransition(android.R.anim.fade_in, 0)
        
        // Warm up the rendering pipeline to reduce first frame lag
        warmUpRenderingPipeline()
        
        // Adjust layout for status bar if needed
        adjustForStatusBar()
        
        // Initialize views
        viewPager = findViewById(R.id.view_pager)
        pageIndicator = findViewById(R.id.page_indicator)
        backgroundOverlay = findViewById(R.id.background_overlay)
        upArrow = findViewById(R.id.up_arrow)
        
        // Determine optimal grid dimensions based on screen size
        calculateGridDimensions()
        
        // Initialize drag helper
        dragHelper = AppDrawerDragHelper(
            context = this,
            upArrowView = upArrow,
            onDragComplete = { fromPos, toPos ->
                handleItemReordering(fromPos, toPos)
            },
            onFolderCreated = { fromItem, toItem ->
                handleFolderCreation(fromItem, toItem)
            },
            onDragToHomeScreen = { item ->
                handleDragToHomeScreen(item)
            }
        )
        
        // Load apps asynchronously
        loadAppsAsync()
    }
    
    /**
     * Make status bar fully transparent
     */
    private fun makeStatusBarTransparent() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                // For Android 11+ (API 30+)
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.apply {
                    // Just make transparent, don't hide
                    setSystemBarsAppearance(
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                        android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    )
                }
            } else {
                // For Android 10 and below
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
            
            // Always set status bar color to transparent
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        } catch (e: Exception) {
            // Fallback if anything goes wrong - don't crash
            e.printStackTrace()
        }
    }
    
    /**
     * Adjust for status bar height at runtime
     */
    private fun adjustForStatusBar() {
        try {
            // Get status bar height
            val statusBarHeight = getStatusBarHeight()
            
            // Apply to header
            val headerContainer = findViewById<ViewGroup>(R.id.header_container)
            headerContainer?.setPadding(
                headerContainer.paddingLeft,
                statusBarHeight,
                headerContainer.paddingRight,
                headerContainer.paddingBottom
            )
        } catch (e: Exception) {
            // Fallback if anything goes wrong - don't crash
            e.printStackTrace()
        }
    }
    
    /**
     * Get the status bar height
     */
    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            resources.getDimensionPixelSize(R.dimen.status_bar_height)
        }
    }
    
    /**
     * Calculate optimal grid dimensions based on screen size
     */
    private fun calculateGridDimensions() {
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        
        val width = size.x
        val height = size.y
        
        // Determine columns based on width
        columnCount = if (width > 720) 4 else 3
        
        // Determine rows based on height
        rowCount = if (height > 1280) 5 else 4
        
        // Update items per page
        itemsPerPage = columnCount * rowCount
    }
    
    /**
     * Load apps asynchronously
     */
    private fun loadAppsAsync() {
        lifecycleScope.launch {
            val loadedItems = withContext(Dispatchers.IO) {
                // Load drawer items in background thread
                loadDrawerItems()
            }
            
            // Update UI on main thread
            drawerItems.clear()
            drawerItems.addAll(loadedItems)
            
            // Setup ViewPager
            setupViewPager()
            
            // Update page indicator
            updatePageIndicator()
        }
    }
    
    /**
     * Setup ViewPager with grid adapters
     */
    private fun setupViewPager() {
        pagerAdapter = AppDrawerPagerAdapter(
            context = this,
            allItems = drawerItems,
            columnCount = columnCount,
            rowCount = rowCount,
            onAppClick = { appInfo -> 
                startActivity(appInfo.launchIntent)
            },
            onFolderClick = { folderItem ->
                showFolderContents(folderItem.folder)
            },
            onItemLongPress = { view, item, position ->
                // Store info for potential drag operation
                touchedView = view
                touchedItem = item
                touchedItemPosition = position
                
                // If this is a genuine long press, show options popup
                // Actual drag/drop will be handled in touch event handling
                if (!dragHelper.isMovementSignificant()) {
                    isLongPress = true
                    if (item is DrawerItem.AppItem) {
                        showAppOptionsPopup(view, item.appInfo)
                    }
                }
            },
            onPageSelected = { pageIndex ->
                // Only handle page selection via adapter if we're not in a transition
                if (!isTransitioning) {
                    viewPager.setCurrentItem(pageIndex, true)
                    updatePageIndicator()
                }
            },
            onItemFocused = { position, page ->
                // Update global cursor position when item gets focus via adapter events
                if (!isTransitioning && allowPositionUpdates && !isTouchMode) {
                    val globalPos = getGlobalPosition(page, position)
                    if (globalPos != globalCursorPosition) {
                        globalCursorPosition = globalPos
                    }
                }
            }
        )
        
        // Configure ViewPager2
        viewPager.adapter = pagerAdapter
        
        // Fix overlapping pages issue by modifying internal RecyclerView
        try {
            val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val recyclerView = recyclerViewField.get(viewPager) as RecyclerView
            
            // Disable nested scrolling to prevent scroll interaction issues
            recyclerView.isNestedScrollingEnabled = false
            
            // Simple configuration to ensure the page fits correctly
            recyclerView.clipToPadding = false
            
            // Set minimal padding - just enough to ensure smooth drawing during transitions
            val smallPadding = resources.getDimensionPixelSize(R.dimen.viewpager_margin)
            recyclerView.setPadding(0, 0, 0, 0)
            
            // Make sure parent doesn't clip children for animations
            (viewPager.parent as? ViewGroup)?.clipChildren = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        // Explicitly set orientation to horizontal
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // Allow user swipes for page changes
        viewPager.isUserInputEnabled = true
        
        // Set offscreen page limit to 1 (keep just adjacent pages)
        viewPager.offscreenPageLimit = 1
        
        // Add a simple, fast and delightful page transition
        viewPager.setPageTransformer(createDelightfulPageTransformer())
        
        // Initialize page indicators
        setupPageIndicators()
        
        // Register page change callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            // Track the original global position when starting page changes
            private var initialGlobalPosition = 0
            private var fromPage = 0
            
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> {
                        // Save current position before any transition
                        fromPage = viewPager.currentItem
                        initialGlobalPosition = globalCursorPosition
                        
                        // User is likely dragging with touch
                        lastTouchTime = System.currentTimeMillis()
                        if (!isTouchMode) {
                            switchToTouchMode()
                        }
                        
                        // Block input during transitions
                        isTransitioning = true
                        allowPositionUpdates = false
                        
                        // Clear focus from all pages to prevent phantom cursor
                        hideCursor()
                    }
                    ViewPager2.SCROLL_STATE_SETTLING -> {
                        // Page is settling after touch or programmatic change
                        isTransitioning = true
                        allowPositionUpdates = false
                    }
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        updatePageIndicator()
                        
                        // Determine if this was a backward navigation
                        val currentPage = viewPager.currentItem
                        val wasBackNavigation = !isTouchMode && currentPage < fromPage
                        
                        // In touch mode, don't show cursor
                        if (!isTouchMode) {
                            if (wasBackNavigation) {
                                // Backward navigation: try to maintain equivalent position on new page
                                val oldRow = getLocalPositionFromGlobal(initialGlobalPosition) / columnCount
                                val oldCol = getLocalPositionFromGlobal(initialGlobalPosition) % columnCount
                                
                                // Calculate equivalent position on the new page
                                val newPageStart = currentPage * itemsPerPage
                                val targetPosition = newPageStart + (oldRow * columnCount) + oldCol
                                
                                // Verify the position is valid on the new page
                                if (targetPosition < drawerItems.size) {
                                    globalCursorPosition = targetPosition
                                } else {
                                    // Fall back to the last item on the page if position is invalid
                                    val lastValidPosition = minOf(newPageStart + itemsPerPage - 1, drawerItems.size - 1)
                                    globalCursorPosition = maxOf(newPageStart, lastValidPosition)
                                }
                            }
                            
                            // Update focus based on global position after transition
                            Handler(Looper.getMainLooper()).postDelayed({
                                updateFocusOnCurrentPage(true)
                                
                                // Re-enable input after focus is set
                                Handler(Looper.getMainLooper()).postDelayed({
                                    isTransitioning = false
                                    allowPositionUpdates = true
                                }, 100)
                            }, 100)
                        } else {
                            // In touch mode, make sure no cursor is visible
                            hideCursor()
                            
                            // Re-enable input after transition
                            Handler(Looper.getMainLooper()).postDelayed({
                                isTransitioning = false
                                allowPositionUpdates = true
                            }, 100)
                        }
                    }
                }
            }
            
            override fun onPageSelected(position: Int) {
                // Always clear all focus first when page changes
                hideCursor()
                
                updatePageIndicator()
                
                if (!isTouchMode) {
                    // Handle d-pad navigation with exact position information
                    dpadNavigationInfo?.let { navInfo ->
                        if (position == navInfo.targetPage) {
                            // This is definitely a d-pad navigation, use the exact target position
                            globalCursorPosition = navInfo.targetGlobalPosition
                            
                            // Clear the navigation info after use
                            dpadNavigationInfo = null
                            return@onPageSelected
                        }
                    }
                    
                    // Fallback to maintaining equivalent position for other cases
                    if (initialGlobalPosition >= 0) {
                        val oldLocalPosition = getLocalPositionFromGlobal(initialGlobalPosition)
                        val oldRow = oldLocalPosition / columnCount
                        val oldCol = oldLocalPosition % columnCount
                        
                        val newPageStart = position * itemsPerPage
                        val targetPosition = newPageStart + (oldRow * columnCount) + oldCol
                        
                        if (targetPosition < drawerItems.size) {
                            globalCursorPosition = targetPosition
                        }
                    }
                } else {
                    // In touch mode, ensure cursor is hidden
                    hideCursor()
                }
            }
            
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                // Update indicators during scroll for a smooth transition effect
                animatePageIndicators(position, positionOffset)
                
                // Always hide cursor during touch scrolling
                if (isTouchMode) {
                    hideCursor()
                }
            }
        })
        
        // Setup touch handling for drag operations
        setupTouchHandling()
        
        // Set initial focus (or hide it if in touch mode)
        lifecycleScope.launch {
            delay(150)
            
            // Set initial cursor position (even if not visible)
            globalCursorPosition = 0
            
            if (isTouchMode) {
                // If opened by touch, don't show cursor initially
                hideCursor()
            } else {
                // Set initial cursor position
                updateCursorPosition(0, false)
            }
        }
    }
    
    /**
     * Switch to touch mode (hide cursor)
     */
    private fun switchToTouchMode() {
        isTouchMode = true
        hideCursor()
    }
    
    /**
     * Switch to d-pad mode (show cursor)
     */
    private fun switchToDPadMode() {
        isTouchMode = false
        showCursorAtTopLeft()
    }
    
    /**
     * Show the cursor at the top-left position of the current page
     */
    private fun showCursorAtTopLeft() {
        if (isTransitioning || !this::pagerAdapter.isInitialized) return
        
        val currentPage = viewPager.currentItem
        // Calculate the global position of the first item on the current page
        val topLeftPosition = currentPage * itemsPerPage
        
        // Only proceed if we have items
        if (topLeftPosition < drawerItems.size) {
            // Update the global cursor position
            globalCursorPosition = topLeftPosition
            
            // Apply focus to the top-left item
            if (!isTransitioning && allowPositionUpdates) {
                updateFocusOnCurrentPage(true)
            }
        }
    }
    
    /**
     * Hide the cursor by clearing focus from all items
     */
    private fun hideCursor() {
        // Clear focus on ALL page items, not just current page
        for (pageIndex in 0 until pagerAdapter.itemCount) {
            val recyclerView = pagerAdapter.getRecyclerViewAt(pageIndex) ?: continue
            
            // Find any focused item on this page and clear its focus
            recyclerView.findFocus()?.clearFocus()
            
            // Clear focus highlights in adapter for this page
            val adapter = recyclerView.adapter as? AppDrawerGridAdapter
            adapter?.clearFocus()
        }
    }
    
    /**
     * Show the cursor by setting focus to current position
     */
    private fun showCursor() {
        if (!isTransitioning && allowPositionUpdates) {
            // Apply focus to the item at the current global cursor position
            updateFocusOnCurrentPage(true)
        }
    }
    
    /**
     * Create a simple but delightful page transition
     */
    private fun createDelightfulPageTransformer(): ViewPager2.PageTransformer {
        return ViewPager2.PageTransformer { page, position ->
            try {
                // Basic transformation that doesn't reveal adjacent pages
                when {
                    position <= -1f || position >= 1f -> {
                        // Hide pages that are not visible
                        page.alpha = 0f
                    }
                    position == 0f -> {
                        // Current page fully visible
                        page.alpha = 1f
                        page.translationX = 0f
                    }
                    position > 0f && position < 1f -> {
                        // Next page coming in
                        page.alpha = 1f - position
                        page.translationX = -page.width * position * 0.25f
                    }
                    position < 0f && position > -1f -> {
                        // Previous page going out
                        page.alpha = 1f + position
                        page.translationX = page.width * position * 0.25f
                    }
                }
            } catch (e: Exception) {
                // Fallback if anything goes wrong
                if (position >= -1 && position <= 1) {
                    page.alpha = maxOf(0f, 1f - Math.abs(position))
                } else {
                    page.alpha = 0f
                }
            }
        }
    }
    
    /**
     * Set up page indicators
     */
    private fun setupPageIndicators() {
        // Clear any existing indicators
        pageIndicator.removeAllViews()
        indicators.clear()
        
        // Get number of pages
        val pageCount = (drawerItems.size + itemsPerPage - 1) / itemsPerPage
        
        // Create indicator views
        val size = resources.getDimensionPixelSize(R.dimen.indicator_size)
        val margin = resources.getDimensionPixelSize(R.dimen.indicator_margin)
        
        for (i in 0 until pageCount) {
            val indicator = View(this)
            
            // Set layout parameters with margins
            val layoutParams = LinearLayout.LayoutParams(size, size)
            layoutParams.marginStart = margin
            layoutParams.marginEnd = margin
            
            // Set initial state (first indicator active, others inactive)
            indicator.background = if (i == 0) {
                getDrawable(R.drawable.indicator_active)
            } else {
                getDrawable(R.drawable.indicator_inactive)
            }
            
            // Add to container and list
            pageIndicator.addView(indicator, layoutParams)
            indicators.add(indicator)
        }
    }
    
    /**
     * Update page indicator based on current page
     */
    private fun updatePageIndicator() {
        val currentPage = viewPager.currentItem
        
        // Update all indicators
        for (i in indicators.indices) {
            indicators[i].background = if (i == currentPage) {
                getDrawable(R.drawable.indicator_active)
            } else {
                getDrawable(R.drawable.indicator_inactive)
            }
            
            // Reset scale and alpha for non-animated indicators
            indicators[i].scaleX = if (i == currentPage) 1.0f else 0.8f
            indicators[i].scaleY = if (i == currentPage) 1.0f else 0.8f
            indicators[i].alpha = if (i == currentPage) 1.0f else 0.5f
        }
    }
    
    /**
     * Animate page indicators during scroll
     */
    private fun animatePageIndicators(position: Int, positionOffset: Float) {
        // Only process if we have indicators
        if (indicators.isEmpty()) return
        
        // Ensure we're within bounds
        if (position >= 0 && position < indicators.size - 1) {
            // Current page indicator
            indicators[position].scaleX = 1f + (0.2f * (1f - positionOffset))
            indicators[position].scaleY = 1f + (0.2f * (1f - positionOffset))
            
            // Alpha transition - not as needed for circle indicators but adds subtlety
            indicators[position].alpha = 0.5f + (0.5f * (1f - positionOffset))
            
            // Next page indicator
            indicators[position + 1].scaleX = 0.8f + (0.2f * positionOffset)
            indicators[position + 1].scaleY = 0.8f + (0.2f * positionOffset)
            indicators[position + 1].alpha = 0.5f + (0.5f * positionOffset)
        }
    }
    
    /**
     * Setup touch handling for drag operations
     */
    private fun setupTouchHandling() {
        // Hack to get internal RecyclerView from ViewPager2
        try {
            val recyclerViewField = ViewPager2::class.java.getDeclaredField("mRecyclerView")
            recyclerViewField.isAccessible = true
            val viewPagerRecyclerView = recyclerViewField.get(viewPager) as RecyclerView
            
            // Add a custom ItemDecoration to draw drag overlay
            viewPagerRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                    // Get current page RecyclerView
                    val currentPage = viewPager.currentItem
                    val currentRecyclerView = pagerAdapter.getRecyclerViewAt(currentPage)
                    
                    // Draw drag overlay if needed
                    currentRecyclerView?.let { recyclerView ->
                        dragHelper.onDrawOver(canvas, recyclerView)
                    }
                }
            })
        } catch (e: Exception) {
            // If we can't get the RecyclerView, fallback to simpler handling
            e.printStackTrace()
        }
    }
    
    /**
     * Load drawer items
     */
    private suspend fun loadDrawerItems(): List<DrawerItem> {
        // Get all installed apps
        val allApps = AppUtils.getInstalledApps(packageManager)
        
        // Filter out hidden apps and our own launcher
        val hiddenApps = prefManager.getHiddenApps()
        val ourPackageName = "com.example.dumbphonelauncher"
        
        val visibleApps = allApps.filterNot { 
            hiddenApps.contains(it.packageName) || it.packageName == ourPackageName
        }
        
        // Load saved layout from preferences
        val savedItems = prefManager.getDrawerItems(allApps)
        if (savedItems.isNotEmpty()) {
            // Even if we have saved items, filter out our launcher app
            return savedItems.filter {
                when (it) {
                    is DrawerItem.AppItem -> it.appInfo.packageName != ourPackageName
                    is DrawerItem.FolderItem -> {
                        // Remove our app from any folders
                        val appsBefore = it.folder.apps.size
                        it.folder.apps.removeIf { app -> app.packageName == ourPackageName }
                        val appsAfter = it.folder.apps.size
                        
                        // Keep folders that still have apps after filtering
                        it.folder.apps.isNotEmpty()
                    }
                }
            }
        }
        
        // If no saved items, sort apps alphabetically
        val items = mutableListOf<DrawerItem>()
        visibleApps.sortedBy { it.appName }.forEach { app ->
            items.add(DrawerItem.AppItem(app))
        }
        return items
    }
    
    /**
     * Save current drawer layout
     */
    private fun saveDrawerItems() {
        prefManager.saveDrawerItems(drawerItems)
    }
    
    /**
     * Handle item reordering when drag completes
     */
    private fun handleItemReordering(fromPosition: Int, toPosition: Int) {
        if (fromPosition != toPosition && fromPosition >= 0 && toPosition >= 0 
            && fromPosition < drawerItems.size && toPosition < drawerItems.size) {
            
            // Get the item to move
            val item = drawerItems[fromPosition]
            
            // Remove from original position and add to new position
            drawerItems.removeAt(fromPosition)
            drawerItems.add(toPosition, item)
            
            // Save changes
            saveDrawerItems()
            
            // Refresh the adapter
            refreshAdapter(toPosition)
        }
    }
    
    /**
     * Handle folder creation when drag item is dropped on another
     */
    private fun handleFolderCreation(fromItem: DrawerItem, toItem: DrawerItem) {
        // Find positions
        val fromPosition = drawerItems.indexOf(fromItem)
        val toPosition = drawerItems.indexOf(toItem)
        
        if (fromPosition >= 0 && toPosition >= 0) {
            // Create a new folder from the two items
            if (fromItem is DrawerItem.AppItem && toItem is DrawerItem.AppItem) {
                // Create folder with both apps
                val folder = Folder(
                    name = "Folder",
                    apps = mutableListOf(fromItem.appInfo, toItem.appInfo)
                )
                
                // Replace the target item with the folder and remove the source item
                drawerItems[toPosition] = DrawerItem.FolderItem(folder)
                drawerItems.removeAt(fromPosition)
                
            } else if (fromItem is DrawerItem.AppItem && toItem is DrawerItem.FolderItem) {
                // Add app to existing folder
                toItem.folder.apps.add(fromItem.appInfo)
                drawerItems.removeAt(fromPosition)
            }
            
            // Save changes
            saveDrawerItems()
            
            // Refresh the adapter
            refreshAdapter(toPosition)
        }
    }
    
    /**
     * Refresh the adapter after changes
     */
    private fun refreshAdapter(targetPosition: Int) {
        // Instead of recreating the entire adapter, update the existing one
        val targetPage = targetPosition / itemsPerPage
        
        if (this::pagerAdapter.isInitialized) {
            // Update pages that need refreshing
            val recyclerView = pagerAdapter.getRecyclerViewAt(targetPage)
            recyclerView?.adapter?.let {
                if (it is AppDrawerGridAdapter) {
                    // Calculate items for this page
                    val startIndex = targetPage * itemsPerPage
                    val endIndex = minOf(startIndex + itemsPerPage, drawerItems.size)
                    val pageItems = if (startIndex < drawerItems.size) {
                        drawerItems.subList(startIndex, endIndex)
                    } else {
                        listOf()
                    }
                    
                    // Update adapter items
                    it.updateItems(pageItems)
                }
            }
            
            // Try to restore the current page
            viewPager.currentItem = targetPage.coerceIn(0, pagerAdapter.itemCount - 1)
            
            // Update page indicator
            updatePageIndicator()
        } else {
            setupViewPager()
        }
    }
    
    /**
     * Handle dragging item to home screen
     */
    private fun handleDragToHomeScreen(item: DrawerItem) {
        setResult(RESULT_OK, intent.apply {
            putExtra("dragged_item", when (item) {
                is DrawerItem.AppItem -> "app:${item.appInfo.packageName}"
                is DrawerItem.FolderItem -> {
                    val apps = item.folder.apps.joinToString(";") { it.packageName }
                    "folder:${item.folder.name}:$apps"
                }
            })
        })
        
        // Close the app drawer with animation
        finish()
        // Animation is now handled in the overridden finish() method
    }
    
    /**
     * Handle app hidden event from AppOptionsPopupHelper
     */
    override fun onAppHidden(packageName: String) {
        // Refresh the app drawer when an app is hidden
        loadAppsAsync()
    }
    
    /**
     * Handle key events for custom navigation with improved focus handling
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Completely block all input during transitions
        if (isTransitioning || !allowPositionUpdates) {
            return true
        }
        
        // If we're in touch mode, switch to d-pad mode and handle the key press immediately
        if (isTouchMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    switchToDPadMode()
                    // After setting cursor to top-left, handle left movement (which typically won't do anything at leftmost position)
                    moveCursor(DIRECTION_LEFT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    switchToDPadMode()
                    // After setting cursor to top-left, move right if possible
                    moveCursor(DIRECTION_RIGHT)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    switchToDPadMode() 
                    // After setting cursor to top-left, handle up (which typically won't do anything in top row)
                    moveCursor(DIRECTION_UP)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    switchToDPadMode()
                    // After setting cursor to top-left, move down
                    moveCursor(DIRECTION_DOWN)
                    return true
                }
            }
        }
        
        // Already in d-pad mode, process navigation keys
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                moveCursor(DIRECTION_LEFT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                moveCursor(DIRECTION_RIGHT)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                moveCursor(DIRECTION_UP)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                moveCursor(DIRECTION_DOWN)
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        
        // Let the system handle other keys
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Handle touch events for drag operations and single-touch app opening
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        currentTouchEvent = ev
        
        // Track last touch time
        lastTouchTime = System.currentTimeMillis()
        
        // If we detect a touch, switch to touch mode
        if (ev.action == MotionEvent.ACTION_DOWN && !isTouchMode) {
            switchToTouchMode()
        }
        
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = ev.rawX
                initialTouchY = ev.rawY
                isLongPress = false
            }
            MotionEvent.ACTION_MOVE -> {
                currentTouchX = ev.rawX
                currentTouchY = ev.rawY
                
                // If we have a touched item and view, handle dragging
                if (touchedItem != null && touchedView != null && touchedItemPosition >= 0) {
                    // Get current page RecyclerView
                    val currentPage = viewPager.currentItem
                    val currentRecyclerView = pagerAdapter.getRecyclerViewAt(currentPage)
                    
                    // Handle drag movement
                    currentRecyclerView?.let { recyclerView ->
                        if (dragHelper.onTouchDown(ev, touchedView!!, touchedItem!!, touchedItemPosition)) {
                            // If touch down initiated drag, we need to handle it
                            if (dragHelper.onTouchMove(ev, recyclerView)) {
                                return true
                            }
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // If we have a touched item and view, handle drop
                if (touchedItem != null && touchedView != null && touchedItemPosition >= 0) {
                    // Get current page RecyclerView
                    val currentPage = viewPager.currentItem
                    val currentRecyclerView = pagerAdapter.getRecyclerViewAt(currentPage)
                    
                    // Handle drag end
                    currentRecyclerView?.let { recyclerView ->
                        if (dragHelper.onTouchUp(ev, recyclerView)) {
                            // Reset touch tracking
                            touchedItem = null
                            touchedView = null
                            touchedItemPosition = -1
                            isLongPress = false
                            return true
                        }
                    }
                    
                    // Reset touch tracking
                    touchedItem = null
                    touchedView = null
                    touchedItemPosition = -1
                    isLongPress = false
                } else if (ev.action == MotionEvent.ACTION_UP) {
                    // This is a regular touch (not a drag, not a long press)
                    // Try to find and open the tapped app
                    
                    // Get touch coordinates
                    val x = ev.rawX
                    val y = ev.rawY
                    
                    // Get current page view and find the tapped item
                    val currentPage = viewPager.currentItem
                    val currentRecyclerView = pagerAdapter.getRecyclerViewAt(currentPage)
                    
                    // Calculate tap tolerance (movement less than this is considered a tap)
                    val tapTolerance = 20f
                    val isSimpleTap = Math.abs(x - initialTouchX) < tapTolerance && 
                                       Math.abs(y - initialTouchY) < tapTolerance
                    
                    // Only try to find the item if this is a simple tap, not a swipe
                    if (isSimpleTap && currentRecyclerView != null) {
                        // Convert raw screen coordinates to relative coordinates in the RecyclerView
                        val recyclerViewLocation = IntArray(2)
                        currentRecyclerView.getLocationOnScreen(recyclerViewLocation)
                        
                        val relativeX = x - recyclerViewLocation[0]
                        val relativeY = y - recyclerViewLocation[1]
                        
                        // Find the child view at these coordinates
                        val touchedView = currentRecyclerView.findChildViewUnder(relativeX, relativeY)
                        
                        if (touchedView != null) {
                            // Get the adapter position of the touched view
                            val position = currentRecyclerView.getChildAdapterPosition(touchedView)
                            if (position != RecyclerView.NO_POSITION) {
                                // Get global position from page and local position
                                val globalPosition = getGlobalPosition(currentPage, position)
                                
                                // Check if the position is valid
                                if (globalPosition >= 0 && globalPosition < drawerItems.size) {
                                    // Get the item at this position
                                    val item = drawerItems[globalPosition]
                                    
                                    // Open the app/folder directly
                                    when (item) {
                                        is DrawerItem.AppItem -> {
                                            // Open the app
                                            startActivity(item.appInfo.launchIntent)
                                            return true
                                        }
                                        is DrawerItem.FolderItem -> {
                                            // Open the folder
                                            showFolderContents(item.folder)
                                            return true
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return super.dispatchTouchEvent(ev)
    }
    
    /**
     * Convert global cursor position to page and local position
     */
    private fun getPageFromGlobalPosition(position: Int): Int {
        return position / itemsPerPage
    }
    
    /**
     * Convert global cursor position to local position within a page
     */
    private fun getLocalPositionFromGlobal(globalPosition: Int): Int {
        return globalPosition % itemsPerPage
    }
    
    /**
     * Calculate global position from page and local position
     */
    private fun getGlobalPosition(page: Int, localPosition: Int): Int {
        return (page * itemsPerPage) + localPosition
    }
    
    /**
     * Update the global cursor position and apply focus to the correct item
     */
    private fun updateCursorPosition(newGlobalPosition: Int, animate: Boolean = true) {
        if (!allowPositionUpdates) return
        
        // Ensure position is within valid range
        val clampedPosition = newGlobalPosition.coerceIn(0, drawerItems.size - 1)
        
        // Calculate target page and position
        val targetPage = getPageFromGlobalPosition(clampedPosition)
        val localPosition = getLocalPositionFromGlobal(clampedPosition)
        
        // Update global tracking
        globalCursorPosition = clampedPosition
        
        // Check if we need to change page
        val currentPage = viewPager.currentItem
        if (targetPage != currentPage) {
            // Start transition
            isTransitioning = true
            allowPositionUpdates = false
            
            // Disable all focus temporarily during page change
            disableAllFocus()
            
            // Change page with animation if requested
            viewPager.setCurrentItem(targetPage, animate)
            
            // Set a timeout to ensure transition completes
            Handler(Looper.getMainLooper()).postDelayed({
                // First delay to ensure page change completes
                updateFocusAfterPageChange()
            }, 250) // Slightly longer delay for page transitions
        } else {
            // Just update focus on current page
            updateFocusOnCurrentPage(false)
        }
    }
    
    /**
     * Force update the focus position after a page change
     */
    private fun updateFocusAfterPageChange() {
        // Allow animations to complete first
        Handler(Looper.getMainLooper()).postDelayed({
            // Update focus
            updateFocusOnCurrentPage(true)
            
            // Re-enable position updates after applying focus
            Handler(Looper.getMainLooper()).postDelayed({
                isTransitioning = false
                allowPositionUpdates = true
            }, 200)
        }, 50)
    }
    
    /**
     * Apply focus to the item corresponding to the global cursor position on the current page
     */
    private fun updateFocusOnCurrentPage(clearOtherFocus: Boolean = true) {
        val currentPage = viewPager.currentItem
        val localPosition = getLocalPositionFromGlobal(globalCursorPosition)
        
        // If requested, clear focus from all pages first
        if (clearOtherFocus) {
            hideCursor()
        }
        
        // Find the RecyclerView for this page
        val recyclerView = pagerAdapter.getRecyclerViewAt(currentPage)
        if (recyclerView == null) return
        
        // Find the target view
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(localPosition)
        if (viewHolder == null) {
            // If viewholder isn't available, try scrolling to position
            (recyclerView.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(
                localPosition, 0
            )
            
            // Try again with a delay
            Handler(Looper.getMainLooper()).postDelayed({
                val laterViewHolder = recyclerView.findViewHolderForAdapterPosition(localPosition)
                if (laterViewHolder != null) {
                    applyFocusToItem(recyclerView, laterViewHolder.itemView, false) // Already cleared focus
                }
            }, 50)
            return
        }
        
        // Apply focus to the found item
        applyFocusToItem(recyclerView, viewHolder.itemView, false) // Already cleared focus
    }
    
    /**
     * Apply focus to a specific view
     */
    private fun applyFocusToItem(recyclerView: RecyclerView, view: View, clearOtherFocus: Boolean) {
        // Clear any existing focus if needed
        if (clearOtherFocus) {
            val focusedView = recyclerView.findFocus()
            if (focusedView != null && focusedView != view) {
                focusedView.clearFocus()
            }
        }
        
        // Get position of this view in the adapter
        val position = recyclerView.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return
        
        // Update adapter focus position
        val adapter = recyclerView.adapter as? AppDrawerGridAdapter
        adapter?.let {
            it.currentFocusPosition = position
        }
        
        // Ensure container blocks child views from getting focus
        if (view is ViewGroup) {
            view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
        
        // Apply highlight immediately to reduce flickering
        view.setBackgroundResource(R.drawable.bg_item_focused)
        
        // Make the view focusable
        view.isFocusable = true
        view.isFocusableInTouchMode = true
                
        // Request focus in a simpler way to reduce flicker
        if (!view.hasFocus()) {
            view.requestFocus()
        }
    }
    
    /**
     * Helper method to disable all focus during transitions
     */
    private fun disableAllFocus() {
        // Disable focus on each page's items
        for (i in 0 until pagerAdapter.itemCount) {
            val recyclerView = pagerAdapter.getRecyclerViewAt(i) ?: continue
            
            // Make all items unfocusable except the current target
            val viewGroup = recyclerView as? ViewGroup
            viewGroup?.descendants?.forEach { view ->
                if (view != null && view != recyclerView) {
                    // Keep the background and state, just disable focus temporarily
                    view.isFocusable = false
                    view.isFocusableInTouchMode = false
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
     * Move cursor to the specified direction
     */
    private fun moveCursor(direction: Int) {
        if (isTransitioning || !allowPositionUpdates) return
        
        val currentPage = viewPager.currentItem
        val localPosition = getLocalPositionFromGlobal(globalCursorPosition)
        
        // Calculate current row and column
        val currentRow = localPosition / columnCount
        val currentCol = localPosition % columnCount
        
        when (direction) {
            DIRECTION_LEFT -> {
                if (currentCol > 0) {
                    // Move left within current page
                    updateCursorPosition(globalCursorPosition - 1)
                } else if (currentPage > 0) {
                    // Going to previous page
                    val targetPage = currentPage - 1
                    
                    // Store current position details before changing page
                    val sourceRow = currentRow
                    val sourceCol = currentCol
                    
                    // Get item count on previous page
                    val prevPageItems = pagerAdapter.getItemCountForPage(targetPage)
                    val prevPageRows = (prevPageItems + columnCount - 1) / columnCount
                    
                    // Ensure the row exists on the previous page
                    val targetRow = sourceRow.coerceAtMost(prevPageRows - 1)
                    
                    // Go to rightmost column on that row
                    val lastColOnRow = minOf(columnCount - 1, 
                        (prevPageItems - 1) % columnCount)
                    
                    // Calculate the target position on the previous page
                    val targetLocalPosition = (targetRow * columnCount) + lastColOnRow
                    val targetGlobalPosition = getGlobalPosition(targetPage, targetLocalPosition)
                    
                    // Store this info in a very explicit way for page change handling
                    dpadNavigationInfo = DPadNavigationInfo(
                        direction = DIRECTION_LEFT,
                        sourceRow = sourceRow,
                        sourceCol = sourceCol,
                        targetPage = targetPage,
                        targetGlobalPosition = targetGlobalPosition
                    )
                    
                    // Request page change - don't set cursor position yet
                    isTransitioning = true
                    allowPositionUpdates = false
                    viewPager.setCurrentItem(targetPage, true)
                }
            }
            DIRECTION_RIGHT -> {
                if (currentCol < columnCount - 1 && localPosition < pagerAdapter.getItemCountForPage(currentPage) - 1) {
                    // Move right within current page
                    updateCursorPosition(globalCursorPosition + 1)
                } else if (currentPage < pagerAdapter.itemCount - 1) {
                    // Going to next page
                    val targetPage = currentPage + 1
                    
                    // Store current position details before changing page
                    val sourceRow = currentRow
                    val sourceCol = currentCol
                    
                    // Get the count of items on the target page
                    val nextPageItems = pagerAdapter.getItemCountForPage(targetPage)
                    
                    // Ensure the row exists on the next page
                    val targetRow = sourceRow.coerceAtMost((nextPageItems - 1) / columnCount)
                    
                    // Go to leftmost column on that row
                    val targetLocalPosition = targetRow * columnCount
                    val targetGlobalPosition = getGlobalPosition(targetPage, targetLocalPosition)
                    
                    // Store this info in a very explicit way for page change handling
                    dpadNavigationInfo = DPadNavigationInfo(
                        direction = DIRECTION_RIGHT,
                        sourceRow = sourceRow,
                        sourceCol = sourceCol,
                        targetPage = targetPage,
                        targetGlobalPosition = targetGlobalPosition
                    )
                    
                    // Request page change - don't set cursor position yet
                    isTransitioning = true
                    allowPositionUpdates = false
                    viewPager.setCurrentItem(targetPage, true)
                }
            }
            DIRECTION_UP -> {
                if (currentRow > 0) {
                    // Move up within current page
                    updateCursorPosition(globalCursorPosition - columnCount)
                }
            }
            DIRECTION_DOWN -> {
                val targetPosition = globalCursorPosition + columnCount
                if (targetPosition < drawerItems.size && 
                    currentRow < rowCount - 1 && 
                    localPosition + columnCount < pagerAdapter.getItemCountForPage(currentPage)) {
                    // Move down within current page
                    updateCursorPosition(targetPosition)
                }
            }
        }
    }
    
    /**
     * Data class to track d-pad navigation between pages
     */
    private data class DPadNavigationInfo(
        val direction: Int,        // DIRECTION_LEFT or DIRECTION_RIGHT
        val sourceRow: Int,        // Original row on source page
        val sourceCol: Int,        // Original column on source page 
        val targetPage: Int,       // Destination page number
        val targetGlobalPosition: Int // Calculated position on target page
    )
    
    // Track d-pad navigation for proper position handling
    private var dpadNavigationInfo: DPadNavigationInfo? = null
    
    /**
     * Override finish method to add exit animation
     */
    override fun finish() {
        super.finish()
        // Start fade out animation
        overridePendingTransition(0, android.R.anim.fade_out)
    }
    
    companion object {
        const val DIRECTION_LEFT = 0
        const val DIRECTION_RIGHT = 1
        const val DIRECTION_UP = 2
        const val DIRECTION_DOWN = 3
        
        // Touch timeout before returning to d-pad mode (in ms)
        const val TOUCH_TIMEOUT = 5000L
    }
} 