package com.slide.layoutx.config

/**
 * @Author petterp
 * @Date 1/25/21-11:47 AM
 * @Email ShiyihuiCloud@163.com
 * @Function 滚动视图ViewGroup扩展
 */
interface IScrollViewGroupListener {

    /** 越界回弹的方向监听 */
    fun observerBounceDirection(direction: BounceEnum)

    /** 切换到其他item */
    fun observerSwitchItem(oldPosition: Int, newPosition: Int)

    /** 切换到其他item时的进度 */
    fun observerSwitchItemOffset(offset: Int)

    /** 弹性滑动的进度 */
    fun observerBounceOffSet(offset: Int)

}