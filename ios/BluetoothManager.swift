//
//  BluetoothManager.swift
//  Pods
//
//  Created by BenQoder on 04/12/2024.
//

import CoreBluetooth
import Foundation

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
    
    func requestBluetoothPermissions() {
        // Permissions are handled in the app's Info.plist for iOS.
    }
    
    func startScanning(onDeviceFound: @escaping ([Dictionary<String, String>]) -> Void) {
        self.onDeviceFound = onDeviceFound
        discoveredPeripherals.removeAll()
        centralManager.scanForPeripherals(withServices: nil, options: nil)
    }
    
    func stopScanning() -> Bool {
        guard centralManager.isScanning else {
            print("Bluetooth scanning is not currently active.")
            return true
        }
        
        centralManager.stopScan()
        return true
    }
    
    func connectToDevice(identifier: UUID) async throws -> Bool {

        guard let peripheral = discoveredPeripherals.first(where: { $0.identifier == identifier }) else {
            throw NSError(domain: "BluetoothManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "Device not found"])
        }
        
        print("Connecting to \(peripheral.name ?? "Unknown")...")

        return try await withCheckedThrowingContinuation { continuation in
            // Keep track of the peripheral for the continuation
            self.onConnectSuccess = { (connectedPeripheral: CBPeripheral) in
                if connectedPeripheral.identifier == identifier {
                    continuation.resume(returning: true)
                }
            }

            self.onConnectFailure = { error in
                continuation.resume(throwing: error)
            }

            centralManager.connect(peripheral, options: nil)
        }
    }
    
    var onDisconnectSuccess: (() -> Void)?
    var onDisconnectFailure: ((Error) -> Void)?
    
    func disconnect() async throws -> Bool {
        guard let peripheral = connectedPeripheral else {
            print("No device is currently connected to disconnect.")
            return true;
        }
        
        return try await withCheckedThrowingContinuation { continuation in
            
            // Handle disconnection events
            self.onDisconnectSuccess = {
                continuation.resume(returning: true)
            }
            
            self.onDisconnectFailure = { error in
                continuation.resume(throwing: error)
            }
            
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }
    
    func printWithDevice(data: [[UInt8]]) async throws -> Bool {
        guard let peripheral = connectedPeripheral else {
            print("No connected device")
            throw NSError(domain: "BluetoothManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "No connected device"])
        }
        
        print("Print count: \(data.count)")
        
        for chunk in data {
            try await Task.sleep(nanoseconds: 10_000_000)
            try await sendPrintDataAsync(peripheral: peripheral, data: Data(chunk))
        }
        
        return true;
    }
    
    func getAllowedMtu() -> Int {
        guard let peripheral = connectedPeripheral else {
            print("No connected device")
            return 20
        }
        
        let mtu = peripheral.maximumWriteValueLength(for: .withoutResponse)
        
        return mtu
    }
    
    private func sendPrintData(peripheral: CBPeripheral, data: Data) {
        guard let characteristic = writableCharacteristics.first else {
            print("No writable characteristic available")
            return
        }
        
        peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
    }
    
    private func sendPrintDataAsync(peripheral: CBPeripheral, data: Data) async throws {
        return try await withCheckedThrowingContinuation { continuation in
            guard let characteristic = writableCharacteristics.first else {
                continuation.resume(throwing: NSError(domain: "BluetoothManager", code: 404, userInfo: [NSLocalizedDescriptionKey: "No writable characteristic available"]))
                return
            }
            
            peripheral.writeValue(data, for: characteristic, type: .withoutResponse)
            
            // You could add additional checks for delegate-based confirmation if necessary
            continuation.resume()
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
    }
    
    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        if let error = error {
            print("Error while disconnecting from peripheral \(peripheral.name ?? "Unknown"): \(error.localizedDescription)")
        } else {
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
