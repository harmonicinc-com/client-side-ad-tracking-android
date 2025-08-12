package com.harmonicinc.clientsideadtracking.tracking.adchoices

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.view.contains
import com.bumptech.glide.Glide
import com.github.harmonicinc.clientsideadtracking.R
import com.google.android.tv.ads.AdsControlsManager
import com.google.android.tv.ads.IconClickFallbackImages
import com.harmonicinc.clientsideadtracking.tracking.AdBreakListener
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.icon.Attributes
import com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks.IconClickFallbackImage
import com.harmonicinc.clientsideadtracking.tracking.util.AndroidUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AdChoiceManager(
    private val context: Context,
    overlayViewContainer: ViewGroup?,
    playerView: ViewGroup,
    private val tracker: AdMetadataTracker,
    private val coroutineScope: CoroutineScope,
) {
    private var adChoiceView: RelativeLayout? = null
    private var iconFallbackImageView: ImageView? = null
    private var iconShowing = false
    private var imageButton: ImageButton? = null
    private val tag = "AdChoiceManager"
    private val adsControlsManager = AdsControlsManager(context)

    init {
        addListeners()

        if (adChoiceView == null) {
            adChoiceView = inflateAdChoiceView(context)
        }
        
        // Ensure UI operations happen on main thread
        coroutineScope.launch {
            overlayViewContainer?.let {
                addViewToContainerView(adChoiceView!!, it)
            } ?: run {
                addViewToContainerView(adChoiceView!!, playerView)
            }
        }
    }

    fun onDestroy() {
        hideAdChoice()
    }

    private fun inflateAdChoiceView(context: Context): RelativeLayout {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        return inflater.inflate(R.layout.adchoice_view, null) as RelativeLayout
    }

    private fun addViewToContainerView(view: View, containerView: ViewGroup) {
        var parent = view.parent
        if (parent != null && parent is ViewGroup && parent != containerView) {
            parent.removeView(view)
            parent = null
        }
        if (parent == null) {
            containerView.addView(view)
        }
    }

    private fun addListeners() {
        val adBreakListener = object: AdBreakListener {
            override fun onCurrentAdUpdate(ad: Ad?) {
                if (ad != null) showAdChoice(ad) else hideAdChoice()
            }
        }
        tracker.addAdBreakListener(adBreakListener)
    }

    private fun showAdChoice(ad: Ad) {
        if (ad.icons.isEmpty() || iconShowing) return
        val adIcon = ad.icons[0]

        // Ensure UI operations happen on main thread
        coroutineScope.launch {
            // Download AdChoice icon
            val imageButton = ImageButton(context)
            Glide.with(context).load(adIcon.staticResource.uri).into(imageButton)
            val relativeLayoutParams = RelativeLayout.LayoutParams(
                adIcon.attributes.width,
                adIcon.attributes.height,
            )
            setImagePosition(relativeLayoutParams, adIcon.attributes)
            imageButton.layoutParams = relativeLayoutParams
            imageButton.setPadding(0, 0, 0, 0)
            imageButton.isClickable = true
            imageButton.isFocusable = true
            imageButton.setBackgroundResource(R.drawable.adchoices_selector)

            imageButton.setOnClickListener {
                try {
                    if (!AndroidUtils.isTelevision(context)) {
                        throw UnsupportedOperationException("Not running on a TV device")
                    }
                    val fallbackImages = getIconClickFallbackImages(adIcon.iconClicks.iconClickFallbackImages)
                    adsControlsManager.handleIconClick(fallbackImages)
                } catch (e: Exception) {
                    Log.w(tag, "Unable to render AT&C using Android TV ads lib, using alternate method")
                    try {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(adIcon.iconClicks.iconClickThrough))
                        context.startActivity(browserIntent)
                    } catch (e: Exception) {
                        Log.w(tag, "Unable to render AT&C using alternate method, showing fallback image")
                        if (adIcon.iconClicks.iconClickFallbackImages.isNotEmpty()) {
                            val fallbackImg = adIcon.iconClicks.iconClickFallbackImages[0]
                            iconFallbackImageView = getIconFallbackImageView(fallbackImg)
                            iconFallbackImageView!!.setOnClickListener {
                                adChoiceView?.removeView(it)
                            }
                            val fallbackRelativeLayoutParams = RelativeLayout.LayoutParams(
                                fallbackImg.width,
                                fallbackImg.height,
                            )
                            fallbackRelativeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT)
                            adChoiceView!!.addView(iconFallbackImageView, fallbackRelativeLayoutParams)
                        } else {
                            Log.e(tag, "Failed to show a fallback image")
                        }
                    }
                }
            }

            imageButton.viewTreeObserver.addOnGlobalFocusChangeListener(onTvFocusChangeListener)

            adChoiceView!!.addView(imageButton, relativeLayoutParams)
            adChoiceView!!.visibility = View.VISIBLE
            iconShowing = true
            this@AdChoiceManager.imageButton = imageButton

            imageButton.requestFocus()
        }
    }

    private val onTvFocusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { oldV, newV ->
            if (oldV != null && adChoiceView?.contains(oldV) == true && newV != null && adChoiceView?.contains(newV) == false) {
                oldV.requestFocus()
            }
        }

    private fun hideAdChoice() {
        // Ensure UI operations happen on main thread
        coroutineScope.launch {
            adChoiceView?.removeAllViews()
            adChoiceView?.visibility = View.INVISIBLE
            iconShowing = false
            imageButton?.viewTreeObserver?.removeOnGlobalFocusChangeListener(onTvFocusChangeListener)
            imageButton = null
        }
    }

    private fun getIconFallbackImageView(fallbackImg: IconClickFallbackImage): ImageView {
        val imageView = ImageView(context)
        Glide.with(context).load(fallbackImg.staticResource.uri).into(imageView)
        return imageView
    }

    private fun setImagePosition(params: RelativeLayout.LayoutParams, attr: Attributes) {
        if (attr.xPositionStr != null) {
            if (attr.xPositionStr == "left") {
                params.leftMargin = 0
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
            } else if (attr.xPositionStr == "right") {
                params.rightMargin = 0
                params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
            }
        } else if (attr.xPosition != null) {
            params.leftMargin = attr.xPosition
        }

        if (attr.yPositionStr != null) {
            if (attr.yPositionStr == "top") {
                params.topMargin = 0
                params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            } else if (attr.yPositionStr == "bottom") {
                params.bottomMargin = 0
                params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            }
        } else if (attr.yPosition != null) {
            params.topMargin = attr.yPosition
        }
    }

    private fun getIconClickFallbackImages(images: List<IconClickFallbackImage>): IconClickFallbackImages {
        return IconClickFallbackImages.builder(
            images.map {
                com.google.android.tv.ads.IconClickFallbackImage.builder()
                    .setWidth(it.width)
                    .setHeight(it.height)
                    .setAltText(it.altText)
                    .setCreativeType(it.staticResource.creativeType)
                    .setStaticResourceUri(it.staticResource.uri)
                    .build()
            }
        ).build()
    }
}