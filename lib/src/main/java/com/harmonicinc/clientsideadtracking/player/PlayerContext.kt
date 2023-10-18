package com.harmonicinc.clientsideadtracking.player

import android.content.Context
import android.view.ViewGroup
import com.harmonicinc.clientsideadtracking.player.baseplayer.AbstractPlayer

data class PlayerContext (

    var wrappedPlayer: AbstractPlayer? = null,

    var playerView: ViewGroup? = null,

    var overlayViewContainer: ViewGroup? = null,

    var androidContext: Context? = null

)