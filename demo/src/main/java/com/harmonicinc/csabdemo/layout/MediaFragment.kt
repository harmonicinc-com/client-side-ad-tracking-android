package com.harmonicinc.csabdemo.layout

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout


@UnstableApi class MediaFragment: Fragment() {
    private lateinit var playbackActivity: PlaybackActivity
    private lateinit var urlInputLayout: TextInputLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.media_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        urlInputLayout = view.findViewById(R.id.url)
        playbackActivity = activity as PlaybackActivity
    }

    fun getUrl(): String? {
        val url = urlInputLayout.editText?.text ?: ""
        if (url.isEmpty()) {
            showSnackbar("No URL entered")
            return null
        }
        return url.toString()
    }

    fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInputLayout.windowToken, 0)
        urlInputLayout.clearFocus()
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
    }
}