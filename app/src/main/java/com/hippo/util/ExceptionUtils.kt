/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.util

import com.hippo.ehviewer.GetText
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.exception.EhException
import com.hippo.network.StatusCodeException
import org.apache.http.conn.ConnectTimeoutException
import java.net.MalformedURLException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object ExceptionUtils {
    private val TAG: String = ExceptionUtils::class.java.getSimpleName()

    @JvmStatic
    fun getReadableString(e: Throwable): String {
        e.printStackTrace()
        if (e is MalformedURLException) {
            return GetText.getString(R.string.error_invalid_url)
        } else if (e is ConnectTimeoutException ||
            e is SocketTimeoutException
        ) {
            return GetText.getString(R.string.error_timeout)
        } else if (e is UnknownHostException) {
            return GetText.getString(R.string.error_unknown_host)
        } else if (e is StatusCodeException) {
            val sb = StringBuilder()
            sb.append(GetText.getString(R.string.error_bad_status_code, e.getResponseCode()))
            if (e.isIdentifiedResponseCode) {
                sb.append(", ").append(e.message)
            }
            return sb.toString()
        } else if (e is ProtocolException && e.message != null && e.message!!.startsWith("Too many follow-up requests:")) {
            return GetText.getString(R.string.error_redirection)
        } else if (e is ProtocolException || e is SocketException || e is SSLException) {
            return GetText.getString(R.string.error_socket)
        } else if (e is EhException) {
            return "" + e.message
        } else {
            if (e.localizedMessage == null) {
                return "" + e.message
            }
            if (e.localizedMessage == e.message) {
                return e.localizedMessage
            }
            return e.localizedMessage + "\n" + e.message
        }
    }

    @JvmStatic
    fun throwIfFatal(t: Throwable) {
        // values here derived from https://github.com/ReactiveX/RxJava/issues/748#issuecomment-32471495
        when (t) {
            is VirtualMachineError -> {
                throw t
            }

            is ThreadDeath -> {
                throw t
            }

            is LinkageError -> {
                throw t
            }
        }
    }
}
