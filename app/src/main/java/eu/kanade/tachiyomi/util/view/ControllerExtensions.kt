package eu.kanade.tachiyomi.util.view

import android.R
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.math.MathUtils
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import eu.kanade.tachiyomi.util.system.dpToPx
import kotlinx.android.synthetic.main.main_activity.*
import kotlin.math.abs

fun Controller.setOnQueryTextChangeListener(
    searchView: SearchView,
    onlyOnSubmit: Boolean = false,
    f: (text: String?) -> Boolean
) {
    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextChange(newText: String?): Boolean {
            if (!onlyOnSubmit && router.backstack.lastOrNull()
                    ?.controller() == this@setOnQueryTextChangeListener
            ) {
                return f(newText)
            }
            return false
        }

        override fun onQueryTextSubmit(query: String?): Boolean {
            if (router.backstack.lastOrNull()?.controller() == this@setOnQueryTextChangeListener) {
                val imm =
                    activity?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        ?: return f(query)
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                return f(query)
            }
            return true
        }
    })
}

fun Controller.scrollViewWith(
    recycler: RecyclerView,
    padBottom: Boolean = false,
    customPadding: Boolean = false,
    swipeRefreshLayout: SwipeRefreshLayout? = null,
    afterInsets: ((WindowInsets) -> Unit)? = null,
    liftOnScroll: ((Boolean) -> Unit)? = null
) {
    var statusBarHeight = -1
    activity?.appbar?.y = 0f
    val attrsArray = intArrayOf(R.attr.actionBarSize)
    val array = recycler.context.obtainStyledAttributes(attrsArray)
    var appBarHeight = if (activity!!.toolbar.height > 0) activity!!.toolbar.height
    else array.getDimensionPixelSize(0, 0)
    array.recycle()
    swipeRefreshLayout?.setDistanceToTriggerSync(150.dpToPx)
    activity!!.toolbar.post {
        if (activity!!.toolbar.height > 0) {
            appBarHeight = activity!!.toolbar.height
            recycler.requestApplyInsets()
        }
    }
    recycler.doOnApplyWindowInsets { view, insets, _ ->
        val headerHeight = insets.systemWindowInsetTop + appBarHeight
        if (!customPadding) view.updatePaddingRelative(
            top = headerHeight,
            bottom = if (padBottom) insets.systemWindowInsetBottom else view.paddingBottom
        )
        swipeRefreshLayout?.setProgressViewOffset(
            true, headerHeight + (-60).dpToPx, headerHeight + 10.dpToPx
        )
        statusBarHeight = insets.systemWindowInsetTop
        afterInsets?.invoke(insets)
    }
    var elevationAnim: ValueAnimator? = null
    var elevate = false
    val elevateFunc: (Boolean) -> Unit = { el ->
        elevate = el
        if (liftOnScroll != null) {
            liftOnScroll.invoke(el)
        } else {
            elevationAnim?.cancel()
            elevationAnim = ValueAnimator.ofFloat(
                activity!!.appbar.elevation, if (el) 15f else 0f
            )
            elevationAnim?.addUpdateListener { valueAnimator ->
                activity!!.appbar.elevation = valueAnimator.animatedValue as Float
            }
            elevationAnim?.start()
        }
    }
    addLifecycleListener(object : Controller.LifecycleListener() {
        override fun onChangeStart(
            controller: Controller,
            changeHandler: ControllerChangeHandler,
            changeType: ControllerChangeType
        ) {
            super.onChangeStart(controller, changeHandler, changeType)
            if (changeType.isEnter)
                elevateFunc(elevate)
            else
                elevationAnim?.cancel()
        }
    })
    elevateFunc(recycler.canScrollVertically(-1))
    recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (router?.backstack?.lastOrNull()
                    ?.controller() == this@scrollViewWith && statusBarHeight > -1 && activity != null && activity!!.appbar.height > 0
            ) {
                if (!recycler.canScrollVertically(-1)) {
                    val shortAnimationDuration = resources?.getInteger(
                        R.integer.config_shortAnimTime
                    ) ?: 0
                    activity!!.appbar.animate().y(0f).setDuration(shortAnimationDuration.toLong())
                        .start()
                    if (elevate) elevateFunc(false)
                } else {
                    activity!!.appbar.y -= dy
                    activity!!.appbar.y = MathUtils.clamp(
                        activity!!.appbar.y, -activity!!.appbar.height.toFloat(), 0f
                    )
                    if ((activity!!.appbar.y <= -activity!!.appbar.height.toFloat() ||
                            dy == 0 && activity!!.appbar.y == 0f) && !elevate)
                        elevateFunc(true)
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                if (router?.backstack?.lastOrNull()
                        ?.controller() == this@scrollViewWith && statusBarHeight > -1 && activity != null && activity!!.appbar.height > 0
                ) {
                    val halfWay = abs((-activity!!.appbar.height.toFloat()) / 2)
                    val shortAnimationDuration = resources?.getInteger(
                        R.integer.config_shortAnimTime
                    ) ?: 0
                    val closerToTop = abs(activity!!.appbar.y) - halfWay > 0
                    val atTop = !recycler.canScrollVertically(-1)
                    activity!!.appbar.animate().y(
                        if (closerToTop && !atTop) (-activity!!.appbar.height.toFloat())
                        else 0f
                    ).setDuration(shortAnimationDuration.toLong()).start()
                    if (recycler.canScrollVertically(-1) && !elevate)
                        elevateFunc(true)
                    else if (!recycler.canScrollVertically(-1) && elevate)
                        elevateFunc(false)
                }
            }
        }
    })
}

fun Controller.requestPermissionsSafe(permissions: Array<String>, requestCode: Int) {
    val activity = activity ?: return
    permissions.forEach { permission ->
        if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), requestCode)
        }
    }
}

fun Controller.withFadeTransaction(): RouterTransaction {
    return RouterTransaction.with(this)
        .pushChangeHandler(FadeChangeHandler())
        .popChangeHandler(FadeChangeHandler())
}
