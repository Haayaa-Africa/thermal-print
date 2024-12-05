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

  async function manualyScanForBlueTooth() {
    console.log("Scanner Start", await ThermalPrint.scanForBlueToothDevices());
  }

  async function manualyScanForBlueToothSuspend() {
    console.log(
      "Scanner Suspend",
      await ThermalPrint.suspendScanForBlueToothDevices()
    );
  }

  // {"id": "86:67:7A:26:C0:A3", "name": "58MINI_C0A3"}

  const [devices, setDevices] = useState<ThermalPrint.Device[]>([]);

  const deviceConnected = useRef<ThermalPrint.Device>();

  React.useEffect(() => {
    ThermalPrint.bluetoothDevicesScannedListener((devices) => {
      if (deviceConnected.current) {
        return;
      }

      setDevices(devices.devices);
    });
  }, []);

  const printSomthing = async () => {
    console.log(deviceConnected.current);
    if (!deviceConnected.current) {
      Alert.alert("Select A device");

      return;
    }

    const result = await captureRef(viewRef, {
      result: "tmpfile",
      quality: 1,
      format: "png",
    });

    if (!result) {
      return Alert.alert("Could not capture");
    }

    const manipulate = await ImageManipulator.manipulateAsync(
      result,
      [
        {
          resize: {
            width: 384,
          },
        },
      ],
      {
        base64: true,
        format: ImageManipulator.SaveFormat.PNG,
        compress: 1,
      }
    );
    if (!manipulate.base64) {
      return Alert.alert("Cannot Manipulate");
    }

    ThermalPrint.sendToBluetoothThermalPrinterAsync(manipulate.base64, 384);
  };

  const checkPermission = async () => {
    if (Platform.OS === "ios") {
      return true;
    }
    if (
      Platform.OS === "android" &&
      PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
    ) {
      const apiLevel = parseInt(Platform.Version.toString(), 10);

      if (apiLevel < 31) {
        const granted = await PermissionsAndroid.request(
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION
        );
        return granted === PermissionsAndroid.RESULTS.GRANTED;
      }
      if (
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN &&
        PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT
      ) {
        const result = await PermissionsAndroid.requestMultiple([
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_SCAN,
          PermissionsAndroid.PERMISSIONS.BLUETOOTH_CONNECT,
          PermissionsAndroid.PERMISSIONS.ACCESS_FINE_LOCATION,
        ]);

        return (
          result["android.permission.BLUETOOTH_CONNECT"] ===
            PermissionsAndroid.RESULTS.GRANTED &&
          result["android.permission.BLUETOOTH_SCAN"] ===
            PermissionsAndroid.RESULTS.GRANTED &&
          result["android.permission.ACCESS_FINE_LOCATION"] ===
            PermissionsAndroid.RESULTS.GRANTED
        );
      }
    }

    Alert.alert("Permission Not Granted");

    return false;
  };

  const printViaPrinter = async () => {
    console.log("HHHH");

    if (Platform.OS === "ios") {
      return true;
    }

    const result = await captureRef(viewRef, {
      result: "tmpfile",
      quality: 1,
      format: "png",
    });

    if (!result) {
      return Alert.alert("Could not capture");
    }

    const manipulate = await ImageManipulator.manipulateAsync(
      result,
      [
        {
          resize: {
            width: 576,
          },
        },
      ],
      {
        base64: true,
        format: ImageManipulator.SaveFormat.PNG,
        compress: 1,
      }
    );

    if (!manipulate.base64) {
      return Alert.alert("Cannot Manipulate");
    }

    ThermalPrint.sendToUsbThermalPrinterAsync(manipulate.base64, 576, 5);
  };

  return (
    <View style={styles.container}>
      <Button title="Print with USB" onPress={printViaPrinter} />
      <Button title="Check Permission" onPress={checkPermission} />
      <Button title="Print with Bluetootha" onPress={printSomthing} />
      <Button
        title="Start Bluetooth"
        onPress={ThermalPrint.initializeBluetooth}
      />
      <Button
        title="Scan Bluetooth Devices"
        onPress={manualyScanForBlueTooth}
      />

      <Button
        title="Connect Bluetooth"
        onPress={async () => {
          const ourDevice = devices.find((d) => d.name === printerName);

          if (ourDevice) {
            deviceConnected.current = ourDevice;

            await ThermalPrint.connectToBlueToothDevice(ourDevice.id);
          }
        }}
      />

      <Button
        title="Disconnect Bluetooth"
        onPress={() => {
          deviceConnected.current = undefined;
          ThermalPrint.disconnectFromBlueToothDevice();
        }}
      />
      <Button
        title="Suspend Bluetooth Scan"
        onPress={manualyScanForBlueToothSuspend}
      />

      <View
        style={{
          width: 300,
          justifyContent: "center",
          borderWidth: 3,
          minHeight: 500,
          borderColor: "black",
          alignItems: "center",
          backgroundColor: "white",
        }}
        ref={viewRef}
      >
        <Image
          source={require("./assets/receipt.jpg")}
          style={{
            flex: 1,
          }}
          resizeMode="contain"
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    alignItems: "center",
    justifyContent: "center",
  },
});
