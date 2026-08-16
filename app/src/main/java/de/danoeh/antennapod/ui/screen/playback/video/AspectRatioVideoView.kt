package de.danoeh.antennapod.ui.screen.playback.video

/*
 * Copyright (C) Google Inc.  All rights reserved.
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
 */

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class AspectRatioVideoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
                                                     defStyle: Int = 0) :
        VideoView(context, attrs, defStyle) {


    private var mVideoWidth = 0
    private var mVideoHeight = 0
    private var mAvailableWidth = -1f
    private var mAvailableHeight = -1f

    init {
        mVideoWidth = 0
        mVideoHeight = 0
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (mVideoWidth <= 0 || mVideoHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        if (mAvailableWidth < 0 || mAvailableHeight < 0) {
            mAvailableWidth = getWidth().toFloat()
            mAvailableHeight = getHeight().toFloat()
        }

        val heightRatio = mVideoHeight.toFloat() / mAvailableHeight
        val widthRatio = mVideoWidth.toFloat() / mAvailableWidth

        val scaledHeight: Int
        val scaledWidth: Int

        if (heightRatio > widthRatio) {
            scaledHeight = Math.ceil((mVideoHeight.toFloat() / heightRatio).toDouble()).toInt()
            scaledWidth = Math.ceil((mVideoWidth.toFloat() / heightRatio).toDouble()).toInt()
        } else {
            scaledHeight = Math.ceil((mVideoHeight.toFloat() / widthRatio).toDouble()).toInt()
            scaledWidth = Math.ceil((mVideoWidth.toFloat() / widthRatio).toDouble()).toInt()
        }

        setMeasuredDimension(scaledWidth, scaledHeight)
    }

    /**
     * Source code originally from:
     * http://clseto.mysinablog.com/index.php?op=ViewArticle&articleId=2992625
     *
     * @param videoWidth
     * @param videoHeight
     */
    fun setVideoSize(videoWidth: Int, videoHeight: Int) {
        // Set the new video size
        mVideoWidth = videoWidth
        mVideoHeight = videoHeight

        /*
         * If this isn't set the video is stretched across the
         * SurfaceHolders display surface (i.e. the SurfaceHolder
         * as the same size and the video is drawn to fit this
         * display area). We want the size to be the video size
         * and allow the aspectratio to handle how the surface is shown
         */
        getHolder().setFixedSize(videoWidth, videoHeight)

        requestLayout()
        invalidate()
    }

    /**
     * Sets the maximum size that the view might expand to
     * @param width
     * @param height
     */
    fun setAvailableSize(width: Float, height: Float) {
        mAvailableWidth = width
        mAvailableHeight = height
        requestLayout()
    }
}
