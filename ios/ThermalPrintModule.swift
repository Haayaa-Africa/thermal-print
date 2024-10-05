import ExpoModulesCore
import UIKit

public class ThermalPrintModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ThermalPrint")

    Constants([
      "PI": Double.pi
    ])

    Events("onChange")
      
    Events("onGenerateBytecode")

    Function("hello") {
      return "Hello world! 👋"
    }

    AsyncFunction("setValueAsync") { (value: String) in
      self.sendEvent("onChange", [
        "value": value
      ])
    }

    AsyncFunction("generateBytecodeAsync") { (base64String: String) -> Data in
      // Step 1: Decode base64 string to UIImage
      guard let imageData = Data(base64Encoded: base64String),
            let image = UIImage(data: imageData) else {
        throw NSError(domain: "ThermalPrintModule", code: 0, userInfo: [NSLocalizedDescriptionKey: "Invalid base64 string"])
      }

      // Step 2: Convert UIImage to black & white (1-bit image)
        guard let bitmapData = self.convertToEscPosFormat(image: image) else {
          throw NSError(domain: "ThermalPrintModule", code: 0, userInfo: [NSLocalizedDescriptionKey: "Failed to convert image to monochrome"])
        }

        return bitmapData
    }
  }

    // Helper function to convert image to monochrome
    // Helper function to convert image to 1-bit black and white
    private func convertTo1BitMonochrome(image: UIImage) -> [UInt8]? {
      guard let cgImage = image.cgImage else { return nil }
      
      let width = cgImage.width
      let height = cgImage.height
      let bytesPerRow = (width + 7) / 8 // Each byte contains 8 pixels

      var bitmapData = [UInt8](repeating: 0, count: bytesPerRow * height)

      // Convert to grayscale first
      let colorSpace = CGColorSpaceCreateDeviceGray()
      let context = CGContext(data: nil,
                              width: width,
                              height: height,
                              bitsPerComponent: 8,
                              bytesPerRow: width,
                              space: colorSpace,
                              bitmapInfo: CGImageAlphaInfo.none.rawValue)

      context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
      
      guard let grayscaleImage = context?.makeImage() else { return nil }
      
      // Process the grayscale image into 1-bit data by thresholding
      let pixelData = grayscaleImage.dataProvider?.data
      let data = CFDataGetBytePtr(pixelData)
      
      for y in 0..<height {
        for x in 0..<width {
          let pixelIndex = y * width + x
          let grayscaleValue = data![pixelIndex]
          
          // Threshold to convert to black or white
          if grayscaleValue < 128 {
            // Set pixel to black (1)
            let byteIndex = (y * bytesPerRow) + (x / 8)
            let bitIndex = 7 - (x % 8)
            bitmapData[byteIndex] |= (1 << bitIndex)
          }
        }
      }
      
      return bitmapData
    }

    // Helper function to convert image to ESC/POS printable format
    private func convertToEscPosFormat(image: UIImage) -> Data? {
        guard let bitData = convertTo1BitMonochrome(image: image) else { return nil }

        let width = image.cgImage!.width
        let height = image.cgImage!.height
        let bytesPerRow = (width + 7) / 8

        var escPosData = Data()

        // Process each line (row by row)
        for y in 0..<height {
            // ESC/POS Command header for each line
            let header: [UInt8] = [0x1D, 0x76, 0x30, 0x00]
            escPosData.append(contentsOf: header)

            // Image width in bytes for the current row
            escPosData.append(UInt8(width % 256))      // Width low byte
            escPosData.append(UInt8(width / 256))      // Width high byte
            
            // Height is set to 1 pixel because we're sending it line by line
            escPosData.append(UInt8(1))                // Height low byte (1 pixel)
            escPosData.append(UInt8(0))                // Height high byte
            
            // Add the bitmap data for this row
            let startByteIndex = y * bytesPerRow
            let endByteIndex = startByteIndex + bytesPerRow
            escPosData.append(contentsOf: bitData[startByteIndex..<endByteIndex])
            
            // No line feed here, as the printer moves to the next line automatically
        }

        return escPosData
    }
}
