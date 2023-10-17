package com.harmonicinc.vosplayer.addon.csab.tracking

import com.harmonicinc.vosplayer.addon.csab.tracking.overlay.EventLog

interface EventLogListener {
    fun onEvent(eventLog: EventLog) {}
}