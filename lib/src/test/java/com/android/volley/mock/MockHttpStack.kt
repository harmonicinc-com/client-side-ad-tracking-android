/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.volley.mock

import com.android.volley.AuthFailureError
import com.android.volley.Request
import com.android.volley.toolbox.BaseHttpStack
import com.android.volley.toolbox.HttpResponse
import com.android.volley.toolbox.HurlStack
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class MockHttpStack : HurlStack() {
    private var mResponseToReturn: HttpResponse? = null
    private var mExceptionToThrow: IOException? = null
    private var lastUrl: String? = null
    private var mLastHeaders: HashMap<String, String>? = null
    private var lastPostBody: ByteArray? = null

    fun getLastHeaders(): Map<String, String>? {
        return mLastHeaders
    }

    fun setResponseToReturn(response: HttpResponse?) {
        mResponseToReturn = response
    }

    fun setExceptionToThrow(exception: IOException?) {
        mExceptionToThrow = exception
    }

    override fun createConnection(url: URL?): HttpURLConnection {
        val connection = super.createConnection(url)
        connection.instanceFollowRedirects = false
        return connection
    }

    @Throws(IOException::class, AuthFailureError::class)
    override fun executeRequest(
        request: Request<*>,
        additionalHeaders: Map<String, String>?
    ): HttpResponse? {
        if (mExceptionToThrow != null) {
            throw mExceptionToThrow!!
        }
        lastUrl = request.url
        mLastHeaders = HashMap()
        if (request.headers != null) {
            mLastHeaders!!.putAll(request.headers)
        }
        if (additionalHeaders != null) {
            mLastHeaders!!.putAll(additionalHeaders)
        }
        lastPostBody = try {
            request.body
        } catch (e: AuthFailureError) {
            null
        }
        return mResponseToReturn
    }
}