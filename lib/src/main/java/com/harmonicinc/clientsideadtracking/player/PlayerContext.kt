package com.harmonicinc.clientsideadtracking.player

import android.content.Context
import android.view.ViewGroup

data class PlayerContext (

    var wrappedPlayer: PlayerAdapter? = null,

    var playerView: ViewGroup? = null,

    var overlayViewContainer: ViewGroup? = null,

    var androidContext: Context? = null

)