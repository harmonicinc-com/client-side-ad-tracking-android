package com.harmonicinc.clientsideadtracking.tracking.client.omsdk

import android.content.Context
import com.github.harmonicinc.clientsideadtracking.R
import java.io.IOException
import java.nio.charset.Charset

object OmidJsLoader {
    /**
     * getOmidJs - gets the Omid JS resource as a string
     * @param context - used to access the JS resource
     * @return - the Omid JS resource as a string
     */
    fun getOmidJs(context: Context): String {
        val res = context.resources
        try {
            res.openRawResource(R.raw.omsdk_v1).use { inputStream ->
                val b = ByteArray(inputStream.available())
                val bytesRead = inputStream.read(b)
                return String(b, 0, bytesRead, Charset.defaultCharset())
            }
        } catch (e: IOException) {
            throw UnsupportedOperationException("Yikes, omid resource not found", e)
        }
    }
}