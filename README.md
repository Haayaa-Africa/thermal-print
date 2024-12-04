# ThermalPrint Module Documentation

## Overview

The **ThermalPrint** module provides utilities to interact with thermal printers via USB or Bluetooth. It supports generating bytecode for printing, sending data to printers, and managing Bluetooth device connections. It is designed for React Native applications and integrates seamlessly with native and Expo platforms.

---

## Installation

Ensure you have set up `expo-modules-core` and installed this module correctly in your React Native project.

```bash
npm install thermal-print expo-modules-core
```

---

## API Reference

### Functions

#### **`generateBytecodeAsync(value: string, printerWidth: number, mtuSize: number): Promise<Uint8Array[]>`**

Generates bytecode for printing.

- **Parameters:**
  - `value` (string): Text or content to be printed.
  - `printerWidth` (number): Width of the printer in dots.
  - `mtuSize` (number): Maximum Transmission Unit size.
- **Returns:** A promise resolving to an array of `Uint8Array` representing the bytecode.

---

#### **`generateBytecodeBase64Async(value: string, printerWidth: number, mtuSize: number): Promise<string[]>`**

Generates Base64 encoded bytecode.

- **Parameters:**
  - `value` (string): Text or content to be printed.
  - `printerWidth` (number): Width of the printer in dots.
  - `mtuSize` (number): Maximum Transmission Unit size.
- **Returns:** A promise resolving to a Base64 encoded string array.

---

#### **`sendToUsbThermalPrinterAsync(value: string, printerWidth: number, chunkSize: number): Promise<void>`**

Sends data to a USB thermal printer.

- **Parameters:**
  - `value` (string): Content to print (Base64 encoded).
  - `printerWidth` (number): Printer width in dots.
  - `chunkSize` (number): Size of data chunks sent to the printer.

---

#### **`addChangeListener(listener: (event: ChangeEventPayload) => void): Subscription`**

Adds a listener for change events.

- **Parameters:**
  - `listener` (function): Callback invoked on changes.
- **Returns:** A subscription object.

---

#### **`addGeneratedBytecodeListener(listener: (event: ChangeEventPayload) => void): Subscription`**

Adds a listener for bytecode generation events.

- **Parameters:**
  - `listener` (function): Callback invoked when bytecode is generated.
- **Returns:** A subscription object.

---

#### **`bluetoothDevicesScannedListener(listener: (event: DeviceFoundEvent) => void): EventSubscription`**

Adds a listener for Bluetooth device scan events.

- **Parameters:**
  - `listener` (function): Callback invoked when devices are discovered.
- **Returns:** A subscription object.

---

#### **`scanForBlueToothDevices(): Promise<void>`**

Scans for nearby Bluetooth devices.

---

#### **`connectToBlueToothDevice(deviceId: string): Promise<void>`**

Connects to a Bluetooth device.

- **Parameters:**
  - `deviceId` (string): ID of the device to connect to.

---

#### **`disconnectFromBlueToothDevice(): Promise<void>`**

Disconnects from the connected Bluetooth device.

---

#### **`sendToBluetoothThermalPrinterAsync(value: string, printerWidth: number): Promise<void>`**

Sends data to a Bluetooth thermal printer.

- **Parameters:**
  - `value` (string): Content to print (Base64 encoded).
  - `printerWidth` (number): Printer width in dots.

---

### Types

#### `DeviceFound`

Represents a discovered Bluetooth device.

- **Properties:**
  - `id` (string): The device's unique ID.
  - `name` (string): The name of the device.

#### `DeviceFoundEvent`

Event payload for discovered Bluetooth devices.

- **Properties:**
  - `devices` (DeviceFound[]): An array of discovered devices.

---

## Usage Example

Below is a simple implementation demonstrating the usage of the **ThermalPrint** module.

```tsx
import React, { useRef, useState } from "react";
import {
  Button,
  View,
  Alert,
  PermissionsAndroid,
  Platform,
} from "react-native";
import { captureRef } from "react-native-view-shot";
import * as ImageManipulator from "expo-image-manipulator";
import * as ThermalPrint from "thermal-print";

export default function App() {
  const viewRef = useRef<View>();
  const [devices, setDevices] = useState<ThermalPrint.DeviceFound[]>([]);

  // Scan for devices
  const scanDevices = () => ThermalPrint.scanForBlueToothDevices();

  // Print content
  const printContent = async () => {
    const imagePath = await captureRef(viewRef, { result: "tmpfile" });
    const manipulated = await ImageManipulator.manipulateAsync(imagePath, [
      { resize: { width: 384 } },
    ]);
    if (manipulated.base64) {
      await ThermalPrint.sendToBluetoothThermalPrinterAsync(
        manipulated.base64,
        384
      );
    }
  };

  return (
    <View>
      <Button title="Scan Devices" onPress={scanDevices} />
      <Button title="Print" onPress={printContent} />
    </View>
  );
}
```

---

## Notes

- Ensure appropriate permissions for Bluetooth are granted, especially on Android.
- Use the `captureRef` utility to capture a snapshot of a view for printing.
- Ensure the printer's width matches the width specified in the functions for accurate results.

This module simplifies the process of integrating thermal printing functionality into your React Native apps, offering comprehensive support for both USB and Bluetooth printers.
