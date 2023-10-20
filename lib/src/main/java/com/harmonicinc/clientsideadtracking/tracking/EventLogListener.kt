package com.harmonicinc.clientsideadtracking.tracking

import com.harmonicinc.clientsideadtracking.tracking.model.EventLog

interface EventLogListener {
    fun onEvent(eventLog: EventLog) {}
}