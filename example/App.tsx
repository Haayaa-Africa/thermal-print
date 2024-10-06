import { Buffer } from "buffer";
import * as ImageManipulator from "expo-image-manipulator";
import React, { useRef, useState } from "react";
import {
  Alert,
  Button,
  PermissionsAndroid,
  PixelRatio,
  Platform,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { BleManager, Device } from "react-native-ble-plx";
import { captureRef } from "react-native-view-shot";
import * as ThermalPrint from "thermal-print";

export const manager = new BleManager();

const printerName = "58MINI_C0A3";

export default function App() {
  const viewRef = useRef<View>();
  const [selectedDevice, setSelectedDevice] = useState<Device>();

  React.useEffect(() => {
    const subscription = manager.onStateChange((state) => {
      if (state === "PoweredOn") {
        scanAndConnect();
        subscription.remove();
      }
    }, true);
    return () => subscription.remove();
  }, [manager]);

  function scanAndConnect() {
    manager.startDeviceScan(null, null, (error, device) => {
      if (error) {
        // Handle error (scanning will be stopped automatically)
        return;
      }

      console.log(device?.name);

      if (device && device.name === printerName) {
        // Stop scanning as it's not necessary if you are scanning for one device.
        setSelectedDevice(device);
        manager.stopDeviceScan();
      }
    });
  }

  const printSomthing = async () => {
    if (!selectedDevice) {
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

    const toPrint = await ThermalPrint.generateBytecodeAsync(
      manipulate.base64,
      384,
      5
    );

    selectedDevice
      .connect()
      .then((device) => {
        return device.discoverAllServicesAndCharacteristics();
      })
      .then(async (device) => {
        const services = await device.services();
        let correctCharacteristic = null;

        for (const service of services) {
          const characteristics = await service.characteristics();

          correctCharacteristic = characteristics.find(
            // eslint-disable-next-line prettier/prettier
            (characteristic) => characteristic.isWritableWithResponse
          );

          if (correctCharacteristic) {
            break;
          }
        }

        if (correctCharacteristic) {
          for (const line of toPrint) {
            const response = await correctCharacteristic.writeWithResponse(
              Buffer.from(line).toString("base64")
            );
          }
        }
      })
      .catch((error) => {});
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

  return (
    <View style={styles.container}>
      <Button title="Check Permission" onPress={checkPermission} />
      <Button title="Print something" onPress={printSomthing} />

      <View
        style={{
          width: 300,
          justifyContent: "center",
          borderWidth: 3,
          minHeight: 100,
          borderColor: "black",
          alignItems: "center",
          backgroundColor: "white",
        }}
        ref={viewRef}
      >
        <Text>This is actually very nice</Text>
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
