package com.harmonicinc.csabdemo.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar

object MaterialUtils {
    fun showSnackbar(msg: String, view: View) {
        Snackbar.make(view, msg, Snackbar.LENGTH_SHORT).show()
    }
}