package ng.haayaa.thermalprint

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.hardware.usb.*
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import kotlin.math.roundToInt
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import expo.modules.kotlin.AppContext
import java.util.LinkedList
import java.util.Queue
import java.util.UUID

class ThermalPrintModule : Module() {
  private var printerConnection: UsbDeviceConnection? = null
  private var printerInterface: UsbInterface? = null
  private var endpoint: UsbEndpoint? = null

  companion object {
    const val USB_PERMISSION_ACTION = "ng.haayaa.thermalprint.USB_PERMISSION"
  }

  override fun definition() = ModuleDefinition {
    Name("ThermalPrint")

    Constants(
      "PI" to Math.PI
    )

    Events("onChange")
    Events("onGenerateBytecode")

    Function("hello") {
      "Hello world! 👋"
    }

    AsyncFunction("setValueAsync") { value: String ->
      sendEvent("onChange", mapOf(
        "value" to value
      ))
    }

    AsyncFunction("generateBytecodeAsync") { base64String: String, printerWidth: Int, chunkSize: Int ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, chunkSize)

      lines
    }

    AsyncFunction("sendToUsbThermalPrinterAsync") { base64String: String, printerWidth: Int, chunkSize: Int ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, chunkSize)

      connectToPrinter(lines);
    }

    AsyncFunction("sendToBluetoothThermalPrinterAsync") { base64String: String, printerWidth: Int, chunkSize: Int,  deviceMac: String, serviceUUID: String, characteristicUUID: String ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, chunkSize)

      printWithBluetooth(
        appContext,lines, deviceMac, serviceUUID, characteristicUUID)
    }
  }

  private fun convertTo1BitMonochrome(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val bytesPerRow = (width + 7) / 8 // Number of bytes per row (8 pixels per byte)

    val monochromeData = ByteArray(bytesPerRow * height)

    // Loop through each pixel, converting to 1-bit monochrome
    for (y in 0 until height) {
      for (x in 0 until width) {
        val pixel = bitmap.getPixel(x, y)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)

        // Convert to grayscale using the weighted average method
        val grayscaleValue = (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()

        // Set bit to 0 if pixel is dark, 1 if bright (inverted for printing)
        if (grayscaleValue < 128) {
          val byteIndex = y * bytesPerRow + (x / 8)
          monochromeData[byteIndex] = monochromeData[byteIndex].toInt().or(1 shl (7 - (x % 8))).toByte()
        }
      }
    }

    return monochromeData
  }

  private fun prepareImageForThermalPrinter(base64ImageString: String, printerWidth: Int, chunkSize: Int): List<ByteArray> {
    // 1. Decode Base64 image
    val decodedString: ByteArray = Base64.decode(base64ImageString, Base64.DEFAULT)
    val decodedBitmap: Bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)

    // 2. Scale the bitmap if it exceeds the printer's width
    val scaledBitmap = if (decodedBitmap.width > printerWidth) {
      val aspectRatio = decodedBitmap.height.toFloat() / decodedBitmap.width
      val newHeight = (printerWidth * aspectRatio).roundToInt()
      Bitmap.createScaledBitmap(decodedBitmap, printerWidth, newHeight, true)
    } else {
      decodedBitmap
    }

    // 3. Convert to 1-bit monochrome
    val printerData: ByteArray = convertTo1BitMonochrome(scaledBitmap)

    // 4. Calculate bytes per line
    val bytesPerLine = (printerWidth + 7) / 8

    // 5. Create the image header (ESC/POS command)
    val header = ByteArray(8)
    header[0] = 0x1D // GS command
    header[1] = 0x76 // 'v'
    header[2] = 0x30 // '0'
    header[3] = 0x00 // Normal mode (no scaling)

    // Width of image in bytes (low byte, high byte)
    header[4] = (bytesPerLine % 256).toByte() // Low byte of width
    header[5] = (bytesPerLine / 256).toByte() // High byte of width

    // Height of image in pixels (low byte, high byte)
    header[6] = (scaledBitmap.height % 256).toByte() // Low byte of height
    header[7] = (scaledBitmap.height / 256).toByte() // High byte of height

    // 6. Split into lines (each line will be bytesPerLine wide)
    val imageData = printerData.asSequence()
      .chunked(bytesPerLine)
      .map { it.toByteArray() }
      .toList()

    // 7. Combine header and image data into larger chunks
    val chunkedData = imageData.chunked(chunkSize).map { chunk ->
      // Create a larger byte array to hold the combined data
      val combinedChunk = chunk.fold(ByteArray(0)) { acc, byteArray ->
        acc + byteArray // Concatenate the byte arrays
      }
      combinedChunk
    }

    // 8. Add header to the first chunk if necessary (you can decide if it’s for the first or all chunks)
    val result = mutableListOf<ByteArray>()
    result.add(header) // Adding header to the first chunk
    result.addAll(chunkedData)

    return result
  }

  private fun connectToPrinter(bytecode: List<ByteArray>) {
    val usbManager = appContext.reactContext?.getSystemService(Context.USB_SERVICE) as UsbManager
    val deviceList = usbManager.deviceList
    var printerDevice: UsbDevice? = null

    // Automatically find the first suitable device (assuming only 1-2 devices are connected)
    deviceList?.values?.forEach { device ->
      val interfaceCount = device.interfaceCount

      // Iterate over the interfaces to find one that supports bulk transfer (typical for printers)
      for (i in 0 until interfaceCount) {
        val usbInterface = device.getInterface(i)
        for (j in 0 until usbInterface.endpointCount) {
          val endpoint = usbInterface.getEndpoint(j)
          if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK &&
            endpoint.direction == UsbConstants.USB_DIR_OUT) {
            // Found a device that can support printing, store its reference
            printerDevice = device
            printerInterface = usbInterface
            this.endpoint = endpoint
            break
          }
        }
      }
      if (printerDevice != null) return@forEach // Exit the loop once the printer is found
    }

    if (printerDevice != null) {
      // Check if we have permission to access the device
      if (!usbManager.hasPermission(printerDevice)) {
        // Request permission if not already granted
        val permissionIntent = PendingIntent.getBroadcast(
          appContext.reactContext,
          0,
          Intent(USB_PERMISSION_ACTION),
          PendingIntent.FLAG_IMMUTABLE // Ensure compatibility with Android 12+
        )
        usbManager.requestPermission(printerDevice, permissionIntent)
        return // Return here and wait for the permission callback
      }

      // Open a connection to the device
      usbManager.openDevice(printerDevice)?.also { connection ->
        if (connection.claimInterface(printerInterface, true)) {
          printerConnection = connection
          // Send data to the printer in chunks
          bytecode.forEach { chunk ->
            printerConnection?.bulkTransfer(endpoint, chunk, chunk.size, 1000) // 1000 ms timeout
          }
        } else {
          connection.close()
        }
      } ?: throw Exception("Unable to open connection to the printer.")
    } else {
      throw Exception("No compatible printer found.")
    }
  }

  @SuppressLint("MissingPermission")
  private fun printWithBluetooth(
    context: AppContext,
    lines: List<ByteArray>,
    deviceMac: String,
    serviceUUID: String,
    characteristicUUID: String
  ) {
    // Initialize Bluetooth adapter
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
      throw Exception("Bluetooth is disabled or not supported on this device")
    }

    // Get the Bluetooth device using the provided MAC address
    val bluetoothDevice: BluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceMac)
      ?: throw Exception("Unable to find the Bluetooth device with MAC: $deviceMac")

    // Queue to manage the data writes
    val writeQueue: Queue<ByteArray> = LinkedList(lines)

    // Retry logic variables
    var connectionAttempts = 0
    val maxAttempts = 3

    // BluetoothGattCallback with retry logic
    val bluetoothGattCallback = object : BluetoothGattCallback() {
      override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        Log.d("ThermalPrint Log", "Connection State Change: Status = $status, NewState = $newState")
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
          Log.d("ThermalPrint Log", "Connected to device, discovering services...")
          gatt?.discoverServices()
        } else if (status == 133 || newState == BluetoothProfile.STATE_DISCONNECTED) {
          Log.d("ThermalPrint Log", "Disconnected from device.")

          // Retry connection if it's a GATT_ERROR 133 or a disconnection
          if (connectionAttempts < maxAttempts) {
            connectionAttempts++
            Log.d("ThermalPrint Log", "Retrying connection attempt $connectionAttempts/$maxAttempts...")
            val handler = Handler(Looper.getMainLooper())
            handler.postDelayed({
              gatt?.connect()
            }, 2000) // 2-second delay before retrying
          } else {
            Log.e("ThermalPrint Log", "Max connection attempts reached. Closing connection.")
            gatt?.close()
          }
        } else {
          Log.e("ThermalPrint Log", "Connection failed with status: $status")
          gatt?.close()
        }
      }

      override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
          val service = gatt?.getService(UUID.fromString(serviceUUID))
          val characteristic = service?.getCharacteristic(UUID.fromString(characteristicUUID))

          if (characteristic == null) {
            throw Exception("Characteristic not found")
          }

          Log.d("ThermalPrint", "Writing data to the printer...")
          writeNextCharacteristic(gatt, characteristic, writeQueue)
        }
      }

      override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
          writeNextCharacteristic(gatt, characteristic, writeQueue)
        } else {
          Log.e("ThermalPrint", "Failed to write characteristic with status: $status")
        }
      }
    }

    // Initiate connection to the Bluetooth device
    val bluetoothGatt = bluetoothDevice.connectGatt(context.reactContext, false, bluetoothGattCallback)

    bluetoothGatt?.let {
      if (!it.connect()) {
        Log.d("ThermalPrint Log", "Retrying initial connection...")
        it.disconnect()
        it.connect()
      }
    } ?: throw Exception("Failed to connect to the printer")
  }

  // Helper function to write the next byte array from the queue
  @SuppressLint("MissingPermission")
  private fun writeNextCharacteristic(
    gatt: BluetoothGatt?,
    characteristic: BluetoothGattCharacteristic?,
    writeQueue: Queue<ByteArray>
  ) {
    if (writeQueue.isNotEmpty() && characteristic != null) {
      characteristic.value = writeQueue.poll()
      gatt?.writeCharacteristic(characteristic)
    }
  }
}