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
package com.hippo.ehviewer.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhUtils
import com.hippo.ehviewer.ui.splash.SplashActivity
import com.hippo.preference.MessagePreference
import java.lang.RuntimeException

class SignOutPreference : MessagePreference {
    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        setDialogMessage(getContext().getString(R.string.settings_eh_sign_out_warning))
    }

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setPositiveButton(R.string.settings_eh_sign_out_yes, this)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            EhUtils.signOut(context)
            Toast.makeText(context, R.string.settings_eh_sign_out_restart, Toast.LENGTH_SHORT)
                .show()
            Thread(Runnable {
                try {
                    Thread.sleep(1500)
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                }
                val intent = Intent(context, SplashActivity::class.java)
                intent.putExtra(SplashActivity.KEY_RESTART, true)
                context.startActivity(intent)
            }).start()
        }
    }
}
