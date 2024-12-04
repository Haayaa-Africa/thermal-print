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
    
    func connectToDevice(identifier: UUID) {
        if let peripheral = discoveredPeripherals.first(where: { $0.identifier == identifier }) {
            centralManager.connect(peripheral, options: nil)
        }
    }
    
    func disconnect() {
        guard let peripheral = connectedPeripheral else {
            print("No device is currently connected to disconnect.")
            return
        }
        
        centralManager.cancelPeripheralConnection(peripheral)
    }
    
    func printWithDevice(data: [[UInt8]]) {
        guard let peripheral = connectedPeripheral else {
            print("No connected device")
            return
        }
        
        print("Print count: \(data.count)")
        
        for (index, chunk) in data.enumerated() {
            serialQueue.asyncAfter(deadline: .now() + Double(index) * 0.01) {
                self.sendPrintData(peripheral: peripheral, data: Data(chunk))
            }
        }
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
