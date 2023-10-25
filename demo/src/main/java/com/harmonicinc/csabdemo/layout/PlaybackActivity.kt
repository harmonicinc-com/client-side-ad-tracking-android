package com.harmonicinc.csabdemo.layout

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.harmonicinc.clientsideadtracking.GooglePalAddon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi class PlaybackActivity : AppCompatActivity() {
    private lateinit var playerFragment: PlayerFragment
    private lateinit var mediaFragment: MediaFragment
    private lateinit var googlePalAddon: GooglePalAddon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playback_activity)

        googlePalAddon = GooglePalAddon(this)

        // Setup tabs & ExoPlayer
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        playerFragment = (supportFragmentManager.findFragmentById(R.id.vos_player_fragment) as PlayerFragment?)!!
        playerFragment.googlePalAddon = googlePalAddon
        val adapter = PlayerFragmentPagerAdapter(playerFragment)
        mediaFragment = adapter.getFragment(0) as MediaFragment
        viewPager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }

    fun onLoad() {
        CoroutineScope(Dispatchers.Main).launch {
            var newUrl = mediaFragment.getUrl()
            if (newUrl != null) {
                googlePalAddon.prepareBeforeLoad(newUrl)
                if (googlePalAddon.isSSAISupported()) {
                    newUrl = googlePalAddon.appendNonceToUrl(listOf(newUrl))[0]
                }
                mediaFragment.hideKeyboard()
                playerFragment.onStart(newUrl)
            }
        }
    }

    public override fun onStop() {
        playerFragment.onStop()
        super.onStop()
    }
}