package com.harmonicinc.clientsideadtracking.tracking.util

import android.content.Context
import android.content.pm.PackageManager

object AndroidUtils {
    fun isTelevision(ctx: Context): Boolean {
        return ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }
}