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
package com.hippo.app

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.hippo.ehviewer.R

class CheckBoxDialogBuilder @SuppressLint("InflateParams") constructor(
    context: Context?,
    message: String?,
    checkText: String?,
    checked: Boolean
) : AlertDialog.Builder(
    context!!
) {
    private val mCheckBox: CheckBox

    init {
        val view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_checkbox_builder, null)
        setView(view)
        val messageView = view.findViewById<View>(R.id.message) as TextView
        mCheckBox = view.findViewById<View>(R.id.checkbox) as CheckBox
        messageView.text = message
        mCheckBox.text = checkText
        mCheckBox.isChecked = checked
    }

    val isChecked: Boolean
        get() = mCheckBox.isChecked
}