package com.harmonicinc.clientsideadtracking.error

/**
 * Sealed class representing different types of errors that can occur in the ad tracking library.
 */
sealed class AdTrackingError(
    val message: String,
    val cause: Throwable? = null,
    val isRecoverable: Boolean = true
) {
    /**
     * Error that occurs during session initialization.
     *
     * @param errorMessage Details about the session initialization error
     * @param errorCause The underlying exception that caused the failure
     */
    data class SessionInitError(
        val errorMessage: String,
        val errorCause: Throwable? = null,
        val errorIsRecoverable: Boolean
    ) : AdTrackingError(errorMessage, errorCause, errorIsRecoverable)

    /**
     * Error that occurs when ad metadata cannot be fetched or parsed.
     *
     * @param errorMessage Details about the metadata error
     * @param errorCause The underlying exception that caused the failure
     */
    data class MetadataError(
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AdTrackingError(errorMessage, errorCause)

    /**
     * Error that occurs when a beacon request fails to be sent.
     *
     * @param beaconUrl The URL of the beacon that failed to send
     * @param event The event type that was being tracked
     * @param errorMessage Details about the error
     * @param errorCause The underlying exception that caused the failure
     */
    data class BeaconError(
        val beaconUrl: String,
        val event: String,
        val errorMessage: String,
        val errorCause: Throwable? = null
    ) : AdTrackingError("Beacon error for $event at $beaconUrl: $errorMessage", errorCause)
}
