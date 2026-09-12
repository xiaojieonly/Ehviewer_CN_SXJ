/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import com.hippo.ehviewer.R

class SelectItemWithIconAdapter(context: Context, texts: Array<CharSequence?>, icons: IntArray) :
    BaseAdapter() {
    private val context: Context
    private val inflater: LayoutInflater

    private val texts: Array<CharSequence?>
    private val icons: IntArray

    init {
        val count = texts.size
        require(count == icons.size) { "Length conflict" }
        this.context = context
        this.inflater = LayoutInflater.from(context)
        this.texts = texts
        this.icons = icons
    }

    override fun getCount(): Int {
        return texts.size
    }

    override fun getItem(position: Int): Any? {
        return texts[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var convertView = convertView
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.dialog_item_select_with_icon, parent, false)
        }
        val view = convertView as TextView

        view.text = texts[position]

        val icon = AppCompatResources.getDrawable(context, icons[position])
        icon!!.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
        view.setCompoundDrawables(icon, null, null, null)

        return view
    }
}
