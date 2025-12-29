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
package com.hippo.ehviewer.client

import android.os.Bundle
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryListUrlParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.ui.scene.ProgressScene
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene
import com.hippo.ehviewer.ui.scene.gallery.list.GalleryListScene
import com.hippo.scene.Announcer

object EhUrlOpener {
    private val TAG: String = EhUrlOpener::class.java.getSimpleName()

    @JvmStatic
    fun parseUrl(url: String?): Announcer? {
        if (TextUtils.isEmpty(url)) {
            return null
        }
        var listUrlBuilder: Parcelable? = null
        try{
            listUrlBuilder = GalleryListUrlParser.parse(url)
        }catch (e: Exception){
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        if (listUrlBuilder != null) {
            val args = Bundle()
            args.putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_LIST_URL_BUILDER)
            args.putParcelable(GalleryListScene.KEY_LIST_URL_BUILDER, listUrlBuilder)
            return Announcer(GalleryListScene::class.java).setArgs(args)
        }

        val result1 = GalleryDetailUrlParser.parse(url)
        if (result1 != null) {
            val args = Bundle()
            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN)
            args.putLong(GalleryDetailScene.KEY_GID, result1.gid)
            args.putString(GalleryDetailScene.KEY_TOKEN, result1.token)
            return Announcer(GalleryDetailScene::class.java).setArgs(args)
        }

        val result2 = GalleryPageUrlParser.parse(url)
        if (result2 != null) {
            val args = Bundle()
            args.putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN)
            args.putLong(ProgressScene.KEY_GID, result2.gid)
            args.putString(ProgressScene.KEY_PTOKEN, result2.pToken)
            args.putInt(ProgressScene.KEY_PAGE, result2.page)
            return Announcer(ProgressScene::class.java).setArgs(args)
        }

        Log.i(TAG, "Can't parse url: " + url)

        return null
    }
}
