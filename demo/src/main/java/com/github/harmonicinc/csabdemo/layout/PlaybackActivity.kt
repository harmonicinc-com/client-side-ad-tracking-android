package com.github.harmonicinc.csabdemo.layout

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import com.github.harmonicinc.csabdemo.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.harmonicinc.clientsideadtracking.GooglePalAddon

@UnstableApi class PlaybackActivity : AppCompatActivity() {
    private lateinit var playerFragment: PlayerFragment
    var googlePalAddon: GooglePalAddon? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.playback_activity)

        googlePalAddon = GooglePalAddon(this)

        // Setup tabs & ExoPlayer
        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        playerFragment = (supportFragmentManager.findFragmentById(R.id.vos_player_fragment) as PlayerFragment?)!!
        playerFragment.pushAddon(googlePalAddon!!)
        val adapter = PlayerFragmentPagerAdapter(playerFragment)
        viewPager.adapter = adapter

        val tabLayout = findViewById<TabLayout>(R.id.tab_layout)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = adapter.getTabTitle(position)
        }.attach()
    }

    suspend fun onResume(url: String) {
        super.onResume()
        var newUrl = url
        if (googlePalAddon != null) {
            googlePalAddon!!.prepareBeforeLoad(newUrl)
            if (googlePalAddon!!.isSSAISupported()) {
                newUrl = googlePalAddon!!.appendNonceToUrl(listOf(newUrl))[0]
            }
        }
        playerFragment.onStart(newUrl)
    }

    public override fun onStop() {
        super.onStop()
        playerFragment.onStop()
    }
}