package com.reactnativedatalogicscanner

import android.util.Log
import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.DecodeException
import com.datalogic.decode.ReadListener
import com.datalogic.device.ErrorManager
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule

class DatalogicScannerModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  private var barcodeManagerOnce: BarcodeManager? = null
  private var listenerOnce: ReadListener? = null
  private var barcodeManagerContinuous: BarcodeManager? = null
  private var listenerContinuous: ReadListener? = null

  override fun getName(): String {
    return "DatalogicScanner"
  }

  private fun emitBarcode(barcode: String) {
    val params = Arguments.createMap()
    params.putString("barcode", barcode)
    reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      .emit("BarcodeScanned", params)
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
    Log.d("KENNETH", "scanOnce()")
    try {
      barcodeManagerOnce = BarcodeManager()
      Log.d("KENNETH", "-- Initialized BarcodeManager()")
      ErrorManager.enableExceptions(true)
      Log.d("KENNETH", "-- Enabled exceptions")
      listenerOnce = ReadListener { decodeResult ->
        Log.d("KENNETH", "-- Received result: " + decodeResult.text)
        promise.resolve(decodeResult.text)
        Log.d("KENNETH", "-- Resolved promise")
        barcodeManagerOnce?.removeReadListener(listenerOnce)
        Log.d("KENNETH", "-- Remove listener")
        barcodeManagerOnce = null
        Log.d("KENNETH", "-- Removed BarcodeManager")
      }
      Log.d("KENNETH", "-- Created listener")
      val added = barcodeManagerOnce!!.addReadListener(listenerOnce)
      Log.d("KENNETH", "-- Added listener: $added")
    } catch (de: DecodeException) {
      Log.d("KENNETH", "DecodeException: [${de.error_number}] ${de.message}")
      promise.reject(de)
    } catch (e: Exception) {
      Log.d("KENNETH", "Exception: " + e.message)
      promise.reject(e)
    }
  }

  @ReactMethod
  fun startScanning(promise: Promise) {
    if (barcodeManagerContinuous != null) {
      return
    }

    try {
      barcodeManagerContinuous = BarcodeManager()
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
}
