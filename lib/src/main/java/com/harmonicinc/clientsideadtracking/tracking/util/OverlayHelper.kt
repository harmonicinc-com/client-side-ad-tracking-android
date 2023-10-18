package com.harmonicinc.clientsideadtracking.tracking.util

import android.view.View
import android.view.ViewGroup

object OverlayHelper {
    fun addViewToContainerView(view: View, containerView: ViewGroup) {
        var parent = view.parent
        if (parent != null && parent is ViewGroup && parent != containerView) {
            parent.removeView(view)
            parent = null
        }
        if (parent == null) {
            containerView.addView(view)
        }
    }
}