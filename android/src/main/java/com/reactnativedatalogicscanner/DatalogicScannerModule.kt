package com.reactnativedatalogicscanner

import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.DecodeException
import com.datalogic.decode.ReadListener
import com.datalogic.decode.configuration.ScannerProperties
import com.datalogic.device.ErrorManager
import com.datalogic.extension.selfshopping.cradle.Cradle
import com.datalogic.extension.selfshopping.cradle.CradleInsertionListener
import com.datalogic.extension.selfshopping.cradle.CradleManager
import com.datalogic.extension.selfshopping.cradle.CradleType
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.datalogic.extension.selfshopping.cradle.joyatouch.CradleJoyaTouch
import com.datalogic.extension.selfshopping.cradle.joyatouch.LockAction

class DatalogicScannerModule(reactContext: ReactApplicationContext) :
  ReactContextBaseJavaModule(reactContext), CradleInsertionListener {

  private var barcodeManagerOnce: BarcodeManager? = null
  private var listenerOnce: ReadListener? = null
  private var barcodeManagerContinuous: BarcodeManager? = null
  private var listenerContinuous: ReadListener? = null
  private var cradleJoyaTouch: CradleJoyaTouch? = null

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
      return
    }

    try {
      barcodeManagerContinuous = BarcodeManager().apply {
        setScannerProperties(this)
      }
      ErrorManager.enableExceptions(true)
      listenerContinuous = ReadListener { decodeResult ->
        emitBarcode(decodeResult.text)
        promise.resolve(decodeResult.text)
      }
      barcodeManagerContinuous!!.addReadListener(listenerContinuous)
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

    val cradle = CradleManager.getCradle()
    if (cradle == null || cradle.type != CradleType.JOYA_TOUCH_CRADLE) {
      return false
    }

    cradleJoyaTouch = cradle as CradleJoyaTouch

    return true
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

    cradleJoyaTouch?.apply {
      removeCradleInsertionListener(this@DatalogicScannerModule)
      addCradleInsertionListener(this@DatalogicScannerModule)
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
