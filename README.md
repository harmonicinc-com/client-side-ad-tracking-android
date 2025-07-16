# Harmonic Client Side Ad Tracking for Android

A library for sending ad beacons on Android devices. Library is currently integrated with Google PAL & OM SDK. The project also includes a demo app that hosts an ExoPlayer that allows users to test their custom DAI assets with Google PAL & OM SDK signaling.

## Features
- [x] Harmonic VOS metadata parsing
- [x] Google Programmatic Access Libraries (PAL)
- [x] Fire ad beacons via Open Measurement SDK (OMSDK)
- [x] Google AdChoices (Why this ad?)
- [x] Android TV ads library (AT&C rendering)
- [x] An overlay showing ad break parameters (ID, duration, fired beacons)

## Requirements
Android 8.0 (API 26) or above
- Min SDK 26 
- Target SDK 33 
- Compile SDK 34

## Usage
1. Include this library in your project

   Groovy:
   ```groovy
   dependencies {
       implementation 'com.github.harmonicinc-com:client-side-ad-tracking-android:0.1.4' 
   }
   ```
   Kotlin:
   ```kotlin
   dependencies {
       implementation("com.github.harmonicinc-com:client-side-ad-tracking-android:0.1.4") 
   }
   ```
   Change version to the latest available. You may find the latest version [here](https://central.sonatype.com/artifact/com.github.harmonicinc-com/client-side-ad-tracking-android/).

2. Include the OMSDK library as dependency
   > [!NOTE]  
   > As of now (Oct 2023), Google still has no support on bundling local modules into a single AAR (Fat AAR). That blocks us from shipping the OMSDK AAR together with the library. 

   - Create a directory on your app root (we use `libs` as an example)
   - Download the AAR that we've included in this repo: [omsdk-android-1.4.5-release.aar](lib%2Flib%2Fomsdk-android-1.4.5-release.aar)
   - Place it under `libs`
   - Add the following lines in your dependency block
      ```groovy
      dependencies {
          implementation fileTree(include: ['*.aar'], dir: 'libs')
          ...
      }
      ```
      Kotlin:
      ```kotlin
      dependencies {
          implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
          // or just include everything
          implementation(fileTree("libs"))
          ...
      }
      ``` 
3. Declare `AD_ID` permission
   - To enable Google WTA, add the following line to your `AndroidManifest.xml`
     ```xml
     <uses-permission android:name="com.google.android.gms.permission.AD_ID"/>
     ```
4. Initialize parameters & interfaces
   - Create an instance of `AdTrackingManager` in activity (preferably, could be somewhere else).
     ```kotlin
     class PlayerActivity: FragmentActivity() {
         private val adTrackingManager = AdTrackingManager(this)
     }
     ```
   - Create an instance of `AdTrackingManagerParams`. Fill in all the required parameters:
     ```kotlin
     val adTrackingParams = AdTrackingManagerParams(
         descriptionUrl, // String: Description URL of video being played
         iconSupported, // Boolean: True if WTA (Why this ad?) icon is supported
         playerType, // String: Name of partner player used to play the ad
         playerVersion, // String: Version of partner player used
         ppid, // String: Publisher Provided ID
         supportedApiFrameworks, // Set<Int>: Supported API frameworks. See appendix for details
         playerHeight, // Int: Player height
         playerWidth, // Int: Player width
         willAdAutoplay, // Boolean: Will ad begin playback automatically?
         willAdPlayMuted, // Boolean: Will ad being played while muted?
         continuousPlayback, // Boolean: Will the player continues to play content videos after ad?
         omidPartnerVersion?, // String?: OMID partner version
         omidPartnerName?, // String: OMID partner name
         omidCustomReferenceData?, // String?: OMID custom reference data in JSON string
         
         // Optional params:
         initRequest // Boolean: Should the session init API be used?
     )
     ```
   - Create a class that implements `PlayerAdapter`. Override all mandatory methods and return appropriate values from your player. The demo project includes an example [ExoPlayerAdapter.kt](demo%2Fsrc%2Fmain%2Fjava%2Fcom%2Fharmonicinc%2Fcsabdemo%2Fplayer%2FExoPlayerAdapter.kt) for your reference.
     ```kotlin
     class YourPlayerAdapter(private val player: YourPlayer): PlayerAdapter {
         override fun getCurrentPositionMs(): Long {
             // TO BE IMPLEMENTED
         }
         override fun getPresentationStartTimeMs(): Long {
             // TO BE IMPLEMENTED
         }
         override fun getPlaybackRate(): Float {
             // TO BE IMPLEMENTED
         }
         override fun getDuration(): Long {
             // TO BE IMPLEMENTED
         }
         override fun getAudioVolume(): Float {
             // TO BE IMPLEMENTED
         }
         override fun isPaused(): Boolean {
             // TO BE IMPLEMENTED
         }
     }
     ```
   - Fire the events in `PlayerAdapter` when specific conditions are met. You might need to listen events emitted from your player.
     ```kotlin
     val playerAdapter = YourPlayerAdapter(player)
     // Call when player starts to buffer
     playerAdapter.onBufferStart()
     
     // Call when playback resumes after buffering
     playerAdapter.onBufferEnd()
     
     // Call when user clicks pause (no need to check if ad is playing. The lib will handle it)
     playerAdapter.onPause()
     
     // Call when user clicks play (no need to check if ad is playing. The lib will handle it)
     playerAdapter.onResume()
     
     // Call when the user clicks an ad
     playerAdapter.onVideoAdClick()
     
     // Call when the user clicks somewhere other than an ad (e.g. skip, mute, etc.)
     playerAdapter.onVideoAdViewTouch()
     
     // Call when user changes the audio volume (no need to check if ad is playing. The lib will handle it)
     playerAdapter.onVolumeChanged()
     ```
5. Using the library
   - Before loading the asset, call `prepareBeforeLoad` to preload the library. Note that it should be called within a coroutine scope.
     ```kotlin 
     val manifestUrl = "https://www.example.com" // put your URL here
     CoroutineScope(Dispatchers.Main).launch {
         adTrackingManager.prepareBeforeLoad(manifestUrl, adTrackingParams)
     }
     ```
   - After preloading, check if the asset supports Harmonic SSAI using `isSSAISupported`. Make sure the asset is supported by the library before continuing.
     ```kotlin
     val isSSAISupported = adTrackingManager.isSSAISupported()
     ```
   - Check if a new URL is obtained by the library. If that is the case, use that URL for your playback.
     ```kotlin
     var updatedManifestUrl = manifestUrl
     if (adTrackingManager.getObtainedManifestUrl() != null) {
        updatedManifestUrl = adTrackingManager.getObtainedManifestUrl()
     }
     ```
   - To obtain the generated nonce, call `appendNonceToUrl`
     ```kotlin
     val manifestUrls = listOf("https://www.example.com")
     val urlsWithNonce = adTrackingManager.appendNonceToUrl(manifestUrls)
     ```
   - Finally, call `onPlay` to start the library
     ```kotlin
     adTrackingManager.onPlay(
        playerContext, // context in your player view
        playerAdapter, // adapter created above
        overlayFrameLayout?, // Optional. A frame layout for showing overlays (e.g. WTA icon, tracking debug overlay)
        playerView // Your player view. Fallback to this if no overlay frame layout is provided
     )
     ```
6. Stop the library after playback 
   - Remember to clean the library after unloading the asset. Otherwise it will keep querying ads metadata.
     ```kotlin
     adTrackingManager.cleanupAfterStop()
     ```

## Development
### Monitor traffic with Charles Proxy
Follow below steps so traffic (especially HTTPS) can be proxied & decrypted by Charles
1. Open Charles Proxy, go to Help > SSL Proxying > Save Charles Root Certificate
2. Save as `<your_android_app_project_root>/src/main/res/raw/charles_ssl_cert.pem`
3. Create an XML under `<root>/src/main/res/xml/network_security_config.xml` with the following content:
   ```xml
    <network-security-config>
       <debug-overrides>
           <trust-anchors>
               <!-- Trust user added CAs while debuggable only -->
               <certificates src="user" />
               <certificates src="@raw/charles_ssl_cert" />
           </trust-anchors>
       </debug-overrides>
   </network-security-config>
   ```
4. Reference to the new config in your app's manifest (i.e. `AndroidManifest.xml`)
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <manifest>
       <application android:networkSecurityConfig="@xml/network_security_config">
       </application>
   </manifest>
   ```
5. Configure the proxy settings on your Android device/emulator. Please refer to your device documentation for instructions.
6. You should now be able to capture SSL traffic in Charles. Look for segments/manifest/metadata requests and see if you can view the response body.

### HTTP requests
> [!NOTE]  
> Allowing insecure traffic is not recommended in production environment. Remember to undo the changes before publishing.

Android apps by default blocks plain text traffic. If you would like to play insecure streams (HTTP):
- In your app's manifest (i.e. `AndroidManifest.xml`), add an extra flag
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <manifest>
      <application android:usesCleartextTraffic="true">
      </application>
  </manifest>
  ```
#### If your Android is running P (aka Android 9) or higher, do also:
- Create an XML under `<root>/src/main/res/xml/network_security_config.xml` with the following content:
  ```xml
  <network-security-config>
      <base-config cleartextTrafficPermitted="true" />
  </network-security-config>
  ```
- Reference to the new config in your app's manifest (i.e. `AndroidManifest.xml`)
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <manifest>
      <application android:networkSecurityConfig="@xml/network_security_config">
      </application>
  </manifest>
  ```

## Appendix
### Tracking overlay
To show/hide the tracking overlay, call `showTrackingOverlay`
```kotlin
adTrackingManager.showTrackingOverlay(state)
```
### API frameworks
> Reference: [IAB AdCOM v1.0 FINAL](https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/main/AdCOM%20v1.0%20FINAL.md#list--api-frameworks-)

The following table is a list of API frameworks either supported by a placement or required by an ad.

<table>
  <tr>
    <td><strong>Value</strong></td>
    <td><strong>Definition</strong></td>
  </tr>
  <tr>
    <td>1</td>
    <td>VPAID 1.0</td>
  </tr>
  <tr>
    <td>2</td>
    <td>VPAID 2.0</td>
  </tr>
  <tr>
    <td>3</td>
    <td>MRAID 1.0</td>
  </tr>
  <tr>
    <td>4</td>
    <td>ORMMA</td>
  </tr>
  <tr>
    <td>5</td>
    <td>MRAID 2.0</td>
  </tr>
  <tr>
    <td>6</td>
    <td>MRAID 3.0</td>
  </tr>
  <tr>
    <td>7</td>
    <td>OMID 1.0</td>
  </tr>
    <tr>
    <td>8</td>
    <td>SIMID 1.0</td>
  </tr>
    </tr>
    <tr>
    <td>9</td>
    <td>SIMID 1.1</td>
  </tr>
    <tr>
    <td>500+</td>
    <td>Vendor-specific codes.</td>
  </tr>
</table>

### How the Playback URL and Beaconing URL are Obtained by the Library

> [!NOTE]  
> Applicable when `initRequest` in `AdTrackingManagerParams` is `true` (default is true).

1. The library sends a request to the manifest endpoint with the query param "initSession=true". For e.g., a GET request is sent to:
    ```
    https://my-host/variant/v1/dash/manifest.mpd?initSession=true
    ```

2. The ad insertion service (PMM) responds with the URLs. For e.g.,
    ```
    {
        "manifestUrl": "./manifest.mpd?sessid=a700d638-a4e8-49cd-b288-6809bd35a3ed&vosad_inst_id=pmm-0",
        "trackingUrl": "./metadata?sessid=a700d638-a4e8-49cd-b288-6809bd35a3ed&vosad_inst_id=pmm-0"
    }
    ```

3. The library constructs the URLs by combining the host in the original URL and the relative URLs obtained. For e.g.,
    ```
    Manifest URL: https://my-host/variant/v1/dash/manifest.mpd?sessid=a700d638-a4e8-49cd-b288-6809bd35a3ed&vosad_inst_id=pmm-0

    Metadata URL: https://my-host/variant/v1/dash/metadata?sessid=a700d638-a4e8-49cd-b288-6809bd35a3ed&vosad_inst_id=pmm-0
    ```

> [!NOTE]  
> When `appendNonceToUrl` is called, the resulting URL will be constructed using the URL obtained above.
