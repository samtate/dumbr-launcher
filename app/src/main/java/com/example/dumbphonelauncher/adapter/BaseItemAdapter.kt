package com.example.dumbphonelauncher.adapter

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.example.dumbphonelauncher.model.AppInfo
import com.example.dumbphonelauncher.model.DrawerItem
import com.example.dumbphonelauncher.model.Folder

/**
 * Base adapter for launcher items that provides common functionality
 */
abstract class BaseItemAdapter<VH : RecyclerView.ViewHolder> : RecyclerView.Adapter<VH>() {
    
    /**
     * Helper method to check if touch event is inside the view
     */
    protected fun isPointInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        return (rawX >= x && rawX <= x + view.width &&
                rawY >= y && rawY <= y + view.height)
    }
    
    /**
     * Set up click listener for a view that triggers on ACTION_UP only if pointer is still inside view
     */
    protected fun setupTouchListener(view: View, onClickAction: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    // Only trigger click if the finger is still on the view
                    if (isPointInsideView(event.rawX, event.rawY, v)) {
                        onClickAction()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
        
        // Normal click listener as fallback
        view.setOnClickListener {
            onClickAction()
        }
    }
    
    /**
     * Set focus properties for D-pad navigation
     */
    protected fun setupFocus(view: View) {
        view.isFocusable = true
        view.isFocusableInTouchMode = true
    }
} 