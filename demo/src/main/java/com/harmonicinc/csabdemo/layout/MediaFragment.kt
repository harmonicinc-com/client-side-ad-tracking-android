package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi class MediaFragment: Fragment() {
    private lateinit var playbackActivity: PlaybackActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.media_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playbackActivity = activity as PlaybackActivity

        val trackingOverlaySwitch = view.findViewById<MaterialSwitch>(R.id.tracking_overlay_switch)
        trackingOverlaySwitch.setOnClickListener {
            playbackActivity.googlePalAddon?.trackingOverlay?.showOverlay = trackingOverlaySwitch.isChecked
        }

        val loadBtn = view.findViewById<Button>(R.id.load_button)
        val stopBtn = view.findViewById<Button>(R.id.stop_button)
        loadBtn.setOnClickListener {
            val url = view.findViewById<TextInputLayout>(R.id.url).editText?.text ?: ""
            if (url.isEmpty()) {
                showSnackbar("No URL entered")
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.Main).launch {
                playbackActivity.onResume(url.toString())
            }
        }
        stopBtn.setOnClickListener {
            playbackActivity.onStop()
        }
    }

    private fun showSnackbar(msg: String) {
        Snackbar.make(requireView(), msg, Snackbar.LENGTH_SHORT).show()
    }
}