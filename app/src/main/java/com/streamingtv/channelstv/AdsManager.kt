package com.streamingtv.channelstv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Singleton manager for AdMob Interstitial ads and Adsterra smartlink.
 * Converted from AdsManager.java (decompiled from APK).
 */
object AdsManager {

    private const val TAG = "AdsManager"
    private const val ADSTERRA_SMARTLINK =
        "https://www.effectivecpmnetwork.com/spm98pzh4?key=98731ca9908e8d999ed557ae6ed2059b"

    /** AdMob Interstitial Unit ID */
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1578094499373443/3546361903"

    private var mInterstitialAd: InterstitialAd? = null

    /**
     * Pre-loads an interstitial ad so it's ready when needed.
     */
    fun loadInterstitial(context: Context) {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: ${adError.message}")
                    mInterstitialAd = null
                }

                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    Log.d(TAG, "Ad was loaded.")
                    mInterstitialAd = interstitialAd
                }
            }
        )
    }

    /**
     * Shows the interstitial ad (if loaded), then runs [onFinalAction].
     * Also opens the Adsterra smartlink after dismissal.
     */
    fun showAdsThenAction(activity: Activity, onFinalAction: () -> Unit) {
        val ad = mInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadInterstitial(activity)
                    // openSmartlink(activity) // <-- Comentado para que no salte al navegador y no rompa el reproductor
                    onFinalAction()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show: ${adError.message}")
                    mInterstitialAd = null
                    // openSmartlink(activity) // <-- Comentado
                    onFinalAction()
                }
            }
            ad.show(activity)
        } else {
            // openSmartlink(activity) // <-- Comentado
            onFinalAction()
        }
    }

    /**
     * Opens the Adsterra smartlink directly (without showing an ad first).
     */
    fun showAdsterra(context: Context) {
        openSmartlink(context)
    }

    private fun openSmartlink(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ADSTERRA_SMARTLINK))
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open smartlink: ${e.message}")
        }
    }
}
