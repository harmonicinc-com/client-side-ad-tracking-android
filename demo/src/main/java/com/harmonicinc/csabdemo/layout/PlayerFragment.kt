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
import com.harmonicinc.csabdemo.player.ExoPlayerAdapter
import com.harmonicinc.clientsideadtracking.GooglePalAddon

@UnstableApi class PlayerFragment: PlaybackSupportFragment() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    var googlePalAddon: GooglePalAddon? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val v = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        layoutInflater.inflate(R.layout.player_view, v)
        playerView = v.findViewById(R.id.exoplayer_view)
        return v
    }

    fun onStart(url: String) {
        if (player == null) {
            initializePlayer(url)
        }
        playerView.onResume()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    private fun initializePlayer(url: String) {
        player = ExoPlayer.Builder(requireContext()).build()
        playerView.player = player
        googlePalAddon!!.prepareAfterPlayerViewCreated(
            playerView.context,
            ExoPlayerAdapter(player!!),
            playerView.overlayFrameLayout,
            playerView
        )

        player?.let {
            it.setMediaItem(createMediaItem(url))
            it.prepare()
            it.play()
        }
    }

    private fun releasePlayer() {
        googlePalAddon?.cleanupAfterStop()
        player?.release()
        player = null
        playerView.player = null
    }

    private fun createMediaItem(url: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
        return builder.build()
    }
}