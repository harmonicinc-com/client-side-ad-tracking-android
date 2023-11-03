package com.harmonicinc.clientsideadtracking.tracking.client.omsdk

import android.content.Context
import com.github.harmonicinc.clientsideadtracking.BuildConfig
import com.harmonicinc.clientsideadtracking.tracking.model.AdVerification
import com.iab.omid.library.harmonicinc.Omid
import com.iab.omid.library.harmonicinc.adsession.AdSession
import com.iab.omid.library.harmonicinc.adsession.AdSessionConfiguration
import com.iab.omid.library.harmonicinc.adsession.AdSessionContext
import com.iab.omid.library.harmonicinc.adsession.CreativeType
import com.iab.omid.library.harmonicinc.adsession.ImpressionType
import com.iab.omid.library.harmonicinc.adsession.Owner
import com.iab.omid.library.harmonicinc.adsession.Partner
import com.iab.omid.library.harmonicinc.adsession.VerificationScriptResource
import java.net.MalformedURLException
import java.net.URL

object AdSessionUtil {
    @Throws(MalformedURLException::class)
    fun getNativeAdSession(
        context: Context,
        customReferenceData: String?,
        creativeType: CreativeType,
        verificationDetails: List<AdVerification>
    ): AdSession {
        ensureOmidActivated(context)
        val adSessionConfiguration = AdSessionConfiguration.createAdSessionConfiguration(
            creativeType,
            if (creativeType == CreativeType.AUDIO) ImpressionType.AUDIBLE else ImpressionType.VIEWABLE,
            Owner.NATIVE,
            if (creativeType == CreativeType.HTML_DISPLAY || creativeType == CreativeType.NATIVE_DISPLAY) Owner.NONE else Owner.NATIVE,
            false
        )
        val partner = Partner.createPartner(
            BuildConfig.PARTNER_NAME,
            "1.0"
        )
        val omidJs: String = OmidJsLoader.getOmidJs(context)
        val verificationScripts: List<VerificationScriptResource> =
            getVerificationScriptResources(verificationDetails)
        val adSessionContext = AdSessionContext.createNativeAdSessionContext(
            partner,
            omidJs,
            verificationScripts,
            null,
            customReferenceData
        )
        return AdSession.createAdSession(adSessionConfiguration, adSessionContext)
    }

    @Throws(MalformedURLException::class)
    private fun getVerificationScriptResources(verificationDetails: List<AdVerification>): List<VerificationScriptResource> {
        return verificationDetails.map {
            val javascriptResourceUrl = getURL(it.javascriptResourceUrl)
            val verificationParameters = it.verificationParameters
            if (verificationParameters == "") {
                VerificationScriptResource.createVerificationScriptResourceWithoutParameters(
                    javascriptResourceUrl
                )
            } else {
                VerificationScriptResource.createVerificationScriptResourceWithParameters(
                    it.vendor,
                    getURL(it.javascriptResourceUrl),
                    it.verificationParameters
                )
            }
        }
    }

    @Throws(MalformedURLException::class)
    private fun getURL(scriptUrl: String): URL {
        return URL(scriptUrl)
    }

    /**
     * Lazily activate the OMID API.
     *
     * @param context any context
     */
    private fun ensureOmidActivated(context: Context) {
        Omid.activate(context.applicationContext)
    }
}