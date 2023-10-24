package com.harmonicinc.csabdemo.layout

import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.adapter.FragmentStateAdapter

@UnstableApi class PlayerFragmentPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {
    private val tabs = linkedMapOf(
        Pair("Media", MediaFragment()),
        Pair("Events", EventFragment())
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs.values.elementAt(position)

    fun getTabTitle(position: Int) = tabs.keys.elementAt(position)
}