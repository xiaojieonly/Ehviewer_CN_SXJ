/*
 * Copyright 2018 Hippo Seven
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

import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListAdapter
import android.widget.TextView
import com.hippo.ehviewer.R

abstract class PrettyPreferenceActivity : AppCompatPreferenceActivity() {
    @Deprecated("Deprecated in Java")
    override fun setListAdapter(adapter: ListAdapter) {
        if (adapter == null) {
            super.setListAdapter(null)
            return
        }
        val count = adapter.count
        val headers: MutableList<Header> = ArrayList(count)
        for (i in 0 until count) {
            headers.add(adapter.getItem(i) as Header)
        }
        super.setListAdapter(HeaderAdapter(this, headers, R.layout.item_preference_header, true))
    }

    private class HeaderAdapter constructor(
        context: Context, objects: List<Header>, layoutResId: Int,
        removeIconBehavior: Boolean
    ) : ArrayAdapter<Header?>(context, 0, objects) {
        private class HeaderViewHolder {
            var icon: ImageView? = null
            var title: TextView? = null
            var summary: TextView? = null
        }

        private val mInflater: LayoutInflater
        private val mLayoutResId: Int
        private val mRemoveIconIfEmpty: Boolean

        init {
            mInflater = context.getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
            mLayoutResId = layoutResId
            mRemoveIconIfEmpty = removeIconBehavior
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val holder: HeaderViewHolder
            val view: View
            if (convertView == null) {
                view = mInflater.inflate(mLayoutResId, parent, false)
                holder = HeaderViewHolder()
                holder.icon = view.findViewById(android.R.id.icon)
                holder.title = view.findViewById(android.R.id.title)
                holder.summary = view.findViewById(android.R.id.summary)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as HeaderViewHolder
            }

            // All view fields must be updated every time, because the view may be recycled
            val header = getItem(position)
            if (mRemoveIconIfEmpty) {
                if (header!!.iconRes == 0) {
                    holder.icon!!.visibility = View.GONE
                } else {
                    holder.icon!!.visibility = View.VISIBLE
                    holder.icon!!.setImageResource(header.iconRes)
                }
            } else {
                holder.icon!!.setImageResource(header!!.iconRes)
            }
            holder.title!!.text = header.getTitle(context.resources)
            val summary = header.getSummary(context.resources)
            if (!TextUtils.isEmpty(summary)) {
                holder.summary!!.visibility = View.VISIBLE
                holder.summary!!.text = summary
            } else {
                holder.summary!!.visibility = View.GONE
            }
            return view
        }
    }
}