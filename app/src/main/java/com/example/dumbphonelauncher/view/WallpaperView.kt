package com.example.dumbphonelauncher.view

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View

/**
 * A custom view that directly renders the system wallpaper
 * This is a more reliable approach than using an ImageView
 */
class WallpaperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var wallpaperDrawable: Drawable? = null
    private val rect = Rect()
    
    init {
        try {
            // Get the wallpaper drawable directly from the system
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperDrawable = wallpaperManager.drawable
            
            // Make the view transparent
            setBackgroundColor(0x00000000)
        } catch (e: Exception) {
            // Ignore errors in production
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Update the rectangle used to draw the wallpaper
        rect.set(0, 0, w, h)
        
        // Ensure the drawable covers the view
        wallpaperDrawable?.let { drawable ->
            // Center the wallpaper to match the wallpaper manager's display
            drawable.setBounds(0, 0, w, h)
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        wallpaperDrawable?.let { drawable ->
            // Draw the wallpaper onto the view
            drawable.draw(canvas)
        }
    }
    
    /**
     * Refresh the wallpaper drawable from the system
     */
    fun refreshWallpaper() {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            wallpaperDrawable = wallpaperManager.drawable
            
            // Update bounds and redraw
            if (width > 0 && height > 0) {
                wallpaperDrawable?.setBounds(0, 0, width, height)
                invalidate()
            }
        } catch (e: Exception) {
            // Ignore errors in production
        }
    }
} 