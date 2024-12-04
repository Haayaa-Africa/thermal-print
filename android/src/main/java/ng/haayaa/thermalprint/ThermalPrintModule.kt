package ng.haayaa.thermalprint

import android.app.PendingIntent
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
import android.util.Log
import androidx.core.os.bundleOf
import expo.modules.kotlin.Promise

class ThermalPrintModule : Module() {
  private var printerConnection: UsbDeviceConnection? = null
  private var printerInterface: UsbInterface? = null
  private var endpoint: UsbEndpoint? = null

  private lateinit var bluetoothManager: BluetoothManager

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

    AsyncFunction("generateBytecodeAsync") { base64String: String, printerWidth: Int, mtuSize: Int ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, mtuSize)

      lines
    }

    AsyncFunction("generateBytecodeBase64Async") { base64String: String, printerWidth: Int, mtuSize: Int ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, mtuSize)

      val base64Lines = prepareImageForBase64ThermalPrinter(lines)

      base64Lines
    }

    AsyncFunction("sendToUsbThermalPrinterAsync") { base64String: String, printerWidth: Int, chunkSize: Int ->

      val lines = prepareImageForUsbThermalPrinter(base64String, printerWidth, chunkSize)

      connectToPrinter(lines);
    }

    /// NEW BLUETOOTH FUNCTIONS

    Events("newDeviceFound")

    AsyncFunction("scanForBlueToothDevices") {
      scanForDevices{
          devices -> this@ThermalPrintModule.sendEvent("newDeviceFound", bundleOf("devices"  to devices))
      }
    }

    AsyncFunction("connectToBlueToothDevice") { deviceId: String, promise: Promise ->
      promise.resolve(
        connectToBluetoothPrinterWithId(deviceId)
      )
    }

    AsyncFunction("disconnectFromBlueToothDevice") { promise: Promise ->
      promise.resolve(
        disconnect()
      )
    }

    AsyncFunction("sendToBluetoothThermalPrinterAsync") { base64String: String, printerWidth: Int ->

      val lines = prepareImageForThermalPrinter(base64String, printerWidth, bluetoothManager.getAllowedMtu() ?: 20)

      printWithBluetoothPrinter(lines)
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

  private fun prepareImageForThermalPrinter(base64ImageString: String, printerWidth: Int, mtuSize: Int): List<ByteArray> {
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

    // 7. Combine header and image data into chunks based on MTU size
    val chunkedData = mutableListOf<ByteArray>()
    var currentChunk = mutableListOf<Byte>()
    var remainingMtu = mtuSize

    // Add header as the first chunk
    currentChunk.addAll(header.asList())
    remainingMtu -= header.size

    for (line in imageData) {
      if (line.size <= remainingMtu) {
        currentChunk.addAll(line.asList())
        remainingMtu -= line.size
      } else {
        // Add the current chunk and start a new one
        chunkedData.add(currentChunk.toByteArray())
        currentChunk = mutableListOf<Byte>()
        currentChunk.addAll(line.asList())
        remainingMtu = mtuSize - line.size
      }
    }

    // Add the last chunk if any
    if (currentChunk.isNotEmpty()) {
      chunkedData.add(currentChunk.toByteArray())
    }

    return chunkedData
  }

  private fun prepareImageForBase64ThermalPrinter(lines: List<ByteArray>): List<String>{
    val base64Lines = lines.map { chunk ->
      Base64.encodeToString(chunk, Base64.DEFAULT)
    }

    return  base64Lines
  }

  private fun prepareImageForUsbThermalPrinter(base64ImageString: String, printerWidth: Int, maxChunkHeight: Int): List<ByteArray> {
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

    // Prepare the final list of byte arrays, split by height
    val byteArrayChunks = mutableListOf<ByteArray>()

    // 6. Split the bitmap into rows of `maxChunkHeight`
    var currentY = 0
    while (currentY < scaledBitmap.height) {
      // Get the actual height for this chunk
      val chunkHeight = minOf(maxChunkHeight, scaledBitmap.height - currentY)

      // Create the bitmap chunk
      val bitmapChunk = Bitmap.createBitmap(scaledBitmap, 0, currentY, scaledBitmap.width, chunkHeight)

      // Convert the chunk to 1-bit monochrome
      val chunkData = convertTo1BitMonochrome(bitmapChunk)

      // 7. Update the header for the chunk
      header[6] = (chunkHeight % 256).toByte() // Low byte of height
      header[7] = (chunkHeight / 256).toByte() // High byte of height

      // Add the header and chunk data to the list
      byteArrayChunks.add(header + chunkData)

      // Move to the next chunk
      currentY += chunkHeight
    }

    return byteArrayChunks
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
          // Send data to the printer with a delay between chunks
          sendDataToPrinterWithDelay(bytecode)
        } else {
          connection.close()
        }
      } ?: throw Exception("Unable to open connection to the printer.")
    } else {
      throw Exception("No compatible printer found.")
    }
  }

  private fun sendDataToPrinterWithDelay(bytecode: List<ByteArray>) {
    val maxChunkSize = 1024 // Use a smaller chunk size (e.g., 1024 bytes)
    val timeout = 5000 // Timeout for larger data transfers

    bytecode.forEach { chunk ->
      // Send each chunk and then wait before sending the next chunk
      val result = printerConnection?.bulkTransfer(endpoint, chunk, chunk.size, timeout)

      // Log the result of each transfer
      Log.d("ThermalPrint", "bulkTransfer result: $result for chunk size: ${chunk.size}")

      if (result == null || result < 0) {
        Log.e("ThermalPrint", "Failed to transfer data to printer, result: $result")
        throw Exception("Failed to transfer data to the printer")
      }

      // Add a small delay to give the printer time to process the data
      try {
        Thread.sleep(1) // Delay for 500 milliseconds (can adjust this delay)
      } catch (e: InterruptedException) {
        Log.e("ThermalPrint", "Thread was interrupted", e)
      }
    }
  }

  private fun scanForDevices (onDeviceFound: (List<Map<String, String>>) -> Unit) {
    val appContext = this.appContext.reactContext?.applicationContext;

    if (appContext === null) return;

    bluetoothManager = BluetoothManager(appContext)

    if (!bluetoothManager.isBluetoothSupported()) {
      Log.d("BLUETOOTH APP LOG", "Bluetooth is not supported on this device")
      return
    }

    val activity = this.appContext.currentActivity;

    if ( activity === null) return;

    bluetoothManager.requestBluetoothPermissions(activity)

    if (bluetoothManager.isBluetoothEnabled()){
      bluetoothManager.startScanning(onDeviceFound)
    }else {
      Log.d("BLUETOOTH APP LOG", "Please enable bluetooth")
    }
  }

  private fun connectToBluetoothPrinterWithId(deviceId: String){
    if (!bluetoothManager.isBluetoothEnabled()) return;

    bluetoothManager.connectToDevice(deviceId)
  }

  private fun printWithBluetoothPrinter(lines: List<ByteArray>){
    if (!bluetoothManager.isBluetoothEnabled()) return;

    bluetoothManager.printWithDevice(lines)
  }

  private fun disconnect(){
    bluetoothManager.disconnect()
  }
}