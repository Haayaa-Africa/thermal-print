//
//  BluetoothManager.swift
//  Pods
//
//  Created by BenQoder on 04/12/2024.
//

import CoreBluetooth
import Foundation
import ExpoModulesCore

class BluetoothManager: NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {
    private var centralManager: CBCentralManager!
    private var discoveredPeripherals: [CBPeripheral] = []
    private var connectedPeripheral: CBPeripheral?
    private var writableCharacteristics: [CBCharacteristic] = []
    private var mtu: Int = 20
    private let knownWritableUUIDs = [
        CBUUID(string: "00002AF1-0000-1000-8000-00805F9B34FB")
    ]
    
    var onDeviceFound: (([Dictionary<String, String>]) -> Void)?
    
    let serialQueue: DispatchQueue = DispatchQueue(label: "com.benqoder.bluetooth.serialQueue");
    
    var onConnectSuccess: ((CBPeripheral) -> Void)?
    var onConnectFailure: ((Error) -> Void)?
    
    override init() {
        super.init()
        centralManager = CBCentralManager(delegate: self, queue: nil)
    }
    
    func isBluetoothSupported() -> Bool {
        return centralManager.state != .unsupported
    }
    
    func isBluetoothEnabled() -> Bool {
        return centralManager.state == .poweredOn
    }
    
    func requestBluetoothPermissions() -> Bool {
        if #available(iOS 13.0, *) {
            return CBManager.authorization == .allowedAlways
        } else {
            return CBPeripheralManager.authorizationStatus() == .authorized
        }
    }
    
    func startScanning(onDeviceFound: @escaping ([Dictionary<String, String>]) -> Void, promise: Promise) {
        self.onDeviceFound = onDeviceFound
        discoveredPeripherals.removeAll()
        centralManager.scanForPeripherals(withServices: nil, options: nil)
        promise.resolve(true)
    }
    
    func stopScanning(promise: Promise) {
        guard centralManager.isScanning else {
            print("Bluetooth scanning is not currently active.")
            promise.resolve(true)
            return
        }
        
        centralManager.stopScan()
        promise.resolve(true)
        return
    }
    
    func connectToDevice(identifier: UUID, promise: Promise) {

        guard let peripheral = discoveredPeripherals.first(where: { $0.identifier == identifier }) else {
            promise.reject(
                "BluetoothDeviceNotFound",
                "Could not find peripheral with identifier \(identifier)"
            )
            
            return
        }
        
        if connectedPeripheral?.identifier == identifier {
            promise.resolve(true)
            return
        }
        
        print("Connecting to \(peripheral.name ?? "Unknown")...")

        self.onConnectSuccess = { (connectedPeripheral: CBPeripheral) in
            if connectedPeripheral.identifier == identifier {
                promise.resolve(true)
            }
        }

        self.onConnectFailure = { error in
            promise.reject(
                "BluetoothDeviceConnectionFailed",
                "Could not connect to peripheral \(peripheral.name ?? "Unknown"). \(error)"
            )
        }

        centralManager.connect(peripheral, options: [CBConnectPeripheralOptionNotifyOnDisconnectionKey: true])
    }
    
    var onDisconnectSuccess: (() -> Void)?
    var onDisconnectFailure: ((Error) -> Void)?
    
    func disconnect(promise: Promise) {
        guard let peripheral = connectedPeripheral else {
            promise.resolve(true)
            return;
        }
        
        self.onDisconnectSuccess = {
            promise.resolve(true)
            return;
        }
        
        self.onDisconnectFailure = { error in
            promise.reject(
                "BluetoothDeviceDisconnectionFailed",
                "Failed to disconnect from device"
            )
            return;
        }
        
        centralManager.cancelPeripheralConnection(peripheral)

    }
    
    func getAllowedMtu() -> Int {
        guard let peripheral = connectedPeripheral else {
            print("No connected device")
            return 20
        }
        
        let mtu = peripheral.maximumWriteValueLength(for: .withoutResponse)
        
        return mtu
    }
    
    func printWithDevice(data: [[UInt8]], promise: Promise) {
        guard let peripheral = connectedPeripheral else {
            promise.reject("BluetoothDeviceDisconnected", "Device disconnected")
            return
        }

        print("Print count: \(data.count)")

        var successCount = 0
        var hasFailure = false
        let totalChunks = data.count

        // Function to check completion status
        func checkCompletion() {
            if hasFailure {
                promise.reject("PRINT_ERROR", "Failed to print a line.")
            } else if successCount == totalChunks {
                promise.resolve(true)
            }
        }

        // Process each chunk
        for chunk in data {
            if hasFailure {
                break // Stop processing further if a failure has already occurred
            }

            let chunkData = Data(chunk)
            sendPrintData(peripheral: peripheral, data: chunkData, onSuccess: {
                successCount += 1
                print("Chunk printed successfully (\(successCount)/\(totalChunks))")
                checkCompletion()
            }, onFailure: { error in
                print("Failed to print chunk: \(error.localizedDescription)")
                hasFailure = true
                checkCompletion()
            })

            // Short delay to avoid overloading
            Thread.sleep(forTimeInterval: 0.01)
        }

        // Polling loop to ensure all operations complete before promise resolution
        let startTime = Date()
        let timeout: TimeInterval = 10 // 10 seconds timeout
        while successCount < totalChunks && !hasFailure {
            Thread.sleep(forTimeInterval: 0.05) // Polling interval
            if Date().timeIntervalSince(startTime) > timeout {
                promise.reject("PRINT_TIMEOUT", "Printing timed out after \(timeout) seconds.")
                break
            }
        }
    }
    
    private func sendPrintData(peripheral: CBPeripheral, data: Data, onSuccess: @escaping () -> Void, onFailure: @escaping (Error) -> Void) {
        guard let characteristic = writableCharacteristics.first else {
            print("No writable characteristic available")
            onFailure(NSError(domain: "Bluetooth", code: 1, userInfo: [NSLocalizedDescriptionKey: "No writable characteristic available"]))
            return
        }

        peripheral.writeValue(data, for: characteristic, type: .withoutResponse)

        // Simulate success for now; actual success/failure is reported via didWriteValueFor delegate
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            // Call onSuccess or onFailure here based on actual write status
            onSuccess() // Simulated success
        }
    }
    
    
    // MARK: - CBCentralManagerDelegate
    
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            print("Bluetooth is not powered on or unavailable")
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        if !discoveredPeripherals.contains(peripheral) {
            if peripheral.name != nil {
                discoveredPeripherals.append(peripheral)
                
                let deviceList = discoveredPeripherals.map { peripheral in
                    [
                        "id": peripheral.identifier.uuidString,
                        "name": peripheral.name ?? "Unknown"
                    ]
                }
                
                onDeviceFound?(deviceList)
            }
        }
    }
    
    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        connectedPeripheral = peripheral
        peripheral.delegate = self
        peripheral.discoverServices(nil)
        onConnectSuccess?(peripheral)
    }
    
    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        if(error != nil) {
            onConnectFailure?(error!)
        }
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        if let error = error {
            onDisconnectFailure?(error)
            print("Error while disconnecting from peripheral \(peripheral.name ?? "Unknown"): \(error.localizedDescription)")
        } else {
            onDisconnectSuccess!()
            print("Successfully disconnected from peripheral \(peripheral.name ?? "Unknown")")
        }
        
        // Reset the connectedPeripheral variable
        if peripheral == connectedPeripheral {
            connectedPeripheral = nil
        }
    }
    
    // MARK: - CBPeripheralDelegate
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            print("Service discovery error: \(error.localizedDescription)")
            return
        }
        
        guard let services = peripheral.services else { return }
        
        for service in services {
            peripheral.discoverCharacteristics(nil, for: service)
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            print("Characteristic discovery error: \(error.localizedDescription)")
            return
        }
        
        guard let characteristics = service.characteristics else { return }
        
        for characteristic in characteristics {
            if knownWritableUUIDs.contains(characteristic.uuid) {
                writableCharacteristics.append(characteristic)
            }
        }
    }
    
    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            print("Error writing value: \(error.localizedDescription)")
        } else {
            print("Data written successfully to characteristic \(characteristic.uuid)")
        }
    }
}
