package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.tracking.overlay.EventLog

interface EventLogListener {
    fun onEvent(eventLog: EventLog) {}
}