package ng.haayaa.thermalprint

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
//import java.io.ByteArrayOutputStream

class ThermalPrintModule : Module() {
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

    AsyncFunction("generateBytecodeAsync") { base64String: String ->
      // Step 1: Decode base64 string to Bitmap
      val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
      val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

      // Step 2: Convert Bitmap to black & white (1-bit image)
      val bitmapData = convertTo1BitMonochrome(bitmap)

      bitmapData
    }
  }

  /**
   * Converts a Bitmap to a1-bit monochrome byte array.
   *
   * @param bitmap The input Bitmap.
   * @return A byte array representing the 1-bit monochrome image. Each byte
   *         represents a pixel and is either 0 or 255.
   */
  private fun convertTo1BitMonochrome(bitmap: Bitmap): ByteArray {
    val width = bitmap.width
    val height = bitmap.height
    val bytesPerRow = (width + 7) / 8

    val monochromeData = ByteArray(bytesPerRow * height)

    for (y in 0 until height){
      for (x in 0 until width) {
        val pixel = bitmap.getPixel(x, y)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        val grayscaleValue = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

        val byteIndex = y * bytesPerRow + (x / 8)
        monochromeData[byteIndex] = if (grayscaleValue < 128) 0.toByte() else 255.toByte()
      }
    }

    return monochromeData
  }
}