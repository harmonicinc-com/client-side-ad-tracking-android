package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import com.github.harmonicinc.csabdemo.R

@UnstableApi class PlaybackControlFragment: Fragment() {
    private lateinit var playbackActivity: PlaybackActivity

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.playback_control_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playbackActivity = activity as PlaybackActivity

        val loadBtn = view.findViewById<Button>(R.id.load_button)
        val stopBtn = view.findViewById<Button>(R.id.stop_button)
        loadBtn.setOnClickListener {
            playbackActivity.onPlayerLoad()
        }
        stopBtn.setOnClickListener {
            playbackActivity.onPlayerStop()
        }
    }
}