package com.harmonicinc.clientsideadtracking.player

interface PlayerEventListener {
    fun onBufferStart()
    fun onBufferEnd()
    fun onPause()
    fun onResume()
}
