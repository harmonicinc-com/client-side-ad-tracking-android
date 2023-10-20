package com.harmonicinc.clientsideadtracking.tracking.adchoices

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import com.bumptech.glide.Glide
import com.gtihub.harmonicinc.clientsideadtracking.R
import com.harmonicinc.clientsideadtracking.player.PlayerContext
import com.harmonicinc.clientsideadtracking.tracking.AdBreakListener
import com.harmonicinc.clientsideadtracking.tracking.AdMetadataTracker
import com.harmonicinc.clientsideadtracking.tracking.model.Ad
import com.harmonicinc.clientsideadtracking.tracking.model.icon.Attributes
import com.harmonicinc.clientsideadtracking.tracking.model.icon.iconclicks.IconClickFallbackImage


class AdChoiceManager(
    playerContext: PlayerContext,
    private val tracker: AdMetadataTracker
) {
    private var adChoiceView: RelativeLayout? = null
    private var iconFallbackImageView: ImageView? = null
    private val context: Context = playerContext.androidContext!!
    private var iconShowing = false
    private val tag = "AdChoiceManager"

    init {
        addListeners()

        if (adChoiceView == null) {
            adChoiceView = inflateAdChoiceView(playerContext.androidContext!!)
        }
        playerContext.overlayViewContainer?.let {
            addViewToContainerView(adChoiceView!!, it)
        } ?: run {
            addViewToContainerView(adChoiceView!!, playerContext.playerView!!)
        }
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

        // Download AdChoice icon
        val imageView = ImageView(context)
        Glide.with(context).load(adIcon.staticResource.uri).into(imageView)
        val relativeLayoutParams = RelativeLayout.LayoutParams(
            adIcon.attributes.width,
            adIcon.attributes.height,
        )
        setImagePosition(relativeLayoutParams, adIcon.attributes)
        imageView.layoutParams = relativeLayoutParams

        imageView.setOnClickListener {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(adIcon.iconClicks.iconClickThrough))
                context.startActivity(browserIntent)
            } catch (e: Exception) {
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
                    Log.e(tag, "Failed to either open the click through link, or show a fallback image")
                }
            }
        }

        adChoiceView!!.addView(imageView, relativeLayoutParams)
        adChoiceView!!.visibility = View.VISIBLE
        iconShowing = true
    }

    private fun hideAdChoice() {
        adChoiceView?.removeAllViews()
        adChoiceView?.visibility = View.INVISIBLE
        iconShowing = false
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
}