package com.hippo.anotherviewer.ui.scene.topList

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.FrameLayout
import android.widget.Spinner
import androidx.annotation.IntDef
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hippo.anotherviewer.SiteApplication
import com.hippo.anotherviewer.R
import com.hippo.anotherviewer.client.SiteClient
import com.hippo.anotherviewer.client.SiteRequest
import com.hippo.anotherviewer.client.SiteUrl
import com.hippo.anotherviewer.client.data.SiteTopListDetail
import com.hippo.anotherviewer.client.data.ListUrlBuilder
import com.hippo.anotherviewer.client.data.topList.TopListInfo
import com.hippo.anotherviewer.client.data.topList.TopListItem
import com.hippo.anotherviewer.client.exception.SiteException
import com.hippo.anotherviewer.ui.scene.BaseScene
import com.hippo.anotherviewer.ui.scene.SiteCallback
import com.hippo.anotherviewer.ui.scene.ProgressScene
import com.hippo.anotherviewer.ui.scene.gallery.detail.GalleryDetailScene
import com.hippo.anotherviewer.ui.scene.gallery.list.GalleryListScene
import com.hippo.anotherviewer.util.ClipboardUtil.createAnnouncerFromClipboardUrl
import com.hippo.scene.Announcer
import com.hippo.scene.SceneFragment
import com.hippo.view.ViewTransition
import java.util.Random

private const val STATE_INIT = -1
private const val STATE_NORMAL = 0
private const val STATE_REFRESH = 1
private const val STATE_REFRESH_HEADER = 2
private const val STATE_FAILED = 3
private const val BACK_PRESSED_INTERVAL = 2000
private const val TRANSITION_ANIMATION_DISABLED = true

private var mPosition = 0

class SiteTopListScene : BaseScene() {

    @IntDef(STATE_INIT, STATE_NORMAL, STATE_REFRESH, STATE_REFRESH_HEADER, STATE_FAILED)
    @Retention(AnnotationRetention.SOURCE)
    private annotation class State

    private var pressBackTime = 0L

    @State
    private var state = STATE_INIT

    private var ehTopListDetail: SiteTopListDetail? = null
    private var viewTransition: ViewTransition? = null
    private var recyclerView: RecyclerView? = null
    private var client: SiteClient? = null
    private var request: SiteRequest? = null
    private var hasFirstRefresh = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ehContext = ehContext ?: return
        client = SiteApplication.getSiteClient(ehContext)
    }

    override fun onCreateView2(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.scene_gallery_top_list, container, false)

        val spinner = view.findViewById<Spinner>(R.id.top_list_spinner)
        spinner.setSelection(0)
        spinner.onItemSelectedListener = TopListKindSelectedListener()

        val frameLayout = view.findViewById<FrameLayout>(R.id.page_detail_view)
        val transitionView = view.findViewById<View>(R.id.data_loading_view)
        viewTransition = ViewTransition(transitionView, frameLayout)

        recyclerView = view.findViewById(R.id.top_list_recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(ehContext)

        if (!hasFirstRefresh) {
            hasFirstRefresh = true
            try {
                loadData()
            } catch (e: SiteException) {
                e.printStackTrace()
            }
        } else {
            bindViewSecond(mPosition)
            adjustViewVisibility(STATE_NORMAL, true)
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewTransition = null
    }

    override fun onBackPressed() {
        val handle = checkDoubleClickExit()
        if (!handle) {
            if (state == STATE_INIT) {
                request?.cancel()
            }
            finish()
        }
    }

    private fun checkDoubleClickExit(): Boolean {
        if (stackIndex != 0) {
            return false
        }

        val time = System.currentTimeMillis()
        return if (time - pressBackTime > BACK_PRESSED_INTERVAL) {
            pressBackTime = time
            showTip(R.string.press_twice_exit, LENGTH_SHORT)
            true
        } else {
            false
        }
    }

    @Throws(SiteException::class)
    private fun loadData() {
        val requested = request()
        if (!requested) {
            throw SiteException("请求数据失败请更换IP地址或检查网络设置是否正确~")
        }
    }

    private fun request(): Boolean {
        val context = ehContext ?: return false
        val activity = activity2 ?: return false
        val ehClient = client ?: return false
        val url = SiteUrl.getTopListUrl()

        val callback = GetTopListDetailListener(context, activity.stageId, tag)

        request = SiteRequest()
            .setMethod(SiteClient.METHOD_GET_TOP_LIST)
            .setArgs(url)
            .setCallback(callback)

        ehClient.execute(request)
        return true
    }

    private fun onGetSiteTopListDetailSuccess(detail: SiteTopListDetail, index: Int) {
        ehTopListDetail = detail
        bindViewSecond(index)
        adjustViewVisibility(STATE_NORMAL, true)
    }

    private fun bindViewSecond(index: Int) {
        val detail = ehTopListDetail ?: return
        val rv = recyclerView ?: return
        val context = ehContext ?: return
        val adapter = SiteTopListAdapterView(context, rv, detail[index], this, index)
        rv.adapter = adapter
    }

    private fun adjustViewVisibility(@State newState: Int, animation: Boolean) {
        val transition = viewTransition ?: return
        state = newState
        val shouldAnimate = !TRANSITION_ANIMATION_DISABLED && animation

        when (newState) {
            STATE_INIT, STATE_REFRESH -> transition.showView(0, shouldAnimate)
            else -> transition.showView(1, shouldAnimate)
        }
    }

    private inner class GetTopListDetailListener(
        context: Context,
        stageId: Int,
        sceneTag: String?,
    ) : SiteCallback<SiteTopListScene, SiteTopListDetail>(context, stageId, sceneTag) {
        override fun isInstance(scene: SceneFragment): Boolean = scene is SiteTopListScene

        override fun onSuccess(result: SiteTopListDetail) {
            onGetSiteTopListDetailSuccess(result, 0)
        }

        override fun onFailure(e: Exception) {
        }

        override fun onCancel() {
        }
    }

    private inner class SiteTopListAdapterView(
        context: Context,
        recyclerView: RecyclerView,
        topListInfo: TopListInfo,
        private val sceneFragment: SceneFragment,
        searchType: Int,
    ) : SiteTopListAdapter(context, topListInfo, searchType) {

        private val hashMap = HashMap<Int, Int>()

        override fun clickTitle(urlFollow: String) {
            val urlBuilder = ListUrlBuilder()
            urlBuilder.mode = ListUrlBuilder.MODE_TOP_LIST
            urlBuilder.setFollow(urlFollow)
            GalleryListScene.startScene(sceneFragment, urlBuilder)
        }

        override fun getRandomColor(position: Int): Int {
            hashMap[position]?.let { return it }
            val random = Random()
            val color = Color.argb(160, random.nextInt(256), random.nextInt(256), random.nextInt(256))
            hashMap[position] = color
            return color
        }

        override fun onItemClick(topListItem: TopListItem, searchType: Int) {
            val urlBuilder = ListUrlBuilder()
            if (searchType == 0) {
                urlBuilder.mode = ListUrlBuilder.MODE_NORMAL
            } else {
                urlBuilder.mode = ListUrlBuilder.MODE_UPLOADER
            }

            if (!topListItem.gid.isNullOrEmpty() && !topListItem.token.isNullOrEmpty()) {
                val args = Bundle()
                args.putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN)
                args.putLong(ProgressScene.KEY_GID, topListItem.gid.toLong())
                args.putString(ProgressScene.KEY_PTOKEN, topListItem.tag)
                val announcer = Announcer(GalleryDetailScene::class.java).setArgs(args)
                startScene(announcer)
                return
            } else if (topListItem.href != null) {
                val announcer = createAnnouncerFromClipboardUrl(topListItem.href)
                if (announcer != null) {
                    startScene(announcer)
                    return
                }
            }

            urlBuilder.keyword = topListItem.value
            GalleryListScene.startScene(sceneFragment, urlBuilder)
        }
    }

    private inner class TopListKindSelectedListener : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            mPosition = position
            bindViewSecond(mPosition)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val ACTION_TOP_LIST = "action_top_list"
    }
}
