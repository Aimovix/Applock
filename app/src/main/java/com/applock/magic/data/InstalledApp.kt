package com.applock.magic.data

import android.graphics.drawable.Drawable

data class InstalledApp(
    val name: String,
    val packageName: String,
    val icon: Drawable?,
    val isLocked: Boolean,
    val isSystemCritical: Boolean = false,
    val isHidden: Boolean = false
)
