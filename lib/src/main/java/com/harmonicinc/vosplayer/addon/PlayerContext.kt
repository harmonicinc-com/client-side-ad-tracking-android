package com.harmonicinc.vosplayer.addon

import android.content.Context
import android.view.ViewGroup
import com.harmonicinc.vosplayer.baseplayer.AbstractPlayer

data class PlayerContext (

    var wrappedPlayer: AbstractPlayer? = null,

    var playerView: ViewGroup? = null,

    var overlayViewContainer: ViewGroup? = null,

    var androidContext: Context? = null

)