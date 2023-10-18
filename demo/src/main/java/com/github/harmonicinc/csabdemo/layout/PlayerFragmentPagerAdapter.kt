package com.github.harmonicinc.csabdemo.layout

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class PlayerFragmentPagerAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {
    private val tabs = linkedMapOf(
        Pair("Media", MediaFragment()),
        Pair("Events", EventFragment())
    )

    override fun getItemCount(): Int = tabs.size

    override fun createFragment(position: Int): Fragment = tabs.values.elementAt(position)

    fun getTabTitle(position: Int) = tabs.keys.elementAt(position)
}