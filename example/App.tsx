import { useEffect } from "react";
import { Button, StyleSheet, Text, View } from "react-native";
import { BleManager, Device } from "react-native-ble-plx";
import * as ThermalPrint from "thermal-print";

const manager = new BleManager();

export default function App() {
  useEffect(() => {
    // manager.startDeviceScan(
    //   null,
    //   {
    //     allowDuplicates: true,
    //   },
    //   (device, error) => {
    //     console.log(error);
    //   }
    // );
    return () => {};
  }, []);

  const printSomthing = async () => {
    const toPrint = await ThermalPrint.generateBytecodeAsync(
      "iVBORw0KGgoAAAANSUhEUgAAA+gAAAKbBAMAAACUTL0MAAAAIVBMVEU/QWP70tPsGSCbh5n4qq3sGSDsGSD///+CiZ/6xsgFFEBjSht+AAAABnRSTlP778D15DBi/uc8AAADwUlEQVR42u3RUQnAIAAFwGHI/ex/FdbEDxMMMcJg1WwhPLyrcKVHeJ8I9Y5QDrYjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pGOdKQjHelIRzrSpSMd6UhHOtKRjnSkIx3pSEc60pGOdKQjXTrSkY50pCMd6UhHOtKRjnSkIx3pSEe6dKQjHelIRzrSkY50pCMd6UhHOtKRjnTpSEc60pGOdKQjHelIRzrSkY50pCMd6dKRjnSkIx3pSEc60pHOovQzwtUijC/BPwEP9hqv5X/1IQAAAABJRU5ErkJggg=="
    );

    console.log({ toPrint });
  };

  const checkPermission = async () => {};

  return (
    <View style={styles.container}>
      <Button title="Check Permission" onPress={checkPermission} />
      <Button title="Print something" onPress={printSomthing} />
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
