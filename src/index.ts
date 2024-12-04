import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

// Import the native module. On web, it will be resolved to ThermalPrint.web.ts
// and on native platforms to ThermalPrint.ts
import { ChangeEventPayload } from "./ThermalPrint.types";
import ThermalPrintModule from "./ThermalPrintModule";
import { EventSubscription } from "expo-modules-core/build/ts-declarations/EventEmitter";

// Get the native constant value.
export const PI = ThermalPrintModule.PI;

export async function generateBytecodeAsync(
  value: string,
  printerWidth: number,
  mtuSize: number
): Promise<Uint8Array[]> {
  return await ThermalPrintModule.generateBytecodeAsync(
    value,
    printerWidth,
    mtuSize
  );
}
export async function generateBytecodeBase64Async(
  value: string,
  printerWidth: number,
  mtuSize: number
): Promise<string[]> {
  return (await ThermalPrintModule.generateBytecodeBase64Async(
    value,
    printerWidth,
    mtuSize
  )) as string[];
}

export async function sendToUsbThermalPrinterAsync(
  value: string,
  printerWidth: number,
  chunkSize: number
): Promise<void> {
  return await ThermalPrintModule.sendToUsbThermalPrinterAsync(
    value,
    printerWidth,
    chunkSize
  );
}

const emitter = new EventEmitter(
  ThermalPrintModule ?? NativeModulesProxy.ThermalPrint
);

export function addChangeListener(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>("onChange", listener);
}

export function addGeneratedBytecodeListener(
  listener: (event: ChangeEventPayload) => void
): Subscription {
  return emitter.addListener<ChangeEventPayload>(
    "onGenerateBytecode",
    listener
  );
}

export { ChangeEventPayload };

/// NEW BLUETOOTH FUNCTIONS

export type DeviceFound = {
  id: string;
  name: string;
};

export type DeviceFoundEvent = {
  devices: DeviceFound[];
};

export function bluetoothDevicesScannedListener(
  listener: (event: DeviceFoundEvent[]) => void
): EventSubscription {
  return ThermalPrintModule.addListener("newDeviceFound", listener);
}

export async function scanForBlueToothDevices(): Promise<void> {
  return await ThermalPrintModule.scanForBlueToothDevices();
}

export async function connectToBlueToothDevice(
  deviceId: string
): Promise<void> {
  return await ThermalPrintModule.connectToBlueToothDevice(deviceId);
}

export async function disconnectFromBlueToothDevice() {
  return await ThermalPrintModule.disconnectFromBlueToothDevice();
}

export async function sendToBluetoothThermalPrinterAsync(
  value: string,
  printerWidth: number
) {
  return await ThermalPrintModule.sendToBluetoothThermalPrinterAsync(
    value,
    printerWidth
  );
}
