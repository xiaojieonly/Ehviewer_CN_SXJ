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

package com.hippo.ehviewer.ui.scene;

import android.content.Context;
import android.view.View;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.transition.TransitionInflater;
import com.hippo.ehviewer.R;
import com.hippo.scene.TransitionHelper;

class EnterGalleryDetailTransaction implements TransitionHelper {

    private final View mThumb;

    public EnterGalleryDetailTransaction(View thumb) {
        mThumb = thumb;
    }

    @Override
    public boolean onTransition(Context context, FragmentTransaction transaction,
            Fragment exit, Fragment enter) {
        if (mThumb == null || !(enter instanceof GalleryDetailScene)) {
            return false;
        }

        String transitionName = ViewCompat.getTransitionName(mThumb);
        if (transitionName != null) {
            exit.setSharedElementReturnTransition(
                    TransitionInflater.from(context).inflateTransition(R.transition.trans_move));
            exit.setExitTransition(
                    TransitionInflater.from(context).inflateTransition(R.transition.trans_fade));
            enter.setSharedElementEnterTransition(
                    TransitionInflater.from(context).inflateTransition(R.transition.trans_move));
            enter.setEnterTransition(
                    TransitionInflater.from(context).inflateTransition(R.transition.trans_fade));
            transaction.addSharedElement(mThumb, transitionName);
        }
        return true;
    }
}
