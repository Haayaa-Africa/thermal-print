import ExpoModulesCore
import UIKit

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        var chunks = [[Element]]()
        var currentIndex = startIndex
        while currentIndex < endIndex {
            let nextIndex = index(currentIndex, offsetBy: size, limitedBy: endIndex) ?? endIndex
            chunks.append(Array(self[currentIndex..<nextIndex]))
            currentIndex = nextIndex
        }
        return chunks
    }
}

public class ThermalPrintModule: Module {

    private var bluetoothManager: BluetoothManager? = nil
    
    public func definition() -> ModuleDefinition {
        
        Name("ThermalPrint")
        
        Events("onGenerateBytecode")
        
        AsyncFunction("generateBytecodeAsync") { (base64String: String, printerWidth: Int, mtuSize: Int, promise: Promise) in
            
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString:base64String,
                printerWidth:printerWidth,
                mtuSize:mtuSize
            )
            
            promise.resolve(bitmapData)
        }
        
        AsyncFunction("generateBytecodeBase64Async") { (base64String: String, printerWidth: Int, mtuSize: Int, promise: Promise) in
            
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString:base64String,
                printerWidth:printerWidth,
                mtuSize:mtuSize
            )
            
            let base64String = prepareImageForBase64ThermalPrinter(lines: bitmapData)
            
            promise.resolve(base64String)
        }
        
        
        /// NEW BLUETOOTH FUNCTIONS
        
        Events("newDevicesFound")
        
        AsyncFunction("initializeBluetooth") {
            if bluetoothManager == nil {
                bluetoothManager = BluetoothManager()
            }
        }
        
        AsyncFunction("scanForBlueToothDevices") {(promise: Promise) in
            do {
                let scanning = try scanForDevices{ devices in
                    self.sendEvent("newDevicesFound", [
                        "devices": devices
                    ])
                }
                
                promise.resolve(scanning)
            } catch {
                promise.reject(error)
            }
        }
        
        AsyncFunction("suspendScanForBlueToothDevices") {(promise: Promise) in
            Task{
                let stopScanning = bluetoothManager?.stopScanning();
                promise.resolve(stopScanning)
            }
        }
        
        AsyncFunction("connectToBlueToothDevice") { (deviceId: String, promise: Promise) in
            print("Connecting to \(deviceId)")
            Task {
                do {
                    let connection = try await self.connectToDevice(deviceId: deviceId)
                    promise.resolve(connection)
                } catch {
                    promise.reject(Exception(name: "Device Not Connected", description: "Could not connect to device") as Error)
                }
            }
        }
        
        AsyncFunction("disconnectFromBlueToothDevice"){(promise: Promise) in
            Task{
                do {
                    promise.resolve(try await bluetoothManager?.disconnect())
                } catch {
                    promise.reject(Exception(name: "Device Not Disconnected", description: "Could not disconnect device") as Error)
                }
            }
        }
        
        AsyncFunction("sendToBluetoothThermalPrinterAsync") { (base64String: String, printerWidth: Int, promise: Promise) in
            
            let mtuSize = bluetoothManager?.getAllowedMtu() ?? 20
            
            let bitmapData = self.prepareImageForThermalPrinter(
                base64ImageString: base64String,
                printerWidth: printerWidth,
                mtuSize: mtuSize
            )
            
            print("Printing With Length \(bitmapData.count) bytes and \(mtuSize)")
            
            Task {
                let printed = await printWithDevice(lines: bitmapData)
                
                if printed {
                    promise.resolve(true)
                } else {
                    promise.reject(Exception(name: "Printing Error", description: "Could not print") as Error)
                }
            }
        }
    }

    func convertTo1BitMonochrome(bitmap: UIImage, maxWidth: Int) -> [UInt8] {
        guard let cgImage = bitmap.cgImage else { return [] }
        let width = cgImage.width
        let height = cgImage.height
        let bytesPerRow = (width + 7) / 8

        var monochromeData = [UInt8](repeating: 0, count: bytesPerRow * height)

        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 4 * width, space: colorSpace, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)
        
        context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
        
        guard let pixelBuffer = context?.data else { return [] }
        let pixels = pixelBuffer.bindMemory(to: UInt8.self, capacity: width * height * 4)

        for y in 0..<height {
            for x in 0..<width {
                let offset = (y * width + x) * 4
                let r = pixels[offset]
                let g = pixels[offset + 1]
                let b = pixels[offset + 2]

                // Convert to grayscale using the weighted average method
                let grayscaleValue = Int(0.299 * Double(r) + 0.587 * Double(g) + 0.114 * Double(b))

                // Set bit to 0 if pixel is dark, 1 if bright (inverted for printing)
                if grayscaleValue < 128 {
                    let byteIndex = y * bytesPerRow + (x / 8)
                    monochromeData[byteIndex] |= (1 << (7 - (x % 8)))
                }
            }
        }

        return monochromeData
    }
    
    func prepareImageForBase64ThermalPrinter(lines: [[UInt8]])-> [String]{
        
        let base64Strings = lines.map{ Data($0).base64EncodedString() }
        
        return base64Strings
    }

    func prepareImageForThermalPrinter(base64ImageString: String, printerWidth: Int, mtuSize: Int) -> [[UInt8]] {
        // 1. Decode Base64 image
        guard let decodedData = Data(base64Encoded: base64ImageString),
              let decodedImage = UIImage(data: decodedData) else {
            return []
        }

        // 2. Scale the bitmap if it exceeds the printer's width
        let scaledImage: UIImage
        if let cgImage = decodedImage.cgImage, cgImage.width > printerWidth {
            let aspectRatio = CGFloat(cgImage.height) / CGFloat(cgImage.width)
            let newHeight = Int(CGFloat(printerWidth) * aspectRatio)
            let newSize = CGSize(width: printerWidth, height: newHeight)
            UIGraphicsBeginImageContext(newSize)
            decodedImage.draw(in: CGRect(origin: .zero, size: newSize))
            scaledImage = UIGraphicsGetImageFromCurrentImageContext() ?? decodedImage
            UIGraphicsEndImageContext()
        } else {
            scaledImage = decodedImage
        }

        // 3. Convert to 1-bit monochrome
        let printerData = convertTo1BitMonochrome(bitmap: scaledImage, maxWidth: printerWidth)

        // 4. Calculate bytes per line
        let bytesPerLine = (printerWidth + 7) / 8

        // 5. Create the image header (ESC/POS command)
        var header = [UInt8](repeating: 0, count: 8)
        header[0] = 0x1D // GS command
        header[1] = 0x76 // 'v'
        header[2] = 0x30 // '0'
        header[3] = 0x00 // Normal mode (no scaling)

        // Width of image in bytes (low byte, high byte)
        header[4] = UInt8(bytesPerLine % 256) // Low byte of width
        header[5] = UInt8(bytesPerLine / 256) // High byte of width

        // Height of image in pixels (low byte, high byte)
        header[6] = UInt8(scaledImage.size.height.truncatingRemainder(dividingBy: 256)) // Low byte of height
        header[7] = UInt8(scaledImage.size.height / 256) // High byte of height

        // 6. Split into lines (each line will be bytesPerLine wide)
        var imageData = [[UInt8]]()
        for y in stride(from: 0, to: printerData.count, by: bytesPerLine) {
            let lineData = Array(printerData[y..<min(y + bytesPerLine, printerData.count)])
            imageData.append(lineData)
        }

        // 7. Combine header and image data into larger chunks based on MTU size
        var chunkedData = [[UInt8]]()
        var currentChunk = [UInt8]()
        var remainingMtu = mtuSize

        // Add header as the first chunk
        currentChunk.append(contentsOf: header)
        remainingMtu -= header.count

        for line in imageData {
            if line.count <= remainingMtu {
                currentChunk.append(contentsOf: line)
                remainingMtu -= line.count
            } else {
                chunkedData.append(currentChunk)
                currentChunk = line
                remainingMtu = mtuSize - line.count
            }
        }

        // Add the last chunk if any
        if !currentChunk.isEmpty {
            chunkedData.append(currentChunk)
        }


        return chunkedData
    }
    
    private func scanForDevices(onDeviceFound: @escaping ([[String: String]]) -> Void) throws -> Bool {
        guard bluetoothManager != nil else {
            throw Exception(
                name: "BluetoothManagerNotInitialized",
                description: "BluetoothManager is not initialized"
            )
        }
        
        guard bluetoothManager!.isBluetoothSupported() else {
            print("Bluetooth is not supported on this device")
            return false
        }
        
        guard bluetoothManager!.isBluetoothEnabled() else {
            print("Please enable Bluetooth")
            return false
        }
        
        // Start scanning for devices
        bluetoothManager!.startScanning { devices in
            onDeviceFound(devices)
        }
        
        return true
    }
    
    private func connectToDevice(deviceId: String) async throws -> Bool {

        guard bluetoothManager != nil else {
            throw Exception(
                name: "BluetoothManagerNotInitialized",
                description: "BluetoothManager is not initialized"
            )
        }
        
        guard bluetoothManager!.isBluetoothSupported() else {
            print("Bluetooth is not supported on this device")
            return false
        }
        
        guard let uuid = UUID(uuidString: deviceId) else {
            print("Invalid UUID string: \(deviceId)")
            return false
        }
        
        do {
            return try await bluetoothManager!.connectToDevice(identifier: uuid)
        } catch {
            print("Failed to connect to device: \(error)")
            return false // Connection failed
        }
    }
    
    private func printWithDevice(lines: [[UInt8]]) async -> Bool{
        guard let manager = bluetoothManager else {
            print("BluetoothManager is not initialized")
            return false
        }
        
        guard manager.isBluetoothSupported() else {
            print("Bluetooth is not supported on this device")
            return false
        }
        
        do {
            return try await manager.printWithDevice(data: lines)
        } catch {
            print("Failed to print: \(error)")
            return false
        }
        
    }
}
