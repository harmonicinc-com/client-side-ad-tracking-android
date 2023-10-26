package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        layoutInflater.inflate(R.layout.player_view, v)
        playerView = v.findViewById(R.id.exoplayer_view)
        playbackActivity = activity as PlaybackActivity
        return v
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