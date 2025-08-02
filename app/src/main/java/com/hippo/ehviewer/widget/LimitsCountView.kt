package com.hippo.ehviewer.widget

import android.animation.Animator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.github.ybq.android.spinkit.SpinKitView
import com.hippo.ehviewer.EhApplication
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.EhClient
import com.hippo.ehviewer.client.EhRequest
import com.hippo.ehviewer.client.data.HomeDetail

class LimitsCountView : FrameLayout {
    private val context: Context
    private var refreshIcon: ImageView? = null
    private var refreshing: SpinKitView? = null
    private var limitsCount: TextView? = null

    private var fromGallery: TextView? = null
    private var fromTorrent: TextView? = null
    private var fromDownload: TextView? = null
    private var fromHentai: TextView? = null

    //    private TextView currentPower;
    private var resetLimits: TextView? = null

    private var homeDetail: HomeDetail? = null

    private var onViewNeedGone: OnViewNeedGone? = null
    private var onViewNeedVisible: OnViewNeedVisible? = null

    var gone: Boolean = true

    private var animating = false

    private var index = 4

    private val rows: MutableList<TextView?> = ArrayList()

    constructor(context: Context) : super(context) {
        this.context = context
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        this.context = context
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        this.context = context
        init(context)
    }

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        this.context = context
        init(context)
    }

    private fun init(context: Context) {
        val inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.limits_count_main, this)
        val login = Settings.isLogin() && Settings.getShowEhLimits()
        if (!login) {
            this.visibility = GONE
            return
        }

        refreshIcon = findViewById(R.id.refresh_icon)
        refreshing = findViewById(R.id.refreshing)
        limitsCount = findViewById(R.id.limits_count)
        fromGallery = findViewById(R.id.from_gallery)
        fromTorrent = findViewById(R.id.from_torrent)
        fromDownload = findViewById(R.id.from_download)
        fromHentai = findViewById(R.id.from_hentai)
        //        currentPower = findViewById(R.id.current_power);
        resetLimits = findViewById(R.id.reset_limits)


        rows.add(fromGallery)
        rows.add(fromTorrent)
        rows.add(fromDownload)
        rows.add(fromHentai)
        //        rows.add(currentPower);
        rows.add(resetLimits)

        setOnClickListener { view: View -> this.onClick(view) }

        resetLimits!!.setOnClickListener { view: View -> this.resetLimit(view) }
        limitsCount!!.setOnClickListener { view: View -> this.onClick(view) }
        refreshIcon!!.setOnClickListener { view: View -> this.onLoadData(view) }

        if (onViewNeedGone == null) {
            onViewNeedGone = OnViewNeedGone()
        }
        if (onViewNeedVisible == null) {
            onViewNeedVisible = OnViewNeedVisible()
        }
    }

    private fun resetLimit(view: View) {
        if (homeDetail!!.resetCost() == 0L) {
            Toast.makeText(context, R.string.limit_unneed_reset, Toast.LENGTH_LONG).show()
            onLoadData(view)
            return
        }
        refreshIcon!!.visibility = GONE
        refreshing!!.visibility = VISIBLE
        val callback: EhClient.Callback<HomeDetail> = LimitsCountDataListener(context, view)

        val request = EhRequest()
            .setMethod(EhClient.METHOD_RESET_LIMIT)
            .setCallback(callback)
        EhApplication.getEhClient(context).execute(request)
    }

    fun onLoadData(view: View, checkData: Boolean) {
        if (!Settings.isLogin()) {
            return
        }
        if (!Settings.getShowEhLimits()) {
            this.visibility = GONE
            return
        } else {
            this.visibility = VISIBLE
        }
        if (checkData && homeDetail != null) {
            return
        }
        if (refreshIcon == null || refreshing == null) {
            return
        }
        onLoadData(view)
    }

    private fun onLoadData(view: View) {
        refreshIcon!!.visibility = GONE
        refreshing!!.visibility = VISIBLE
        val callback: EhClient.Callback<HomeDetail>
        if (view === this) {
            callback = LimitsCountDataListener(context, view)
        } else {
            callback = LimitsCountDataListener(context)
        }
        val request = EhRequest()
            .setMethod(EhClient.METHOD_GET_HOME)
            .setCallback(callback)
        EhApplication.getEhClient(context).execute(request)
    }

    private fun onClick(view: View) {
        try{
            if (homeDetail == null) {
                onLoadData(this)
                return
            }
            if (animating) {
                return
            }
            animating = true
            if (gone) {
                showNext()
            } else {
                removeNext()
            }
        }catch (e:Exception){
            e.printStackTrace();
        }
    }

    private fun showNext() {
        rows[index]!!.visibility = VISIBLE
        rows[index]!!.animate().translationZ(-50f).alpha(1f).setDuration(100)
            .setListener(onViewNeedVisible)
    }

    private fun removeNext() {
        rows[index]!!.animate().translationZ(0f).alpha(0f).setDuration(100)
            .setListener(onViewNeedGone)
    }

    private fun bindingData() {
        fromGallery!!.text =
            resources.getString(R.string.from_gallery_visits, homeDetail!!.fromGalleryVisits)
        fromTorrent!!.text =
            resources.getString(
                R.string.from_torrent_completions,
                homeDetail!!.fromTorrentCompletions
            )
        fromDownload!!.text =
            resources.getString(R.string.from_archive_download, homeDetail!!.fromArchiveDownloads)
        fromHentai!!.text =
            resources.getString(R.string.from_hentai_home, homeDetail!!.fromHentaiAtHome)
        //        currentPower.setText(getResources().getString(R.string.current_moderation_power, homeDetail.getCurrentModerationPower()));
        resetLimits!!.text = resources.getString(R.string.reset_cost, homeDetail!!.resetCost)
    }

    fun hide() {
        if (animating || gone) {
            return
        }
        animating = true
        removeNext()
    }

    fun show() {
        if (animating || !gone) {
            return
        }
        animating = true
        showNext()
    }

    private inner class OnViewNeedVisible : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
            if (index == 0) {
                gone = false
                animating = false
                return
            }
            index--
            showNext()
        }

        override fun onAnimationCancel(animation: Animator) {
        }

        override fun onAnimationRepeat(animation: Animator) {
        }
    }

    private inner class OnViewNeedGone : Animator.AnimatorListener {
        override fun onAnimationStart(animation: Animator) {
        }

        override fun onAnimationEnd(animation: Animator) {
//            fromGallery.setVisibility(GONE);
            rows[index]!!.visibility = GONE
            if (index == rows.size - 1) {
                animating = false
                gone = true
                return
            }
            index++
            removeNext()
        }

        override fun onAnimationCancel(animation: Animator) {
        }

        override fun onAnimationRepeat(animation: Animator) {
        }
    }

    protected inner class LimitsCountDataListener : EhClient.Callback<HomeDetail> {
        private val context: Context
        private var view: View? = null

        constructor(context: Context) {
            this.context = context
        }

        constructor(context: Context, view: View?) {
            this.context = context
            this.view = view
        }

        override fun onSuccess(result: HomeDetail) {
            homeDetail = result
            limitsCount!!.text = homeDetail!!.getImageLimits(context)
            refreshIcon!!.visibility = VISIBLE
            refreshing!!.visibility = GONE
            bindingData()
            if (view != null && gone) {
                onClick(view!!)
            }
        }

        override fun onFailure(e: Exception) {
        }

        override fun onCancel() {
        }
    }
}
