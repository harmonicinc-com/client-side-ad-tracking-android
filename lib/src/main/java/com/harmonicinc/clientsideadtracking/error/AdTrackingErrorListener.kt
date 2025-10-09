package com.harmonicinc.clientsideadtracking.error

/**
 * Listener interface for receiving error callbacks.
 */
interface AdTrackingErrorListener {
    /**
     * Called when an error occurs.
     * 
     * @param error The error that occurred
     */
    fun onError(error: AdTrackingError)
}
