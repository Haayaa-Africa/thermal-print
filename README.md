# ThermalPrint Module for React Native

A powerful and versatile library for React Native applications to handle thermal printing via Bluetooth and USB. This package provides an easy-to-use interface for capturing and printing content from your app.

## Features

- Scan and connect to Bluetooth thermal printers.
- Print via USB thermal printers.
- Generate and manipulate bytecode for printing.
- Capture views and print images as receipts.
- Handle platform-specific permissions seamlessly.

## Installation

To use this library in your project, install it via npm or yarn:

```bash
npm install thermal-print
# or
yarn add thermal-print
```

## Usage

Below is a comprehensive example of how to use the `ThermalPrint` module in your React Native application.

### Example Code

```tsx
import * as ImageManipulator from "expo-image-manipulator";
import React, { useRef, useState } from "react";
import {
  Alert,
  Button,
  PermissionsAndroid,
  Platform,
  StyleSheet,
  View,
  Image,
} from "react-native";
import { captureRef } from "react-native-view-shot";
import * as ThermalPrint from "thermal-print";

const printerName = "58MINI_C0A3";

export default function App() {
  const viewRef = useRef<View>();
  const [devices, setDevices] = useState<ThermalPrint.Device[]>([]);
  const deviceConnected = useRef<ThermalPrint.Device>();

  React.useEffect(() => {
    ThermalPrint.bluetoothDevicesScannedListener((devices) => {
      if (!deviceConnected.current) {
        setDevices(devices.devices);
      }
    });
  }, []);

  const checkPermission = async () => {
    if (Platform.OS === "ios") return true;

    const apiLevel = parseInt(Platform.Version.toString(), 10);
    if (apiLevel < 31) {
      const granted = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
      );
      return granted === PermissionsAndroid.RESULTS.GRANTED;
    }

    const result = await PermissionsAndroid.requestMultiple([
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
      PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
    ]);

    return (
      result["android.permission.BLUETOOTH_SCAN"] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      result["android.permission.BLUETOOTH_CONNECT"] ===
        PermissionsAndroid.RESULTS.GRANTED &&
      result["android.permission.ACCESS_FINE_LOCATION"] ===
        PermissionsAndroid.RESULTS.GRANTED
    );
  };

  const printSomthing = async () => {
    if (!deviceConnected.current) return Alert.alert("Select a device");

    const result = await captureRef(viewRef, {
      result: "tmpfile",
      quality: 1,
      format: "png",
    });
    if (!result) return Alert.alert("Could not capture");

    const manipulate = await ImageManipulator.manipulateAsync(
      result,
      [{ resize: { width: 384 } }],
      { base64: true, format: ImageManipulator.SaveFormat.PNG, compress: 1 }
    );
    if (!manipulate.base64) return Alert.alert("Cannot Manipulate");

    await ThermalPrint.sendToBluetoothThermalPrinterAsync(
      manipulate.base64,
      384
    );
  };

  return (
    <View style={styles.container}>
      <Button title="Check Permission" onPress={checkPermission} />
      <Button title="Print with Bluetooth" onPress={printSomthing} />
      <View ref={viewRef} style={styles.receiptContainer}>
        <Image source={require("./assets/receipt.jpg")} style={styles.image} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, justifyContent: "center", alignItems: "center" },
  receiptContainer: {
    width: 300,
    borderWidth: 3,
    borderColor: "black",
    minHeight: 500,
    backgroundColor: "white",
  },
  image: { flex: 1, resizeMode: "contain" },
});
```

## API Reference

### Bluetooth Functions

- `initializeBluetooth()`  
  Initializes the Bluetooth module.

- `scanForBlueToothDevices()`  
  Starts scanning for Bluetooth devices.

- `suspendScanForBlueToothDevices()`  
  Stops scanning for Bluetooth devices.

- `connectToBlueToothDevice(deviceId: string)`  
  Connects to a Bluetooth device by its ID.

- `disconnectFromBlueToothDevice()`  
  Disconnects the currently connected Bluetooth device.

- `sendToBluetoothThermalPrinterAsync(value: string, printerWidth: number)`  
  Sends data to a Bluetooth thermal printer.

### USB Functions

- `sendToUsbThermalPrinterAsync(value: string, printerWidth: number, chunkSize: number)`  
  Sends data to a USB thermal printer.

### Image Manipulation

- `generateBytecodeAsync(value: string, printerWidth: number, mtuSize: number): Promise<Uint8Array[]>`  
  Generates bytecode for a given string.

- `generateBytecodeBase64Async(value: string, printerWidth: number, mtuSize: number): Promise<string[]>`  
  Generates base64-encoded bytecode for a given string.

### Listeners

- `bluetoothDevicesScannedListener(listener: (event: DeviceFoundEvent) => void)`  
  Listens for newly found Bluetooth devices.

## Permissions

Ensure to request the necessary permissions for Android devices:

- `ACCESS_FINE_LOCATION`
- `BLUETOOTH_SCAN`
- `BLUETOOTH_CONNECT`

For iOS, no additional permissions are needed.

## Contribution

Contributions are welcome! Feel free to open issues or submit pull requests to improve the library.

## License

This library is licensed under the MIT License.
