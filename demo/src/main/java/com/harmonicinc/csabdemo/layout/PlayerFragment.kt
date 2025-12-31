package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.leanback.app.PlaybackSupportFragment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.github.harmonicinc.csabdemo.R

@UnstableApi class PlayerFragment: PlaybackSupportFragment() {
    var player: ExoPlayer? = null
    lateinit var playerView: PlayerView
    private lateinit var playbackActivity: PlaybackActivity
    private lateinit var muteButton: ImageButton
    private var isMuted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        layoutInflater.inflate(R.layout.player_view, v)
        playerView = v.findViewById(R.id.exoplayer_view)
        muteButton = v.findViewById(R.id.mute_button)
        playbackActivity = activity as PlaybackActivity
        
        muteButton.setOnClickListener {
            toggleMute()
        }
        
        return v
    }

    private fun toggleMute() {
        player?.let {
            isMuted = !isMuted
            it.volume = if (isMuted) 0f else 1f
            updateMuteButtonIcon()
        }
    }

    private fun updateMuteButtonIcon() {
        val iconRes = if (isMuted) {
            android.R.drawable.ic_lock_silent_mode
        } else {
            android.R.drawable.ic_lock_silent_mode_off
        }
        muteButton.setImageResource(iconRes)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        player = ExoPlayer.Builder(requireContext()).build()
        playerView.player = player
    }

    fun onPlayerLoad(url: String) {
        player?.let {
            it.setMediaItem(createMediaItem(url))
            it.prepare()
            it.play()
        }
    }

    fun onPlayerStop() {
        player?.removeMediaItem(0)
    }

    override fun onDestroyView() {
        player?.release()
        playerView.player = null
        player = null
        super.onDestroyView()
    }

    private fun createMediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
        return builder.build()
    }
}