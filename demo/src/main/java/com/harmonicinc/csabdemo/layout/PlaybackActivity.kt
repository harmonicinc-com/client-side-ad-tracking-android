package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.github.harmonicinc.csabdemo.BuildConfig
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.harmonicinc.clientsideadtracking.AdTrackingManager
import com.harmonicinc.clientsideadtracking.AdTrackingManagerParams
import com.harmonicinc.csabdemo.player.ExoPlayerAdapter
import com.harmonicinc.csabdemo.utils.MaterialUtils.showSnackbar
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi class PlaybackActivity : AppCompatActivity() {
    private lateinit var playerFragment: PlayerFragment
    private lateinit var mediaFragment: MediaFragment
    private lateinit var adTrackingManager: AdTrackingManager
    private lateinit var rootLayout: ConstraintLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playback_activity)
        rootLayout = findViewById(R.id.root_layout)
        adTrackingManager = AdTrackingManager(this)

        // Setup tabs & ExoPlayer
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)

        playerFragment = (supportFragmentManager.findFragmentById(R.id.vos_player_fragment) as PlayerFragment?)!!
        val adapter = PlayerFragmentPagerAdapter(playerFragment)
        mediaFragment = adapter.getFragment(0) as MediaFragment
        viewPager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }

    override fun onDestroy() {
        if (adTrackingManager.isSSAISupported()) {
            adTrackingManager.cleanupAfterStop()
        }
        playerFragment.onPlayerStop()
        super.onDestroy()
    }

    fun onPlayerLoad() {
        val adTrackingParams = AdTrackingManagerParams(
            "",
            true,
            "harmonicinc.csabdemo",
            BuildConfig.VERSION_NAME,
            "",
            setOf(7),
            playerFragment.playerView.height,
            playerFragment.playerView.width,
            willAdAutoplay = false,
            willAdPlayMuted = false,
            continuousPlayback = false,
            null,
            null,
            null,
            initRequest = true,
        )

        mediaFragment.hideKeyboard()
        val exceptionHandler = CoroutineExceptionHandler { _, e ->
            showSnackbar("Error occurred: ${e.message}", rootLayout)
        }
        CoroutineScope(Dispatchers.Main).launch(exceptionHandler) {
            var newUrl = mediaFragment.getUrl()
            if (newUrl != null) {
                adTrackingManager.prepareBeforeLoad(newUrl, adTrackingParams)
                if (adTrackingManager.isSSAISupported()) {
                    newUrl = adTrackingManager.appendNonceToUrl(listOf(newUrl))[0]

                    adTrackingManager.onPlay(
                        playerFragment.playerView.context,
                        ExoPlayerAdapter(playerFragment.player!!),
                        playerFragment.playerView.overlayFrameLayout,
                        playerFragment.playerView
                    )
                } else {
                    showSnackbar("Asset does not support SSAI", rootLayout)
                }
                playerFragment.onPlayerLoad(newUrl)
            }
        }
    }

    fun onPlayerStop() {
        if (adTrackingManager.isSSAISupported()) {
            adTrackingManager.cleanupAfterStop()
        }
        playerFragment.onPlayerStop()
    }
}