package com.slide.layoutx.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.*
import android.widget.Scroller
import androidx.annotation.LayoutRes
import androidx.core.view.NestedScrollingParent
import androidx.core.view.NestedScrollingParentHelper
import com.slide.layoutx.config.BounceEnum
import com.slide.layoutx.config.IScrollViewGroupListener
import kotlin.math.abs


/**
 * @Author petterp
 * @Date 1/22/21-3:33 PM
 * @Email ShiyihuiCloud@163.com
 * @Function 支持垂直嵌套滑动的ViewGroup
 * PS: 暂时只支持垂直
 */
class ScrollerViewGroup : ViewGroup, NestedScrollingParent {

    /** 是否允许top越界回弹 */
    private var isTopOverBounce = true

    /** 是否允许bottom越界回弹 */
    private var isBottomOverBounce = false

    /** 按下时的Y轴坐标 */
    private var mDownY = 0f

    /** 当前item下标 */
    private var position = 0

    /** 界面可滚动的top边界 */
    private var topBorder = 0

    /** 弹性滑动距离 */
    private var scrollHeight = 300

    /** 移动时的上一次的Y轴坐标 */
    private var mLastMoveY = 0f

    /** 拖动时的最小移动像素 */
    private var mTouchSlop = 0

    /** 子View个数 */
    private var mChildCount = 0

    /** 界面可滚动的bottom边界 */
    private var bottomBorder = 0

    /** 滚动操作实例 */
    private val mScroller: Scroller

    /** 系统给最大触摸滑动速度 */
    private val mMaximumVelocity: Int

    /** 系统给最小触摸滑动速度 */
    private val mMinimumVelocity: Int

    /** 是否允许执行Fling */
    @Volatile
    var isNestedScrollingFling = false

    /** 监听器 */
    private var listener: IScrollViewGroupListener? = null

    /** 用于检测快速滑动 */
    private var mVelocityTracker: VelocityTracker? = null

    /** 初始化子view的LayoutInflater */
    private var layoutInflater = LayoutInflater.from(context)

    /** 嵌套滚动辅助类 */
    private val mNestedScrollingParentHelper = NestedScrollingParentHelper(this)

    init {
        val configuration = ViewConfiguration.get(context)
        mMaximumVelocity = configuration.scaledMaximumFlingVelocity
        mTouchSlop = configuration.scaledTouchSlop
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity
        mScroller = Scroller(context)
        isMotionEventSplittingEnabled = false
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        mChildCount = childCount
        for (i in 0 until mChildCount) {
            measureChild(getChildAt(i), widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        if (changed && mChildCount > 0) {
            for (i in 0 until mChildCount) {
                val child: View = getChildAt(i)
                child.layout(l, child.measuredHeight * i, r, child.measuredHeight * (i + 1))
            }
            topBorder = getChildAt(0).top
            bottomBorder = getChildAt(mChildCount - 1).bottom
        }
    }


    /** 当有嵌套滚动时，判断父View是否接收嵌套滚动
     * @param child            嵌套滑动对应的父类的子类(因为嵌套滑动对于的父View不一定是一级就能找到的，可能挑了两级父View的父View，child的辈分>=target)
     * @param target           具体嵌套滑动的那个子类
     * @param nestedScrollAxes 支持嵌套滚动轴。水平方向，垂直方向，或者不指定
     * @param type             滑动类型，ViewCompat.TYPE_NON_TOUCH fling 效果ViewCompat.TYPE_TOUCH 手势滑动
     * */
    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        return true
    }


    /** 用于一些配置的初始化 */
    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        super.onNestedScrollAccepted(child, target, axes)
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes)
    }

    /** 获得子View传递的事件，如果未消费完，需要通过consumed传递回去 */
    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        super.onNestedPreScroll(target, dx, dy, consumed)
        /** 这里应该注意事件的变化 */
        val isHide = (dy > 0 && scrollY < getChildAt(position).top)
        //canScrollVertically 可不可以垂直滚动
        val isShow = dy < 0 && scrollY > 0 && !target.canScrollVertically(-1)
        if (isHide || isShow) {
            scrollBy(0, dy)
            consumed[1] = dy
        }
    }


    /** 在嵌套的预备文件上 */
    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return false
    }


    /** 嵌套滚动停止时 */
    override fun onStopNestedScroll(child: View) {
        mNestedScrollingParentHelper.onStopNestedScroll(child)
        if (isNestedScrollingFling) {
            isNestedScrollingFling = false
            return
        }
        val oldPosition = position
        calPosition()
        if ((abs(position * measuredHeight - scrollY)) < mTouchSlop) {
//            observerScrollItem(oldPosition)
            scrollTo(0, position * measuredHeight)
        } else {
            observerScrollItem(oldPosition)
        }
    }


    /** 捕获对内部NestedScrollingChild的fling事件 */
    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ): Boolean {
        //如果子view不可滚动
        val isConsumed = scrollY > 0 && !target.canScrollVertically(-1)
        if (isConsumed && (velocityY < mMinimumVelocity)) {
            isNestedScrollingFling = true
            val oldPosition = position
            position = if (position == 0) 0 else position - 1
            observerScrollItem(oldPosition)
            return true
        }
        return false
    }


    /**  返回当前滑动的方向，一般直接通过NestedScrollingParentHelper.getNestedScrollAxes()返回即可 */
    override fun getNestedScrollAxes(): Int {
        return mNestedScrollingParentHelper.nestedScrollAxes
    }


    /** 默认不拦截事件,将Down优先交给子View自己处理 */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            //确保子View可以获得Down
            MotionEvent.ACTION_DOWN -> {
                if (mVelocityTracker == null) mVelocityTracker = VelocityTracker.obtain()
                mDownY = ev.y
                mLastMoveY = mDownY
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            //这里调用View的scrollTo() 完成实际的滚动
            scrollTo(0, mScroller.currY)
            //必须调用该方法，否则不一定能看到滚动效果
            invalidate()
        }
        super.computeScroll()
    }


    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //添加事件
        when (event.actionMasked) {
            //当又有手指按下时,抛弃此次事件,让回弹到应该去的位置
            MotionEvent.ACTION_POINTER_DOWN -> {
                // TODO: 1/25/21 这里暂时处理有问题，默认不应该这样滚动,应该按照当前子item条目去滚动
                val childAt = getChildAt(position)
                val diffTop = childAt.top - scrollY
                //滚动View
                mScroller.startScroll(0, scrollY, 0, diffTop)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                //更新移动的坐标y
                val moveY = event.y
                //移动的距离
                val mobileY = (mLastMoveY - moveY).toInt()
                mLastMoveY = moveY
                if (mobileY == 0) return true
                //对于距离过大采取忽略move
//                if (mobileY > mMinimumVelocity) return true
                mVelocityTracker?.addMovement(event)
                //如果运行弹性滑动
                if (isOverBounce()) {
                    if (isTopOverBounce && (scrollY - mobileY <= -scrollHeight) && mobileY < 0) {
                        return true
                    }
                    //开启边界判断
                } else {
                    //计算边界
                    if ((scrollY + mobileY <= topBorder) && mobileY < 0) {
                        scrollTo(0, topBorder)
                        return true
                    } else if ((scrollY + mobileY + height > bottomBorder) && mobileY > 0) {
                        scrollTo(0, bottomBorder - height)
                        return true
                    }
//                    // TODO: 1/25/21 bottom弹性滑动暂时关闭
//                    else if ((scrollY + mobileY + height >= bottomBorder + scrollHeight) && mobileY > 0) {
//                        return true
//                    }
//                    if ((scrollY + mobileY + height > bottomBorder) && mobileY > 0) {
//                        scrollTo(0, bottomBorder - height)
//                        return true
//                    }
                }
//                calPosition()
                //滚动View
                scrollBy(0, mobileY)
                //更新最新的按下手指位置
            }
            MotionEvent.ACTION_UP -> {
                if (isOverBounce()) {
                    //证明此时处于弹性下拉打开
                    if ((scrollY + scrollHeight) <= 0 && isTopOverBounce) {
                        scrollItem()
                        listener?.observerBounceDirection(BounceEnum.TOP)
                        return true
                        //此时处于弹性底部打开
                    }

                    // TODO: 1/25/21 底部弹性滑动暂时关闭
//                    else if ((scrollY + height >= bottomBorder + scrollHeight)) {
//                        scrollItem()
//                        listener?.observerBounceDirection(BounceEnum.BOTTOM)
//                        return true
//                    }
                }
                //手指抬起时，初始化速度追踪器
                flingPosition()
            }

            MotionEvent.ACTION_CANCEL -> {
                //还原
//                scrollItem()
                release()
            }
        }
        return true
    }

    /** 计算快速滑动 */
    private fun flingPosition() {
        mVelocityTracker?.computeCurrentVelocity(1000, mMaximumVelocity.toFloat())
        val velocity = mVelocityTracker?.yVelocity ?: 0f
        val oldPosition = position
        //如果速度大于最小滑动速度
        if (abs(velocity) > mMinimumVelocity) {
            //下滑
            if (velocity > 0) {
                position = if (position == 0) 0 else position - 1
                //上滑
            } else if (velocity < 0) {
                position = if (position == childCount - 1) childCount - 1 else position + 1
            }
        } else {
            calPosition()
        }
        //触发带监听的滚动的
        observerScrollItem(oldPosition)
        release()
    }


    /** 是否开启越界回弹 */
    private fun isOverBounce(): Boolean = isTopOverBounce || isBottomOverBounce

    /** 计算当前position下标 */
    private fun calPosition() {
        //如果滚动的距离大于屏幕一半，要滚动的位置=已滑动距离/view高度+1，否则 等于已滑动距离/view高度
        position =
            if (scrollY % measuredHeight > measuredHeight / 2) scrollY / measuredHeight + 1 else scrollY / measuredHeight
        //如果下标>子item个数
        if (position >= childCount - 1) {
            //强行让位置=最后一个
            position = childCount - 1
        } else if (position <= 0) {
            //否则位置为第一个
            position = 0
        }
    }

    /** 设置Top越界回弹 */
    fun setEnableTopOverBounce(isEnable: Boolean) {
        this.isTopOverBounce = isEnable
    }

    /** 设置Bottom越界回弹 */
    fun setEnableBottomOverBounce(isEnable: Boolean) {
        this.isBottomOverBounce = isEnable
    }

    /** 添加监听listener */
    fun addScrollListener(listener: IScrollViewGroupListener) {
        this.listener = listener
    }

    /** 切换到指定item */
    fun setCurrItem(position: Int) {
        val oldPosition = this.position
        if (oldPosition == position) return
        this.position = position
        observerScrollItem(oldPosition, position)
    }


    /** 触发滚动监听 */
    private fun observerScrollItem(oldPosition: Int, duration: Int = 250) {
        if (oldPosition != position) {
            listener?.observerSwitchItem(oldPosition, position)
        }
        scrollItem(duration)
    }

    // TODO: 1/25/21 此方法预估存在问题
    /** 滚动item */
    private fun scrollItem(duration: Int = 250) {
        mScroller.startScroll(
            0,
            scrollY,
            0,
            //计算当前下标
            position * measuredHeight - scrollY, duration
        )
        invalidate()
    }

    /** 添加子View */
    fun <T : View> addView(@LayoutRes layoutRes: Int): T {
        val view = layoutInflater.inflate(
            layoutRes, this, false
        )
        addView(view)
        invalidate()
        return view as T
    }

    /** 清除资源 */
    private fun release() {
        mVelocityTracker?.clear()
    }

}
