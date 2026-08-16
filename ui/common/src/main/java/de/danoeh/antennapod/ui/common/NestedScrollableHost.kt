/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Source: https://github.com/android/views-widgets-samples/blob/87e58d1/ViewPager2/app/src/main/java/androidx/viewpager2/integration/testapp/NestedScrollableHost.kt
 * And modified for our need
 */

package de.danoeh.antennapod.ui.common

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout

import androidx.viewpager2.widget.ViewPager2

import androidx.viewpager2.widget.ViewPager2.ORIENTATION_HORIZONTAL
import androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL

import kotlin.math.abs


/**
 * Layout to wrap a scrollable component inside a ViewPager2. Provided as a solution to the problem
 * where pages of ViewPager2 have nested scrollable elements that scroll in the same direction as
 * ViewPager2. The scrollable element needs to be the immediate and only child of this host layout.
 *
 * This solution has limitations when using multiple levels of nested scrollable elements
 * (e.g. a horizontal RecyclerView in a vertical RecyclerView in a horizontal ViewPager2).
 */
class NestedScrollableHost @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                     defStyleAttr: Int = 0, defStyleRes: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private var parentViewPager: ViewPager2? = null
    private var touchSlop = 0
    private var initialX = 0f
    private var initialY = 0f
    private var preferVertical = 1
    private var preferHorizontal = 1
    private var scrollDirection = 0

    init {
        init(context)
        if (attrs != null) {
            setAttributes(context, attrs)
        }
    }

    private fun setAttributes(context: Context, attrs: AttributeSet?) {
        val a: TypedArray = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.NestedScrollableHost,
                0, 0)

        try {
            preferHorizontal = a.getInteger(R.styleable.NestedScrollableHost_preferHorizontal, 1)
            preferVertical = a.getInteger(R.styleable.NestedScrollableHost_preferVertical, 1)
            scrollDirection = a.getInteger(R.styleable.NestedScrollableHost_scrollDirection, 0)
        } finally {
            a.recycle()
        }

    }

    private fun init(context: Context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop()


        getViewTreeObserver().addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                var v = getParent() as View?
                while ((v != null && v !is ViewPager2) || isntSameDirection(v)) {
                    v = v!!.getParent() as View?
                }
                parentViewPager = v as ViewPager2?

                getViewTreeObserver().removeOnPreDrawListener(this)
                return false
            }
        })
    }

    private fun isntSameDirection(v: View?): Boolean {
        var orientation = 0
        when (scrollDirection) {
            1 -> orientation = ORIENTATION_VERTICAL
            2 -> orientation = ORIENTATION_HORIZONTAL
            else -> return false
        }
        return (v is ViewPager2) && (v as ViewPager2).getOrientation() != orientation
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        handleInterceptTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }


    private fun canChildScroll(orientation: Int, delta: Float): Boolean {
        val direction = -delta.toInt()
        val child = getChildAt(0)!!
        if (orientation == 0) {
            return child.canScrollHorizontally(direction)
        } else if (orientation == 1) {
            return child.canScrollVertically(direction)
        } else {
            throw IllegalArgumentException()
        }
    }

    private fun handleInterceptTouchEvent(e: MotionEvent) {
        if (parentViewPager == null) {
            return
        }
        val orientation = parentViewPager!!.getOrientation()
        val preferedDirection = preferHorizontal + preferVertical > 2

        // Early return if child can't scroll in same direction as parent and theres no prefered scroll direction
        if (!canChildScroll(orientation, -1f) && !canChildScroll(orientation, 1f) && !preferedDirection) {
            return
        }


        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            initialX = e.getX()
            initialY = e.getY()
            getParent().requestDisallowInterceptTouchEvent(true)
        } else if (e.getAction() == MotionEvent.ACTION_MOVE) {
            val dx = e.getX() - initialX
            val dy = e.getY() - initialY
            val isVpHorizontal = orientation == ViewPager2.ORIENTATION_HORIZONTAL

            // assuming ViewPager2 touch-slop is 2x touch-slop of child
            val scaledDx = abs(dx) * (if (isVpHorizontal) 1f else 0.5f) * preferHorizontal
            val scaledDy = abs(dy) * (if (isVpHorizontal) 0.5f else 1f) * preferVertical
            if (scaledDx > touchSlop || scaledDy > touchSlop) {
                if (isVpHorizontal == (scaledDy > scaledDx)) {
                    // Gesture is perpendicular, allow all parents to intercept
                    getParent().requestDisallowInterceptTouchEvent(preferedDirection)
                } else {
                    // Gesture is parallel, query child if movement in that direction is possible
                    if (canChildScroll(orientation, if (isVpHorizontal) dx else dy)) {
                        // Child can scroll, disallow all parents to intercept
                        getParent().requestDisallowInterceptTouchEvent(true)
                    } else {
                        // Child cannot scroll, allow all parents to intercept
                        getParent().requestDisallowInterceptTouchEvent(false)
                    }
                }
            }

        }
    }
}
