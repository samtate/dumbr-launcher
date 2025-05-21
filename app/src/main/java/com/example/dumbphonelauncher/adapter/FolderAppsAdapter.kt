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

class FolderAppsAdapter(
    private val apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<FolderAppsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.app_icon)
        val appName: TextView = view.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.appIcon.setImageDrawable(app.icon)
        holder.appName.text = app.appName
        
        // Action on touch up instead of down to allow for gestures
        holder.itemView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_UP -> {
                    // Only trigger click if the finger is still on the view
                    if (isPointInsideView(event.rawX, event.rawY, v)) {
                        onAppClick(app)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
        
        // Set D-pad navigation
        setupDpadNavigation(holder.itemView, position)
        
        // Set focus for d-pad navigation
        holder.itemView.isFocusable = true
        holder.itemView.isFocusableInTouchMode = true
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
    
    private fun setupDpadNavigation(itemView: View, position: Int) {
        // Calculate the row and column based on a 3-column grid
        val row = position / 3
        val col = position % 3
        
        // We already set proper focus properties
        // The RecyclerView will handle D-pad navigation automatically
    }

    override fun getItemCount() = apps.size
} 