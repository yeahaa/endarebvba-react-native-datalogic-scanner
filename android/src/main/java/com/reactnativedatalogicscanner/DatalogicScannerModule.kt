package com.reactnativedatalogicscanner

import android.os.Handler
import android.os.Looper
import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.DecodeException
import com.datalogic.decode.ReadListener
import com.datalogic.decode.configuration.ScannerProperties
import com.datalogic.device.ErrorManager
import com.datalogic.extension.selfshopping.cradle.Cradle
import com.datalogic.extension.selfshopping.cradle.CradleInsertionListener
import com.datalogic.extension.selfshopping.cradle.CradleManager
import com.datalogic.extension.selfshopping.cradle.CradleType
import com.datalogic.extension.selfshopping.cradle.joyatouch.CradleJoyaTouch
import com.datalogic.extension.selfshopping.cradle.joyatouch.LockAction
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class DatalogicScannerModule(reactContext: ReactApplicationContext) :
        ReactContextBaseJavaModule(reactContext), CradleInsertionListener {

  private var barcodeManagerOnce: BarcodeManager? = null
  private var listenerOnce: ReadListener? = null
  private var barcodeManagerContinuous: BarcodeManager? = null
  private var listenerContinuous: ReadListener? = null
  private var cradleJoyaTouch: CradleJoyaTouch? = null
  private var cradleListenerRegistered = false
  private var cradleManagerInitialized = false
  private var keepAliveStarted = false
  private val handler = Handler(Looper.getMainLooper())
  private val cradleKeepAliveRunnable = Runnable { keepCradleAlive() }

  init {
    hasCradle()

    reactApplicationContext.addLifecycleEventListener(
            object : LifecycleEventListener {
              override fun onHostResume() {
                if (cradleManagerInitialized) {
                  listenToCradle()
                }
              }

              override fun onHostPause() {}

              override fun onHostDestroy() {
                handler.removeCallbacks(cradleKeepAliveRunnable)

                cradleJoyaTouch?.let {
                  if (cradleListenerRegistered) {
                    it.removeCradleInsertionListener(this@DatalogicScannerModule)
                    cradleListenerRegistered = false
                  }
                }
                // This prevents rare memory leaks after React reload.
                cradleJoyaTouch = null

                keepAliveStarted = false
                cradleManagerInitialized = false
              }
            }
    )
  }

  override fun getName(): String {
    return "DatalogicScanner"
  }

  private fun emitBarcode(barcode: String) {
    val params = Arguments.createMap()
    params.putString("barcode", barcode)
    reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("BarcodeScanned", params)
  }

  private fun emitCradleEvent(event: CradleEvent) {
    val params = Arguments.createMap()
    params.putString("type", event.name)
    reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("CradleChanged", params)
  }

  @ReactMethod
  fun testScan(success: Boolean, promise: Promise) {
    if (success) {
      promise.resolve("TestScan Success")
    } else {
      promise.reject(Exception("TestScan Failure"))
    }
  }

  @ReactMethod
  fun scanOnce(promise: Promise) {
    try {
      barcodeManagerOnce = BarcodeManager().apply {
        setScannerProperties(this)
      }
      ErrorManager.enableExceptions(true)
      listenerOnce = ReadListener { decodeResult ->
        promise.resolve(decodeResult.text)
        barcodeManagerOnce?.removeReadListener(listenerOnce)
        barcodeManagerOnce = null
      }
      val added = barcodeManagerOnce!!.addReadListener(listenerOnce)
    } catch (de: DecodeException) {
      promise.reject(de)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun startScanning(promise: Promise) {
    if (barcodeManagerContinuous != null) {
      promise.resolve(true) // Already started
      return
    }

    try {
      barcodeManagerContinuous = BarcodeManager().apply {
        setScannerProperties(this)
      }
      ErrorManager.enableExceptions(true)
      listenerContinuous = ReadListener { decodeResult ->
        emitBarcode(decodeResult.text)
        // Do NOT resolve the promise here!
        //promise.resolve(decodeResult.text)
      }
      // ZELF
      //barcodeManagerContinuous!!.addReadListener(listenerContinuous)
      val added = barcodeManagerContinuous!!.addReadListener(listenerContinuous)
      if (added > 0) {
        promise.resolve(true)
      } else {
        promise.reject("LISTENER_ERROR", "Failed to add read listener")
      }
    } catch (de: DecodeException) {
      promise.reject(de)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun stopScanning() {
    if (barcodeManagerContinuous == null) {
      return
    }

    if (listenerContinuous != null) {
      barcodeManagerContinuous!!.removeReadListener(listenerContinuous)
      listenerContinuous = null
    }

    barcodeManagerContinuous = null
  }

  @ReactMethod
  fun unlockFromCradle(promise: Promise) {
    if (!hasCradle() || cradleJoyaTouch == null) {
      promise.reject(CradleNotFoundException())

      return
    }

    cradleJoyaTouch?.let { cradle ->
      if (cradle.insertionState == Cradle.InsertionState.INSERTED_CORRECTLY) {
        cradle.controlLock(LockAction.UNLOCK)
        promise.resolve(true)

        return
      }

      // Already unlocked
      promise.resolve(false)
    }
  }

  private fun hasCradle(): Boolean {
    if (cradleJoyaTouch != null) {
      return true
    }

    try {
      val cradle = CradleManager.getCradle()
      if (cradle != null && cradle.type == CradleType.JOYA_TOUCH_CRADLE) {
        cradleJoyaTouch = cradle as CradleJoyaTouch
        return true
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
    return false
  }

  @ReactMethod
  fun getCradleState(promise: Promise) {
    if (!hasCradle() || cradleJoyaTouch == null) {
      promise.reject(CradleNotFoundException())

      return
    }

    cradleJoyaTouch?.let { cradle ->
      when (cradle.insertionState) {
        Cradle.InsertionState.INSERTED_CORRECTLY -> promise.resolve(CradleEvent.INSERTED_CORRECTLY.name)
        Cradle.InsertionState.INSERTED_WRONGLY -> promise.resolve(CradleEvent.INSERTED_WRONGLY.name)
        Cradle.InsertionState.EXTRACTED -> promise.resolve(CradleEvent.EXTRACTED.name)
        else -> promise.reject(CradleNotFoundException())
      }
    }
  }

  @ReactMethod
  fun listenToCradle() {
    if (!hasCradle() || cradleJoyaTouch == null) {
      return
    }
    /*
    cradleJoyaTouch?.apply {
      removeCradleInsertionListener(this@DatalogicScannerModule)
      addCradleInsertionListener(this@DatalogicScannerModule)
    } */
    cradleJoyaTouch?.let { cradle ->
      if (!cradleListenerRegistered) {
        cradle.addCradleInsertionListener(this)
        cradleListenerRegistered = true
      }
      // REQUIRED on Joya Touch 22 — forces service binding or the listener will stop firing after
      // idle/sleep.
      val state = cradle.insertionState
      // Emit current state immediately (important) This prevents state desynchronization between
      // native and React Native.
      when (state) {
        Cradle.InsertionState.INSERTED_CORRECTLY -> emitCradleEvent(CradleEvent.INSERTED_CORRECTLY)
        Cradle.InsertionState.INSERTED_WRONGLY -> emitCradleEvent(CradleEvent.INSERTED_WRONGLY)
        Cradle.InsertionState.EXTRACTED -> emitCradleEvent(CradleEvent.EXTRACTED)
        else -> {}
      }

      if (!keepAliveStarted) {
        handler.post(cradleKeepAliveRunnable)
        keepAliveStarted = true
      }
      cradleManagerInitialized = true
    }
  }
   /** Keep-alive logic for idle devices */
    private fun keepCradleAlive() {
        cradleJoyaTouch?.let { cradle ->
            try {
                val state = cradle.insertionState
                // Optional: emit current state each keep-alive cycle
                when (state) {
                    Cradle.InsertionState.INSERTED_CORRECTLY -> emitCradleEvent(CradleEvent.INSERTED_CORRECTLY)
                    Cradle.InsertionState.INSERTED_WRONGLY -> emitCradleEvent(CradleEvent.INSERTED_WRONGLY)
                    Cradle.InsertionState.EXTRACTED -> emitCradleEvent(CradleEvent.EXTRACTED)
                    else -> {}
                }
            } catch (e: Exception) {
                // Service died, attempt recovery
                cradleJoyaTouch?.let {
                    if (cradleListenerRegistered) {
                        try { it.removeCradleInsertionListener(this) } catch (_: Exception) {}
                        cradleListenerRegistered = false
                    }
                }
                cradleJoyaTouch = null
                cradleManagerInitialized = false
                keepAliveStarted = false

                if (hasCradle()) listenToCradle()
                return
            }
        }

        if (cradleManagerInitialized) {
            handler.postDelayed(cradleKeepAliveRunnable, 30000)
        }
    }

  override fun onDeviceInsertedCorrectly() {
    emitCradleEvent(CradleEvent.INSERTED_CORRECTLY)
  }

  override fun onDeviceInsertedWrongly() {
    emitCradleEvent(CradleEvent.INSERTED_WRONGLY)
  }

  override fun onDeviceExtracted() {
    emitCradleEvent(CradleEvent.EXTRACTED)
  }

  @ReactMethod
  fun addListener(eventName: String) {
    // Keep: Required for RN built in Event Emitter Calls.
  }

  @ReactMethod
  fun removeListeners(count: Int) {
    // Keep: Required for RN built in Event Emitter Calls.
  }

  private fun setScannerProperties(barcodeManager: BarcodeManager) {
    ScannerProperties.edit(barcodeManager).apply {
      ean13.sendChecksum.set(true)
      code39.code32.set(false)
      goodread.goodReadEnable.set(true)
      displayNotification.enable.set(false)
      store(barcodeManager, true)
    }
  }
}
