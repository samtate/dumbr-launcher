package com.example.dumbphonelauncher.util

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.model.DrawerItem

/**
 * Helper class to handle drag and drop operations in the app drawer
 */
class AppDrawerDragHelper(
    private val context: Context,
    private val upArrowView: View,
    private val onDragComplete: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onFolderCreated: (fromItem: DrawerItem, toItem: DrawerItem) -> Unit,
    private val onDragToHomeScreen: (item: DrawerItem) -> Unit
) {
    private var draggedItem: DrawerItem? = null
    private var draggedView: View? = null
    private var draggedPosition = -1
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var currentTouchX = 0f
    private var currentTouchY = 0f
    
    /**
     * Check if the movement is significant enough to be considered a drag
     */
    fun isMovementSignificant(): Boolean {
        val dx = Math.abs(currentTouchX - initialTouchX)
        val dy = Math.abs(currentTouchY - initialTouchY)
        val touchSlop = 20f // Minimum distance to be considered a drag
        return dx > touchSlop || dy > touchSlop
    }
    
    /**
     * Handle touch down event to initiate drag
     */
    fun onTouchDown(event: MotionEvent, view: View, item: DrawerItem, position: Int): Boolean {
        draggedItem = item
        draggedView = view
        draggedPosition = position
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        currentTouchX = initialTouchX
        currentTouchY = initialTouchY
        return true
    }
    
    /**
     * Handle touch move event during drag
     */
    fun onTouchMove(event: MotionEvent, recyclerView: RecyclerView): Boolean {
        currentTouchX = event.rawX
        currentTouchY = event.rawY
        
        // Find the view under the touch point
        val x = event.rawX
        val y = event.rawY
        
        val location = IntArray(2)
        recyclerView.getLocationOnScreen(location)
        
        val relativeX = x - location[0]
        val relativeY = y - location[1]
        
        val targetView = recyclerView.findChildViewUnder(relativeX, relativeY)
        if (targetView != null) {
            val targetPosition = recyclerView.getChildAdapterPosition(targetView)
            if (targetPosition != RecyclerView.NO_POSITION && targetPosition != draggedPosition) {
                // Handle item reordering
                return true
            }
        }
        
        return false
    }
    
    /**
     * Handle touch up event to complete drag
     */
    fun onTouchUp(event: MotionEvent, recyclerView: RecyclerView): Boolean {
        // Find the view under the touch point
        val x = event.rawX
        val y = event.rawY
        
        val location = IntArray(2)
        recyclerView.getLocationOnScreen(location)
        
        val relativeX = x - location[0]
        val relativeY = y - location[1]
        
        val targetView = recyclerView.findChildViewUnder(relativeX, relativeY)
        if (targetView != null) {
            val targetPosition = recyclerView.getChildAdapterPosition(targetView)
            if (targetPosition != RecyclerView.NO_POSITION && targetPosition != draggedPosition) {
                // Handle item reordering
                onDragComplete(draggedPosition, targetPosition)
                return true
            }
        }
        
        // Reset drag state
        draggedItem = null
        draggedView = null
        draggedPosition = -1
        
        return false
    }
    
    /**
     * Draw overlay during drag operation
     */
    fun onDrawOver(canvas: android.graphics.Canvas, recyclerView: RecyclerView) {
        draggedView?.let { view ->
            // Draw the dragged view at the current touch position
            canvas.save()
            canvas.translate(currentTouchX - view.width / 2, currentTouchY - view.height / 2)
            view.draw(canvas)
            canvas.restore()
        }
    }
} 