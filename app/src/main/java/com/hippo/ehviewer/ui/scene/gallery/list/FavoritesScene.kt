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
package com.hippo.ehviewer.ui.scene.gallery.list

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ImageSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.view.get
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.amlcurran.showcaseview.ShowcaseView
import com.github.amlcurran.showcaseview.SimpleShowcaseEventListener
import com.github.amlcurran.showcaseview.targets.PointTarget
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hippo.android.resource.AttrResources
import com.hippo.annotation.Implemented
import com.hippo.app.EditTextDialogBuilder
import com.hippo.drawable.AddDeleteDrawable
import com.hippo.drawable.DrawerArrowDrawable
import com.hippo.easyrecyclerview.EasyRecyclerView
import com.hippo.easyrecyclerview.EasyRecyclerView.CustomChoiceListener
import com.hippo.easyrecyclerview.FastScroller.OnDragHandlerListener
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.FavListUrlBuilder
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.client.parser.FavoritesParser
import com.hippo.ehviewer.ui.CommonOperations
import com.hippo.ehviewer.ui.annotation.DrawerLifeCircle
import com.hippo.ehviewer.ui.annotation.ViewLifeCircle
import com.hippo.ehviewer.ui.annotation.WholeLifeCircle
import com.hippo.ehviewer.ui.dialog.FavoriteListSortDialog
import com.hippo.ehviewer.ui.scene.BaseScene
import com.hippo.ehviewer.ui.scene.EhCallback
import com.hippo.ehviewer.ui.scene.gallery.detail.GalleryDetailScene
import com.hippo.ehviewer.widget.EhDrawerLayout
import com.hippo.ehviewer.widget.GalleryInfoContentHelper
import com.hippo.ehviewer.widget.JumpDateSelector
import com.hippo.ehviewer.widget.JumpDateSelector.OnTimeSelectedListener
import com.hippo.ehviewer.widget.SearchBar
import com.hippo.lib.yorozuya.AssertUtils
import com.hippo.lib.yorozuya.ObjectUtils
import com.hippo.lib.yorozuya.SimpleHandler
import com.hippo.lib.yorozuya.ViewUtils
import com.hippo.ripple.Ripple
import com.hippo.scene.Announcer
import com.hippo.scene.SceneFragment
import com.hippo.util.AppHelper.Companion.hideSoftInput
import com.hippo.util.DrawableManager
import com.hippo.widget.ContentLayout
import com.hippo.widget.FabLayout
import com.hippo.widget.FabLayout.OnClickFabListener
import com.hippo.widget.FabLayout.OnExpandListener
import com.hippo.widget.SearchBarMover
import okhttp3.OkHttpClient
import java.util.concurrent.ExecutorService

// TODO Get favorite, modify favorite, add favorite, what a mess!
class FavoritesScene : BaseScene(), EasyRecyclerView.OnItemClickListener,
    EasyRecyclerView.OnItemLongClickListener, OnDragHandlerListener, SearchBarMover.Helper,
    SearchBar.Helper, OnClickFabListener, OnExpandListener, CustomChoiceListener {
    @ViewLifeCircle
    private var mRecyclerView: EasyRecyclerView? = null

    @ViewLifeCircle
    private var mSearchBar: SearchBar? = null

    @ViewLifeCircle
    private var mFabLayout: FabLayout? = null

    @ViewLifeCircle
    private var mAdapter: FavoritesAdapter? = null

    @ViewLifeCircle
    private var mHelper: FavoritesHelper? = null

    @ViewLifeCircle
    private var mSearchBarMover: SearchBarMover? = null

    @ViewLifeCircle
    private var mLeftDrawable: DrawerArrowDrawable? = null
    private var mActionFabDrawable: AddDeleteDrawable? = null

    private var mDrawerLayout: EhDrawerLayout? = null

    @DrawerLifeCircle
    private var mDrawerAdapter: FavDrawerAdapter? = null

    @WholeLifeCircle
    private var mClient: EhClient? = null

    @WholeLifeCircle
    private var mFavCatArray: Array<String?>? = null

    @WholeLifeCircle
    private var mFavCountArray: IntArray? = null

    @WholeLifeCircle
    private var mUrlBuilder: FavListUrlBuilder? = null

    private var jumpSelectorDialog: AlertDialog? = null
    private var mJumpDateSelector: JumpDateSelector? = null

    var current: Int = 0 // -1 for error
    var limit: Int = 0 // -1 for error

    private var mFavLocalCount = 0
    private var mFavCountSum = 0

    private var mHasFirstRefresh = false
    private var mSearchMode = false

    // Avoid unnecessary search bar update
    private var mOldFavCat: String? = null

    // Avoid unnecessary search bar update
    private var mOldKeyword: String? = null

    // For modify action
    private var mEnableModify = false

    // For modify action
    private var mModifyFavCat = 0

    // For modify action
    private val mModifyGiList: MutableList<GalleryInfo> = ArrayList<GalleryInfo>()

    // For modify action
    private var mModifyAdd = false

    private var mShowcaseView: ShowcaseView? = null

    private var mResult: FavoritesParser.Result? = null

    private var executorService: ExecutorService? = null

    @ViewLifeCircle
    private var sortDialog: FavoriteListSortDialog? = null


    override fun getNavCheckedItem(): Int {
        return R.id.nav_favourite
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = getEHContext()
        AssertUtils.assertNotNull(context)
        executorService = EhApplication.getExecutorService(context!!)
        mClient = EhApplication.getEhClient(context)
        mFavCatArray = Settings.getFavCat()
        mFavCountArray = Settings.getFavCount()
        mFavLocalCount = Settings.getFavLocalCount()
        mFavCountSum = Settings.getFavCloudCount()

        if (savedInstanceState == null) {
            onInit()
        } else {
            onRestore(savedInstanceState)
        }
    }

    private fun onInit() {
        mUrlBuilder = FavListUrlBuilder()
        mUrlBuilder!!.setFavCat(Settings.getRecentFavCat())
        mSearchMode = false
    }

    private fun onRestore(savedInstanceState: Bundle) {
        mUrlBuilder = savedInstanceState.getParcelable<FavListUrlBuilder?>(KEY_URL_BUILDER)
        if (mUrlBuilder == null) {
            mUrlBuilder = FavListUrlBuilder()
        }
        mSearchMode = savedInstanceState.getBoolean(KEY_SEARCH_MODE)
        mHasFirstRefresh = savedInstanceState.getBoolean(KEY_HAS_FIRST_REFRESH)
        mFavCountArray = savedInstanceState.getIntArray(KEY_FAV_COUNT_ARRAY)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val hasFirstRefresh: Boolean
        if (mHelper != null && 1 == mHelper!!.getShownViewIndex()) {
            hasFirstRefresh = false
        } else {
            hasFirstRefresh = mHasFirstRefresh
        }
        outState.putBoolean(KEY_HAS_FIRST_REFRESH, hasFirstRefresh)
        outState.putParcelable(KEY_URL_BUILDER, mUrlBuilder)
        outState.putBoolean(KEY_SEARCH_MODE, mSearchMode)
        outState.putIntArray(KEY_FAV_COUNT_ARRAY, mFavCountArray)
    }

    override fun onDestroy() {
        super.onDestroy()

        mClient = null
        mFavCatArray = null
        mFavCountArray = null
        mFavCountSum = 0
        mUrlBuilder = null
    }

    override fun onCreateView2(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.scene_favorites, container, false)
        val contentLayout = view.findViewById<View?>(R.id.content_layout) as ContentLayout
        val activity = getActivity2()
        AssertUtils.assertNotNull(activity)
        mDrawerLayout = ViewUtils.`$$`(activity, R.id.draw_view) as EhDrawerLayout
        mRecyclerView = contentLayout.getRecyclerView()
        val fastScroller = contentLayout.getFastScroller()
        val refreshLayout = contentLayout.getRefreshLayout()
        mSearchBar = ViewUtils.`$$`(view, R.id.search_bar) as SearchBar
        mFabLayout = ViewUtils.`$$`(view, R.id.fab_layout) as FabLayout

        val context = getEHContext()
        AssertUtils.assertNotNull(context)
        val resources = context!!.getResources()
        val paddingTopSB = resources.getDimensionPixelOffset(R.dimen.gallery_padding_top_search_bar)

        mHelper = FavoritesHelper()
        mHelper!!.setEmptyString(resources.getString(R.string.gallery_list_empty_hit))
        contentLayout.setHelper(mHelper)
        contentLayout.getFastScroller().setOnDragHandlerListener(this)

        mAdapter = FavoritesAdapter(inflater, resources, mRecyclerView!!, Settings.getListMode())
        mRecyclerView!!.setSelector(
            Ripple.generateRippleDrawable(
                context, !AttrResources.getAttrBoolean(
                    context, androidx.appcompat.R.attr.isLightTheme
                ), ColorDrawable(Color.TRANSPARENT)
            )
        )
        mRecyclerView!!.setDrawSelectorOnTop(true)
        mRecyclerView!!.setClipToPadding(false)
        mRecyclerView!!.setOnItemClickListener(this)
        mRecyclerView!!.setOnItemLongClickListener(this)
        mRecyclerView!!.setChoiceMode(EasyRecyclerView.CHOICE_MODE_MULTIPLE_CUSTOM)
        mRecyclerView!!.setCustomCheckedListener(this)

        fastScroller.setPadding(
            fastScroller.getPaddingLeft(), fastScroller.getPaddingTop() + paddingTopSB,
            fastScroller.getPaddingRight(), fastScroller.getPaddingBottom()
        )

        refreshLayout.setHeaderTranslationY(paddingTopSB.toFloat())

        mLeftDrawable = DrawerArrowDrawable(
            context,
            AttrResources.getAttrColor(context, R.attr.drawableColorPrimary)
        )
        mSearchBar!!.setLeftDrawable(mLeftDrawable)
        mSearchBar!!.setRightDrawable(
            DrawableManager.getVectorDrawable(
                context,
                R.drawable.v_magnify_x24
            )
        )
        mSearchBar!!.setHelper(this)
        mSearchBar!!.setAllowEmptySearch(false)
        updateSearchBar()
        mSearchBarMover = SearchBarMover(this, mSearchBar, mRecyclerView)

        mActionFabDrawable =
            AddDeleteDrawable(context, resources.getColor(R.color.primary_drawable_dark))
        mFabLayout!!.getPrimaryFab().setImageDrawable(mActionFabDrawable)
        mFabLayout!!.setExpanded(false, false)
        mFabLayout!!.setAutoCancel(true)
        mFabLayout!!.setHidePrimaryFab(false)
        mFabLayout!!.setOnClickFabListener(this)
        mFabLayout!!.setOnExpandListener(this)
        addAboveSnackView(mFabLayout)

        // Restore search mode
        if (mSearchMode) {
            mSearchMode = false
            enterSearchMode(false)
        }

        // Only refresh for the first time
        if (!mHasFirstRefresh) {
            mHasFirstRefresh = true
            mHelper!!.firstRefresh()
        }

        guideCollections()

        return view
    }

    private fun guideCollections() {
        val activity: Activity? = getActivity2()
        if (null == activity || !Settings.getGuideCollections()) {
            return
        }

        val display = activity.getWindowManager().getDefaultDisplay()
        val point = Point()
        display.getSize(point)

        mShowcaseView = ShowcaseView.Builder(activity)
            .withMaterialShowcase()
            .setStyle(R.style.Guide)
            .setTarget(PointTarget(point.x, point.y / 3))
            .blockAllTouches()
            .setContentTitle(R.string.guide_collections_title)
            .setContentText(R.string.guide_collections_text)
            .replaceEndButton(R.layout.button_guide)
            .setShowcaseEventListener(object : SimpleShowcaseEventListener() {
                override fun onShowcaseViewDidHide(showcaseView: ShowcaseView) {
                    mShowcaseView = null
                    ViewUtils.removeFromParent(showcaseView)
                    Settings.putGuideCollections(false)
                    openDrawer(Gravity.RIGHT)
                }
            }).build()
    }

    // keyword of mUrlBuilder, fav cat of mUrlBuilder, mFavCatArray.
    // They changed, call it
    private fun updateSearchBar() {
        val context = getEHContext()
        if (null == context || null == mUrlBuilder || null == mSearchBar || null == mFavCatArray) {
            return
        }

        // Update title
        val favCat = mUrlBuilder!!.getFavCat()
        val favCatName: String?
        if (favCat >= 0 && favCat < 10) {
            favCatName = mFavCatArray!![favCat]
        } else if (favCat == FavListUrlBuilder.FAV_CAT_LOCAL) {
            favCatName = getString(R.string.local_favorites)
        } else {
            favCatName = getString(R.string.cloud_favorites)
        }
        val keyword = mUrlBuilder!!.getKeyword()
        if (TextUtils.isEmpty(keyword)) {
            if (!ObjectUtils.equal(favCatName, mOldFavCat)) {
                mSearchBar!!.setTitle(getString(R.string.favorites_title, favCatName))
            }
        } else {
            if (!ObjectUtils.equal(favCatName, mOldFavCat) || !ObjectUtils.equal(
                    keyword,
                    mOldKeyword
                )
            ) {
                mSearchBar!!.setTitle(getString(R.string.favorites_title_2, favCatName, keyword))
            }
        }

        // Update hint
        if (!ObjectUtils.equal(favCatName, mOldFavCat)) {
            val searchImage = DrawableManager.getVectorDrawable(context, R.drawable.v_magnify_x24)
            val ssb = SpannableStringBuilder("   ")
            ssb.append(getString(R.string.favorites_search_bar_hint, favCatName))
            val textSize = (mSearchBar!!.getEditTextTextSize() * 1.25).toInt()
            if (searchImage != null) {
                searchImage.setBounds(0, 0, textSize, textSize)
                ssb.setSpan(ImageSpan(searchImage), 1, 2, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            mSearchBar!!.setEditTextHint(ssb)
        }

        mOldFavCat = favCatName
        mOldKeyword = keyword

        // Save recent fav cat
        Settings.putRecentFavCat(mUrlBuilder!!.getFavCat())
    }

    override fun onDestroyView() {
        super.onDestroyView()

        if (null != mShowcaseView) {
            ViewUtils.removeFromParent(mShowcaseView)
            mShowcaseView = null
        }

        if (null != mHelper) {
            mHelper!!.destroy()
            if (1 == mHelper!!.getShownViewIndex()) {
                mHasFirstRefresh = false
            }
        }
        if (null != mRecyclerView) {
            mRecyclerView!!.stopScroll()
            mRecyclerView = null
        }
        if (null != mFabLayout) {
            removeAboveSnackView(mFabLayout)
            mFabLayout = null
        }

        mAdapter = null

        mSearchBar = null

        mSearchBarMover = null
        mLeftDrawable = null

        mOldFavCat = null
        mOldKeyword = null
    }

    override fun onCreateDrawerView(
        inflater: LayoutInflater,
        container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.drawer_list_rv, container, false)
        val context = getEHContext()
        val toolbar = ViewUtils.`$$`(view, R.id.toolbar) as Toolbar

        AssertUtils.assertNotNull(context)

        toolbar.setTitle(R.string.collections)
        toolbar.inflateMenu(R.menu.drawer_favorites)
        toolbar.setOnMenuItemClickListener(object : Toolbar.OnMenuItemClickListener {
            override fun onMenuItemClick(item: MenuItem): Boolean {
                val id = item.getItemId()
                when (id) {
                    R.id.action_default_favorites_slot -> {
                        val items = arrayOfNulls<String>(12)
                        items[0] = getString(R.string.let_me_select)
                        items[1] = getString(R.string.local_favorites)
                        val favCat = Settings.getFavCat()
                        System.arraycopy(favCat, 0, items, 2, 10)
                        AlertDialog.Builder(context!!)
                            .setTitle(R.string.default_favorites_collection)
                            .setItems(items, object : DialogInterface.OnClickListener {
                                override fun onClick(dialog: DialogInterface?, which: Int) {
                                    Settings.putDefaultFavSlot(which - 2)
                                }
                            }).show()
                        return true
                    }
                }
                return false
            }
        })

        val recyclerView = view.findViewById<View?>(R.id.recycler_view_drawer) as EasyRecyclerView
        recyclerView.setLayoutManager(LinearLayoutManager(context))
        recyclerView.addItemDecoration(
            DividerItemDecoration(
                context,
                DividerItemDecoration.VERTICAL
            )
        )

        mDrawerAdapter = FavDrawerAdapter(inflater)
        recyclerView.setAdapter(mDrawerAdapter)
        recyclerView.setOnItemClickListener(this)

        return view
    }

    override fun onDestroyDrawerView() {
        super.onDestroyDrawerView()

        mDrawerAdapter = null
    }

    override fun onBackPressed() {
        if (null != mShowcaseView) {
            return
        }

        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
            mRecyclerView!!.outOfCustomChoiceMode()
        } else if (mFabLayout != null && mFabLayout!!.isExpanded()) {
            mFabLayout!!.toggle()
        } else if (mSearchMode) {
            exitSearchMode(true)
        } else {
            finish()
        }
    }

    @Implemented(OnDragHandlerListener::class)
    override fun onStartDragHandler() {
        // Lock right drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT)
    }

    @Implemented(OnDragHandlerListener::class)
    override fun onEndDragHandler() {
        // Restore right drawer
        if (null != mRecyclerView && !mRecyclerView!!.isInCustomChoice()) {
            setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT)
        }

        if (mSearchBarMover != null) {
            mSearchBarMover!!.returnSearchBarPosition()
        }
    }

    @Implemented(EasyRecyclerView.OnItemClickListener::class)
    override fun onItemClick(
        parent: EasyRecyclerView?,
        view: View,
        position: Int,
        id: Long
    ): Boolean {
        if (mDrawerLayout != null && mDrawerLayout!!.isDrawerOpen(Gravity.RIGHT)) {
            // Skip if in search mode
            if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
                return true
            }

            if (mUrlBuilder == null || mHelper == null) {
                return true
            }

            // Local favorite position is 0, All favorite position is 1, so position - 2 is OK
            val newFavCat = position - 2

            // Check is the same
            if (mUrlBuilder!!.getFavCat() == newFavCat) {
                return true
            }

            // Ensure outOfCustomChoiceMode to avoid error
            if (mRecyclerView != null) {
                mRecyclerView!!.isInCustomChoice()
            }

            exitSearchMode(true)

            mUrlBuilder!!.setKeyword(null)
            mUrlBuilder!!.setFavCat(newFavCat)
            updateSearchBar()
            mHelper!!.refresh()

            closeDrawer(Gravity.RIGHT)
        } else {
            if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
                mRecyclerView!!.toggleItemChecked(position)
            } else if (mHelper != null) {
                val gi = mHelper!!.getDataAtEx(position)
                if (gi == null) {
                    return true
                }
                val args = Bundle()
                args.putString(
                    GalleryDetailScene.KEY_ACTION,
                    GalleryDetailScene.ACTION_GALLERY_INFO
                )
                args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, gi)
                val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
                val thumb: View?
                if (null != (view.findViewById<View?>(R.id.thumb).also { thumb = it })) {
                    announcer.setTranHelper(EnterGalleryDetailTransaction(thumb))
                }
                startScene(announcer)
            }
        }
        return true
    }

    @Implemented(EasyRecyclerView.OnItemLongClickListener::class)
    override fun onItemLongClick(
        parent: EasyRecyclerView?,
        view: View?,
        position: Int,
        id: Long
    ): Boolean {
        // Can not into
        if (mRecyclerView != null && !mSearchMode) {
            if (!mRecyclerView!!.isInCustomChoice()) {
                mRecyclerView!!.intoCustomChoiceMode()
            }
            mRecyclerView!!.toggleItemChecked(position)
        }
        return true
    }

    @Implemented(SearchBarMover.Helper::class)
    override fun isValidView(recyclerView: RecyclerView?): Boolean {
        return recyclerView === mRecyclerView
    }

    @Implemented(SearchBarMover.Helper::class)
    override fun getValidRecyclerView(): RecyclerView? {
        return mRecyclerView
    }

    @Implemented(SearchBarMover.Helper::class)
    override fun forceShowSearchBar(): Boolean {
        return false
    }

    @Implemented(SearchBar.Helper::class)
    override fun onClickTitle() {
        // Skip if in search mode
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
            return
        }

        if (!mSearchMode) {
            enterSearchMode(true)
        }
    }

    @Implemented(SearchBar.Helper::class)
    override fun onClickLeftIcon() {
        // Skip if in search mode
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
            return
        }

        if (mSearchMode) {
            exitSearchMode(true)
        } else {
            toggleDrawer(Gravity.LEFT)
        }
    }

    @Implemented(SearchBar.Helper::class)
    override fun onClickRightIcon() {
        // Skip if in search mode
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
            return
        }

        if (!mSearchMode) {
            enterSearchMode(true)
        } else {
            if (mSearchBar != null) {
                mSearchBar!!.applySearch(false)
            }
        }
    }

    @Implemented(SearchBar.Helper::class)
    override fun onSearchEditTextClick() {
    }

    @Implemented(SearchBar.Helper::class)
    override fun onApplySearch(query: String?) {
        // Skip if in search mode
        if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
            return
        }

        if (mUrlBuilder == null || mHelper == null) {
            return
        }

        // Ensure outOfCustomChoiceMode to avoid error
        if (mRecyclerView != null) {
            mRecyclerView!!.isInCustomChoice()
        }

        exitSearchMode(true)

        mUrlBuilder!!.setKeyword(query)
        updateSearchBar()
        mHelper!!.refresh()
    }

    @Implemented(SearchBar.Helper::class)
    override fun onSearchEditTextBackPressed() {
        onBackPressed()
    }

    override fun onExpand(expanded: Boolean) {
        if (expanded) {
            mActionFabDrawable!!.setDelete(ANIMATE_TIME)
        } else {
            mActionFabDrawable!!.setAdd(ANIMATE_TIME)
        }
    }

    @Implemented(OnClickFabListener::class)
    override fun onClickPrimaryFab(view: FabLayout?, fab: FloatingActionButton?) {
        if (mRecyclerView != null && mFabLayout != null) {
            if (mRecyclerView!!.isInCustomChoice()) {
                mRecyclerView!!.outOfCustomChoiceMode()
            } else {
                mFabLayout!!.toggle()
            }
        }
    }

    private fun showGoToDialog() {
        val context = getEHContext()

        if (null == context || null == mHelper) {
            return
        }

        if (mHelper!!.mPages < 0) {
            showDateJumpDialog(context)
        } else {
            showPageJumpDialog(context)
        }


        //        Context context = getEHContext();
//        if (null == context || null == mHelper) {
//            return;
//        }
    }

    private fun showDateJumpDialog(context: Context) {
        if (mHelper == null) {
            return
        }
        if (mHelper!!.nextHref == null || mHelper!!.nextHref.isEmpty()) {
//            if ((mHelper.nextHref == null || mHelper.nextHref.isEmpty())&&(mHelper.prevHref == null || mHelper.prevHref.isEmpty())) {
            Toast.makeText(getEHContext(), R.string.gallery_list_no_more_data, Toast.LENGTH_LONG)
                .show()
            return
        }
        if (jumpSelectorDialog == null) {
            val linearLayout = getLayoutInflater().inflate(
                R.layout.gallery_list_date_jump_dialog,
                null
            ) as LinearLayout
            mJumpDateSelector =
                linearLayout.findViewById<JumpDateSelector>(R.id.gallery_list_jump_date)
            mJumpDateSelector!!.setOnTimeSelectedListener(OnTimeSelectedListener { urlAppend: String? ->
                this.onTimeSelected(
                    urlAppend!!
                )
            })
            jumpSelectorDialog = AlertDialog.Builder(context).setView(linearLayout).create()
        }
        mJumpDateSelector!!.setFoundMessage(mHelper!!.resultCount)
        jumpSelectorDialog!!.show()
    }

    private fun showPageJumpDialog(context: Context?) {
        val page = mHelper!!.getPageForTop()
        val pages = mHelper!!.getPages()
        val hint = getString(R.string.go_to_hint, page + 1, pages)
        val builder = EditTextDialogBuilder(context, null, hint)
        builder.editText.setInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val dialog = builder.setTitle(R.string.go_to)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            .setOnClickListener(View.OnClickListener { v: View? ->
                if (null == mHelper) {
                    dialog.dismiss()
                    return@OnClickListener
                }
                val text = builder.text.trim { it <= ' ' }
                val goTo: Int
                try {
                    goTo = text.toInt() - 1
                } catch (e: NumberFormatException) {
                    builder.setError(getString(R.string.error_invalid_number))
                    return@OnClickListener
                }
                if (goTo < 0 || goTo >= pages) {
                    builder.setError(getString(R.string.error_out_of_range))
                    return@OnClickListener
                }
                builder.setError(null)
                mHelper!!.goTo(goTo)
                hideSoftInput(dialog)
                dialog.dismiss()
            })
    }

    private fun onTimeSelected(urlAppend: String) {
        Log.d(TAG, urlAppend)
        if (urlAppend.isEmpty() || mHelper == null || jumpSelectorDialog == null || mUrlBuilder == null) {
            return
        }
        jumpSelectorDialog!!.dismiss()
        mHelper!!.nextHref = mUrlBuilder!!.jumpHrefBuild(mHelper!!.nextHref, urlAppend)
        mHelper!!.goTo(-996)
    }

    @Implemented(OnClickFabListener::class)
    override fun onClickSecondaryFab(view: FabLayout, fab: FloatingActionButton?, position: Int) {
        val context = getEHContext()
        if (null == context || null == mRecyclerView || null == mHelper) {
            return
        }

        if (!mRecyclerView!!.isInCustomChoice()) {
            when (position) {
                0 -> if (mHelper!!.canGoTo()) {
                    showGoToDialog()
                }

                1 -> mHelper!!.refresh()
                5 -> {
                    if (mHelper == null || !mHelper!!.canGoTo()) {

                    } else {
                        val mRandomFavorite = RandomFavorite(context)
                        mRandomFavorite.execute()
                    }
                }

                6 -> {
                    val randomFavorite = RandomFavorite()
                    randomFavorite.random()
                }

                7 -> {
                    if (mUrlBuilder != null) {
                        if (sortDialog == null) {
                            sortDialog = FavoriteListSortDialog(this)
                        }
                        if (mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL) {
                            sortDialog!!.showLocalSort(mResult)
                        } else {
                            sortDialog!!.showCloudSort(mResult)
                        }
                    }
                }
            }
            view.setExpanded(false)
            return
        }

        mModifyGiList.clear()
        val stateArray = mRecyclerView!!.getCheckedItemPositions()
        var i = 0
        val n = stateArray.size()
        while (i < n) {
            if (stateArray.valueAt(i)) {
                val gi = mHelper!!.getDataAtEx(stateArray.keyAt(i))
                if (gi != null) {
                    mModifyGiList.add(gi)
                }
            }
            i++
        }

        when (position) {
            2 -> {
                // Download
                val activity: Activity? = getActivity2()
                if (activity != null) {
                    CommonOperations.startDownload(getActivity2(), mModifyGiList, false)
                }
                mModifyGiList.clear()
                if (mRecyclerView != null && mRecyclerView!!.isInCustomChoice()) {
                    mRecyclerView!!.outOfCustomChoiceMode()
                }
            }

            3 -> {
                // Delete
                val helper: DeleteDialogHelper = DeleteDialogHelper()
                AlertDialog.Builder(context)
                    .setTitle(R.string.delete_favorites_dialog_title)
                    .setMessage(
                        getString(
                            R.string.delete_favorites_dialog_message,
                            mModifyGiList.size
                        )
                    )
                    .setPositiveButton(android.R.string.ok, helper)
                    .setOnCancelListener(helper)
                    .show()
            }

            4 -> {
                // Move
                val helper: MoveDialogHelper = MoveDialogHelper()
                // First is local favorite, the other 10 is cloud favorite
                val array = arrayOfNulls<String>(11)
                array[0] = getString(R.string.local_favorites)
                System.arraycopy(Settings.getFavCat(), 0, array, 1, 10)
                AlertDialog.Builder(context)
                    .setTitle(R.string.move_favorites_dialog_title)
                    .setItems(array, helper)
                    .setOnCancelListener(helper)
                    .show()
            }

            8 -> {
                selectAll(view, fab)
            }
        }
    }

    private val showNormalFabsRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mFabLayout != null) {
                mFabLayout!!.setSecondaryFabVisibilityAt(0, true)
                mFabLayout!!.setSecondaryFabVisibilityAt(1, true)
                mFabLayout!!.setSecondaryFabVisibilityAt(2, false)
                mFabLayout!!.setSecondaryFabVisibilityAt(3, false)
                mFabLayout!!.setSecondaryFabVisibilityAt(4, false)
                mFabLayout!!.setSecondaryFabVisibilityAt(5, true)
                mFabLayout!!.setSecondaryFabVisibilityAt(6, true)
                mFabLayout!!.setSecondaryFabVisibilityAt(7, true)
                mFabLayout!!.setSecondaryFabVisibilityAt(8, false)
            }
        }
    }

    private fun showNormalFabs() {
        // Delay showing normal fabs to avoid mutation
        SimpleHandler.getInstance().removeCallbacks(showNormalFabsRunnable)
        SimpleHandler.getInstance().postDelayed(showNormalFabsRunnable, 300)
    }

    private fun showSelectionFabs() {
        SimpleHandler.getInstance().removeCallbacks(showNormalFabsRunnable)

        if (mFabLayout != null) {
            mFabLayout!!.setSecondaryFabVisibilityAt(0, false)
            mFabLayout!!.setSecondaryFabVisibilityAt(1, false)
            mFabLayout!!.setSecondaryFabVisibilityAt(2, true)
            mFabLayout!!.setSecondaryFabVisibilityAt(3, true)
            mFabLayout!!.setSecondaryFabVisibilityAt(4, true)
            mFabLayout!!.setSecondaryFabVisibilityAt(5, false)
            mFabLayout!!.setSecondaryFabVisibilityAt(6, false)
            mFabLayout!!.setSecondaryFabVisibilityAt(7, false)
            mFabLayout!!.setSecondaryFabVisibilityAt(8, true)
        }
    }

    private fun selectAll(view: FabLayout, fab: FloatingActionButton?) {
        if (mRecyclerView != null && !mSearchMode) {
            if (!mRecyclerView!!.isInCustomChoice()) {
                mRecyclerView!!.intoCustomChoiceMode()
            }
        }
        if (checkedAll()) {
            fab?.setImageResource(R.drawable.v_check_dark_x24)
            mRecyclerView!!.outOfCustomChoiceMode()
        } else {
            fab?.setImageResource(R.drawable.v_check_all_dark_x24)
            mRecyclerView!!.checkAll()
        }
    }

    private fun checkedAll(): Boolean {
        if (mRecyclerView == null || mAdapter == null) {
            return false
        }
        return mAdapter!!.getItemCount() == mRecyclerView!!.checkedItemCount
    }

    @Implemented(CustomChoiceListener::class)
    override fun onIntoCustomChoice(view: EasyRecyclerView?) {
        if (mFabLayout != null) {
            showSelectionFabs()
            mFabLayout!!.setAutoCancel(false)
            // Delay expanding action to make layout work fine
            SimpleHandler.getInstance().post(Runnable { mFabLayout!!.setExpanded(true) })
        }
        if (mHelper != null) {
            mHelper!!.setRefreshLayoutEnable(false)
        }
        // Lock drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.LEFT)
        setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, Gravity.RIGHT)
    }

    @Implemented(CustomChoiceListener::class)
    override fun onOutOfCustomChoice(view: EasyRecyclerView?) {
        if (mFabLayout != null) {
            showNormalFabs()
            mFabLayout!!.setAutoCancel(true)
            mFabLayout!!.setExpanded(false)
            val fabB = mFabLayout!!.get(8) as FloatingActionButton
            fabB.setImageResource(R.drawable.v_check_dark_x24)
        }
        if (mHelper != null) {
            mHelper!!.setRefreshLayoutEnable(true)
        }

        // Unlock drawer
        setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.LEFT)
        setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Gravity.RIGHT)
    }

    @Implemented(CustomChoiceListener::class)
    override fun onItemCheckedStateChanged(
        view: EasyRecyclerView,
        position: Int,
        id: Long,
        checked: Boolean
    ) {
        if (view.getCheckedItemCount() == 0) {
            view.outOfCustomChoiceMode()
        } else {
            if (mAdapter != null && view.getCheckedItemCount() < mAdapter!!.getItemCount()) {
                val fabB = mFabLayout!!.get(8) as FloatingActionButton
                fabB.setImageResource(R.drawable.v_check_dark_x24)
            }else{
                val fabB = mFabLayout!!.get(8) as FloatingActionButton
                fabB.setImageResource(R.drawable.v_check_all_dark_x24)
            }
        }
    }

    private fun enterSearchMode(animation: Boolean) {
        if (mSearchMode || mSearchBar == null || mSearchBarMover == null || mLeftDrawable == null) {
            return
        }
        mSearchMode = true
        mSearchBar!!.setState(SearchBar.STATE_SEARCH_LIST, animation)
        mSearchBarMover!!.returnSearchBarPosition(animation)
        mLeftDrawable!!.setArrow(ANIMATE_TIME)
    }

    private fun exitSearchMode(animation: Boolean) {
        if (!mSearchMode || mSearchBar == null || mSearchBarMover == null || mLeftDrawable == null) {
            return
        }
        mSearchMode = false
        mSearchBar!!.setState(SearchBar.STATE_NORMAL, animation)
        mSearchBarMover!!.returnSearchBarPosition()
        mLeftDrawable!!.setMenu(ANIMATE_TIME)
    }

    private fun onGetFavoritesSuccess(result: FavoritesParser.Result, taskId: Int) {
        mResult = result
        if (mHelper != null && mSearchBarMover != null &&
            mHelper!!.isCurrentTask(taskId)
        ) {
            if (mFavCatArray != null && mFavCatArray != null) {
                System.arraycopy(result.catArray, 0, mFavCatArray!!, 0, 10)
            }

            mFavCountArray = result.countArray
            if (mFavCountArray != null) {
                mFavCountSum = 0
                for (i in 0..9) {
                    mFavCountSum = mFavCountSum + mFavCountArray!![i]
                }
                Settings.putFavCloudCount(mFavCountSum)
            }

            updateSearchBar()
            //            mHelper.onGetPageData(taskId, result.pages, result.nextPage, result.galleryInfoList);
            mHelper!!.onGetPageData(taskId, result, result.galleryInfoList)

            if (mDrawerAdapter != null) {
                mDrawerAdapter!!.notifyDataSetChanged()
            }
        }
    }

    private fun onGetFavoritesFailure(e: Exception?, taskId: Int) {
        if (mHelper != null && mSearchBarMover != null &&
            mHelper!!.isCurrentTask(taskId)
        ) {
            mHelper!!.onGetException(taskId, e)
        }
    }

    private fun onGetFavoritesLocal(keyword: String?, taskId: Int) {
        if (mHelper != null && mHelper!!.isCurrentTask(taskId)) {
            val list: MutableList<GalleryInfo?>?
            if (TextUtils.isEmpty(keyword)) {
                list = EhDB.getAllLocalFavorites()
            } else {
                list = EhDB.searchLocalFavorites(keyword)
            }

            if (list.size == 0) {
                mHelper!!.onGetPageData(taskId, 0, 0, ArrayList())
            } else {
                mHelper!!.onGetPageData(taskId, 1, 0, list)
            }

            if (TextUtils.isEmpty(keyword)) {
                mFavLocalCount = list.size
                Settings.putFavLocalCount(mFavLocalCount)
                if (mDrawerAdapter != null) {
                    mDrawerAdapter!!.notifyDataSetChanged()
                }
            }
        }
    }

    fun updateSort(sort: String?) {
        if (null == mHelper) {
            return
        }
        mHelper!!.refreshSort(sort)
    }

    private inner class DeleteDialogHelper : DialogInterface.OnClickListener,
        DialogInterface.OnCancelListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (which != DialogInterface.BUTTON_POSITIVE) {
                return
            }
            if (mRecyclerView == null || mHelper == null || mUrlBuilder == null) {
                return
            }

            mRecyclerView!!.outOfCustomChoiceMode()

            if (mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL) { // Delete local fav
                val gidArray = LongArray(mModifyGiList.size)
                var i = 0
                val n = mModifyGiList.size
                while (i < n) {
                    gidArray[i] = mModifyGiList.get(i).gid
                    i++
                }
                EhDB.removeLocalFavorites(gidArray)
                mModifyGiList.clear()
                mHelper!!.refresh()
            } else { // Delete cloud fav
                mEnableModify = true
                mModifyFavCat = -1
                mModifyAdd = false
                mHelper!!.refresh()
            }
        }

        override fun onCancel(dialog: DialogInterface?) {
            mModifyGiList.clear()
        }
    }

    private inner class MoveDialogHelper : DialogInterface.OnClickListener,
        DialogInterface.OnCancelListener {
        override fun onClick(dialog: DialogInterface?, which: Int) {
            if (mRecyclerView == null || mHelper == null || mUrlBuilder == null) {
                return
            }
            val srcCat = mUrlBuilder!!.getFavCat()
            val dstCat: Int
            if (which == 0) {
                dstCat = FavListUrlBuilder.FAV_CAT_LOCAL
            } else {
                dstCat = which - 1
            }
            if (srcCat == dstCat) {
                return
            }

            mRecyclerView!!.outOfCustomChoiceMode()

            if (srcCat == FavListUrlBuilder.FAV_CAT_LOCAL) { // Move from local to cloud
                val gidArray = LongArray(mModifyGiList.size)
                var i = 0
                val n = mModifyGiList.size
                while (i < n) {
                    gidArray[i] = mModifyGiList.get(i).gid
                    i++
                }
                EhDB.removeLocalFavorites(gidArray)
                mEnableModify = true
                mModifyFavCat = dstCat
                mModifyAdd = true
                mHelper!!.refresh()
            } else if (dstCat == FavListUrlBuilder.FAV_CAT_LOCAL) { // Move from cloud to local
                EhDB.putLocalFavorites(mModifyGiList)
                mEnableModify = true
                mModifyFavCat = -1
                mModifyAdd = false
                mHelper!!.refresh()
            } else {
                mEnableModify = true
                mModifyFavCat = dstCat
                mModifyAdd = false
                mHelper!!.refresh()
            }
        }

        override fun onCancel(dialog: DialogInterface?) {
            mModifyGiList.clear()
        }
    }

    private inner class FavoritesAdapter(
        inflater: LayoutInflater, resources: Resources,
        recyclerView: RecyclerView, type: Int
    ) : GalleryAdapter(inflater, resources, recyclerView, type, false, executorService) {
        override fun getItemCount(): Int {
            return if (null != mHelper) mHelper!!.size() else 0
        }

        override fun getDataAt(position: Int): GalleryInfo? {
            return if (null != mHelper) mHelper!!.getDataAtEx(position) else null
        }
    }

    private inner class FavoritesHelper : GalleryInfoContentHelper() {
        override fun getPageData(taskId: Int, type: Int, page: Int) {
            val activity = getActivity2()
            if (null == activity || null == mUrlBuilder || null == mClient) {
                return
            }

            if (mEnableModify) {
                mEnableModify = false

                val local = mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL

                if (mModifyAdd) {
                    val gidArray = LongArray(mModifyGiList.size)
                    val tokenArray = arrayOfNulls<String>(mModifyGiList.size)
                    var i = 0
                    val n = mModifyGiList.size
                    while (i < n) {
                        val gi = mModifyGiList.get(i)
                        gidArray[i] = gi.gid
                        tokenArray[i] = gi.token
                        i++
                    }
                    val modifyGiListBackup: MutableList<GalleryInfo> =
                        ArrayList<GalleryInfo>(mModifyGiList)
                    mModifyGiList.clear()

                    val request = EhRequest()
                    request.setMethod(EhClient.METHOD_ADD_FAVORITES_RANGE)
                    request.setCallback(
                        AddFavoritesListener(
                            getContext()!!,
                            activity.getStageId(), getTag(),
                            taskId, mUrlBuilder!!.getKeyword(), modifyGiListBackup
                        )
                    )
                    request.setArgs(gidArray, tokenArray, mModifyFavCat)
                    mClient!!.execute(request)
                } else {
                    val gidArray = LongArray(mModifyGiList.size)
                    var i = 0
                    val n = mModifyGiList.size
                    while (i < n) {
                        gidArray[i] = mModifyGiList.get(i).gid
                        i++
                    }
                    mModifyGiList.clear()

                    val url: String?
                    if (local) {
                        // Local fav is shown now, but operation need be done for cloud fav
                        url = EhUrl.getFavoritesUrl()
                    } else {
                        url = mUrlBuilder!!.build()
                    }

                    mUrlBuilder!!.setIndex(page)
                    val request = EhRequest()
                    request.setMethod(EhClient.METHOD_MODIFY_FAVORITES)
                    request.setCallback(
                        GetFavoritesListener(
                            getContext()!!,
                            activity.getStageId(), getTag(),
                            taskId, local, mUrlBuilder!!.getKeyword()
                        )
                    )
                    request.setArgs(url, gidArray, mModifyFavCat, Settings.getShowJpnTitle())
                    mClient!!.execute(request)
                }
            } else if (mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL) {
                val keyword = mUrlBuilder!!.getKeyword()
                SimpleHandler.getInstance().post(Runnable { onGetFavoritesLocal(keyword, taskId) })
            } else {
                mUrlBuilder!!.setIndex(page)
                val url = mUrlBuilder!!.build()
                val request = EhRequest()
                request.setMethod(EhClient.METHOD_GET_FAVORITES)
                request.setCallback(
                    GetFavoritesListener(
                        getContext()!!,
                        activity.getStageId(), getTag(),
                        taskId, false, mUrlBuilder!!.getKeyword()
                    )
                )
                request.setArgs(url, Settings.getShowJpnTitle())
                mClient!!.execute(request)
            }
        }

        override fun getPageData(taskId: Int, type: Int, page: Int, append: String?) {
            val activity = getActivity2()
            if (null == activity || null == mUrlBuilder || null == mClient) {
                return
            }
            mUrlBuilder!!.setIndex(page)
            var url = mUrlBuilder!!.build()
            if (url.contains("?")) {
                url = url + "&" + append
            } else {
                url = url + "?" + append
            }
            val request = EhRequest()
            request.setMethod(EhClient.METHOD_GET_FAVORITES)
            request.setCallback(
                GetFavoritesListener(
                    getContext()!!,
                    activity.getStageId(), getTag(),
                    taskId, false, mUrlBuilder!!.getKeyword()
                )
            )
            request.setArgs(url, Settings.getShowJpnTitle())
            mClient!!.execute(request)
        }

        override fun getExPageData(pageAction: Int, taskId: Int, page: Int) {
            val activity = getActivity2()
            if (null == activity || null == mUrlBuilder || null == mClient) {
                return
            }

            if (mEnableModify) {
                mEnableModify = false

                val local = mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL

                val gidArray = LongArray(mModifyGiList.size)
                if (mModifyAdd) {
                    val tokenArray = arrayOfNulls<String>(mModifyGiList.size)
                    var i = 0
                    val n = mModifyGiList.size
                    while (i < n) {
                        val gi = mModifyGiList.get(i)
                        gidArray[i] = gi.gid
                        tokenArray[i] = gi.token
                        i++
                    }
                    val modifyGiListBackup: MutableList<GalleryInfo> =
                        ArrayList<GalleryInfo>(mModifyGiList)
                    mModifyGiList.clear()

                    val request = EhRequest()
                    request.setMethod(EhClient.METHOD_ADD_FAVORITES_RANGE)
                    request.setCallback(
                        AddFavoritesListener(
                            getContext()!!,
                            activity.getStageId(), getTag(),
                            taskId, mUrlBuilder!!.getKeyword(), modifyGiListBackup
                        )
                    )
                    request.setArgs(gidArray, tokenArray, mModifyFavCat)
                    mClient!!.execute(request)
                } else {
                    var i = 0
                    val n = mModifyGiList.size
                    while (i < n) {
                        gidArray[i] = mModifyGiList.get(i).gid
                        i++
                    }
                    mModifyGiList.clear()

                    val url: String?
                    if (local) {
                        // Local fav is shown now, but operation need be done for cloud fav
                        url = EhUrl.getFavoritesUrl()
                    } else {
//                        url = mUrlBuilder.build();
                        url = mResult!!.nextHref
                    }

                    mUrlBuilder!!.setIndex(page)
                    val request = EhRequest()
                    request.setMethod(EhClient.METHOD_MODIFY_FAVORITES)
                    request.setCallback(
                        GetFavoritesListener(
                            getContext()!!,
                            activity.getStageId(), getTag(),
                            taskId, local, mUrlBuilder!!.getKeyword()
                        )
                    )
                    request.setArgs(url, gidArray, mModifyFavCat, Settings.getShowJpnTitle())
                    mClient!!.execute(request)
                }
            } else if (mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL) {
                val keyword = mUrlBuilder!!.getKeyword()
                SimpleHandler.getInstance().post(object : Runnable {
                    override fun run() {
                        onGetFavoritesLocal(keyword, taskId)
                    }
                })
            } else {
//                String url = mUrlBuilder.build();
//                String url = mResult.nextHref;
                val url = mUrlBuilder!!.build(pageAction, mHelper)
                if (url == null || url.isEmpty()) {
                    Toast.makeText(
                        getContext(),
                        R.string.gallery_list_action_url_missed,
                        Toast.LENGTH_LONG
                    ).show()
                    if (mHelper != null) {
                        mHelper!!.cancelCurrentTask()
                    }
                    return
                }
                mUrlBuilder!!.setIndex(page)
                val request = EhRequest()
                request.setMethod(EhClient.METHOD_GET_FAVORITES)
                request.setCallback(
                    GetFavoritesListener(
                        getContext()!!,
                        activity.getStageId(), getTag(),
                        taskId, false, mUrlBuilder!!.getKeyword()
                    )
                )
                request.setArgs(url, Settings.getShowJpnTitle())
                mClient!!.execute(request)
            }
        }

        override fun getContext(): Context? {
            return this@FavoritesScene.getEHContext()
        }

        override fun notifyDataSetChanged() {
            // Ensure outOfCustomChoiceMode to avoid error
            if (mRecyclerView != null) {
                mRecyclerView!!.outOfCustomChoiceMode()
            }

            if (mAdapter != null) {
                mAdapter!!.notifyDataSetChanged()
            }
        }

        override fun notifyItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (mAdapter != null) {
                mAdapter!!.notifyItemRangeRemoved(positionStart, itemCount)
            }
        }

        override fun notifyItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (mAdapter != null) {
                mAdapter!!.notifyItemRangeInserted(positionStart, itemCount)
            }
        }

        override fun onShowView(hiddenView: View?, shownView: View?) {
            if (null != mSearchBarMover) {
                mSearchBarMover!!.showSearchBar()
            }
        }

        override fun isDuplicate(d1: GalleryInfo, d2: GalleryInfo): Boolean {
            return d1.gid == d2.gid
        }

        override fun onScrollToPosition(postion: Int) {
            if (0 == postion) {
                if (null != mSearchBarMover) {
                    mSearchBarMover!!.showSearchBar()
                }
            }
        }
    }

    private class AddFavoritesListener(
        context: Context,
        stageId: Int,
        sceneTag: String?,
        private val mTaskId: Int,
        private val mKeyword: String?,
        private val mBackup: MutableList<GalleryInfo>
    ) : EhCallback<FavoritesScene?, Void?>(context, stageId, sceneTag) {
        override fun onSuccess(result: Void?) {
            val scene = getScene()
            if (scene != null) {
                scene.onGetFavoritesLocal(mKeyword, mTaskId)
            }
        }

        override fun onFailure(e: Exception?) {
            // TODO It's a failure, add all of backup back to db.
            // But how to known which one is failed?
            EhDB.putLocalFavorites(mBackup)

            val scene = getScene()
            if (scene != null) {
                scene.onGetFavoritesLocal(mKeyword, mTaskId)
            }
        }

        override fun onCancel() {
        }

        override fun isInstance(scene: SceneFragment?): Boolean {
            return scene is FavoritesScene
        }
    }

    private class GetFavoritesListener(
        context: Context,
        stageId: Int,
        sceneTag: String?,
        private val mTaskId: Int, // Local fav is shown now, but operation need be done for cloud fav
        private val mLocal: Boolean,
        private val mKeyword: String?
    ) : EhCallback<FavoritesScene?, FavoritesParser.Result?>(context, stageId, sceneTag) {
        override fun onSuccess(result: FavoritesParser.Result?) {
            // Put fav cat
            Settings.putFavCat(result?.catArray)
            Settings.putFavCount(result?.countArray)
            val scene = getScene()
            if (scene != null) {
                if (mLocal) {
                    scene.onGetFavoritesLocal(mKeyword, mTaskId)
                } else {
                    scene.onGetFavoritesSuccess(result!!, mTaskId)
                }
            }
        }

        override fun onFailure(e: Exception) {
            val scene = getScene()
            if (scene != null) {
                if (mLocal) {
                    e.printStackTrace()
                    scene.onGetFavoritesLocal(mKeyword, mTaskId)
                } else {
                    scene.onGetFavoritesFailure(e, mTaskId)
                }
            }
        }

        override fun onCancel() {
        }

        override fun isInstance(scene: SceneFragment?): Boolean {
            return scene is FavoritesScene
        }
    }

    private inner class RandomFavorite : AsyncTask<Void?, Void?, GalleryInfo?> {
        var mOkHttpClient: OkHttpClient? = null

        internal constructor(context: Context) {
            mOkHttpClient = EhApplication.getOkHttpClient(context)
        }

        constructor()

        override fun doInBackground(vararg v: Void?): GalleryInfo? {
            publishProgress()
            if (mUrlBuilder == null) {
                return GalleryInfo()
            }
            // local favorities
            if (mUrlBuilder!!.getFavCat() == FavListUrlBuilder.FAV_CAT_LOCAL) {
                val keyword = mUrlBuilder!!.getKeyword()
                val gInfoL: MutableList<GalleryInfo?>?
                if (TextUtils.isEmpty(keyword)) {
                    gInfoL = EhDB.getAllLocalFavorites()
                } else {
                    gInfoL = EhDB.searchLocalFavorites(keyword)
                }
                return gInfoL.get((Math.random() * gInfoL.size).toInt())
            }
            // cloud favorities
            try {
                val lastGInfo: GalleryInfo?
                val firstGInfo: GalleryInfo?
                val url: String?

                if ((mHelper!!.lastHref == null || mHelper!!.lastHref.isEmpty()) && (mHelper!!.firstHref == null || mHelper!!.firstHref.isEmpty())) { // only one page
                    if (mHelper!!.getDataAtEx(0) == null) return null // no gallery

                    return mHelper!!.getDataAtEx((Math.random() * mHelper!!.size()).toInt())
                } else if (mHelper!!.lastHref == null || mHelper!!.lastHref.isEmpty()) { //many pages but user at the last page
                    lastGInfo = mHelper!!.getDataAtEx(mHelper!!.size() - 1)
                    firstGInfo = EhEngine.getAllFavorites(
                        mOkHttpClient,
                        mHelper!!.firstHref
                    ).galleryInfoList.get(0)
                    url = mHelper!!.firstHref
                } else if (mHelper!!.firstHref == null || mHelper!!.firstHref.isEmpty()) { //many pages but user at the first page
                    val gInfoL =
                        EhEngine.getAllFavorites(mOkHttpClient, mHelper!!.lastHref).galleryInfoList
                    lastGInfo = gInfoL.get(gInfoL.size - 1)
                    firstGInfo = mHelper!!.getDataAtEx(0)
                    url = mHelper!!.lastHref.replace("&prev=1", "")
                } else { // many pages
                    val gInfoL =
                        EhEngine.getAllFavorites(mOkHttpClient, mHelper!!.lastHref).galleryInfoList
                    lastGInfo = gInfoL.get(gInfoL.size - 1)
                    firstGInfo = EhEngine.getAllFavorites(
                        mOkHttpClient,
                        mHelper!!.firstHref
                    ).galleryInfoList.get(0)
                    url = mHelper!!.lastHref.replace("&prev=1", "")
                }

                val gidDiff = firstGInfo!!.gid - lastGInfo!!.gid
                val block = gidDiff / 50
                val rBlock = (((Math.random() * block).toInt()) + 1).toLong()
                val rGInfoL = EhEngine.getAllFavorites(
                    mOkHttpClient,
                    url + "&next=" + (firstGInfo.gid - gidDiff / block * ((Math.random() * block).toInt()) + 1)
                ).galleryInfoList
                return rGInfoL.get((Math.random() * rGInfoL.size).toInt())
            } catch (e: Throwable) {
                throw RuntimeException(e)
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onProgressUpdate(vararg values: Void?) {
            mHelper!!.showProgressBar(true)
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(info: GalleryInfo?) {
            super.onPostExecute(info)

            //抄onItemClick(EasyRecyclerView parent, View view, int position, long id)的跳轉功能
            if (info == null) return
            val args = Bundle()
            args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO)
            args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, info)
            val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
            /*View thumb;
            if (null != (thumb = view.findViewById(R.id.thumb))) {
                announcer.setTranHelper(new EnterGalleryDetailTransaction(thumb));
            }*/
            startScene(announcer)
        }

        fun random() {
            if (mHelper == null) {
                return
            }
            val gInfoL = mHelper!!.getData()
            if (gInfoL == null || gInfoL.isEmpty()) {
                return
            }

            onPostExecute(gInfoL.get((Math.random() * gInfoL.size).toInt()))
        }
    }

    private inner class FavDrawerHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        internal val key: TextView
        internal val value: TextView

        init {
            key = ViewUtils.`$$`(itemView, R.id.key) as TextView
            value = ViewUtils.`$$`(itemView, R.id.value) as TextView
        }
    }

    private inner class FavDrawerAdapter(private val mInflater: LayoutInflater) :
        RecyclerView.Adapter<FavDrawerHolder?>() {
        override fun getItemViewType(position: Int): Int {
            return position
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavDrawerHolder {
            return FavDrawerHolder(mInflater.inflate(R.layout.item_drawer_favorites, parent, false))
        }

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: FavDrawerHolder, position: Int) {
            if (0 == position) {
                holder.key.setText(R.string.local_favorites)
                holder.value.setText(mFavLocalCount.toString())
                holder.itemView.setEnabled(true)
            } else if (1 == position) {
                holder.key.setText(R.string.cloud_favorites)
                holder.value.setText(mFavCountSum.toString())
                holder.itemView.setEnabled(true)
            } else {
                if (null == mFavCatArray || null == mFavCountArray || mFavCatArray!!.size < (position - 1) || mFavCountArray!!.size < (position - 1)) {
                    return
                }
                holder.key.setText(mFavCatArray!![position - 2])
                holder.value.setText(mFavCountArray!![position - 2].toString())
                holder.itemView.setEnabled(true)
            }
        }

        override fun getItemCount(): Int {
            if (null == mFavCatArray) {
                return 2
            }
            return 12
        }
    }

    companion object {
        private const val ANIMATE_TIME = 300L

        private const val KEY_URL_BUILDER = "url_builder"
        private const val KEY_SEARCH_MODE = "search_mode"
        private const val KEY_HAS_FIRST_REFRESH = "has_first_refresh"
        private const val KEY_FAV_COUNT_ARRAY = "fav_count_array"
        private const val TAG = "FavoritesScene"
    }
}
