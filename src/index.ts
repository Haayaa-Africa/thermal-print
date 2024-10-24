import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

// Import the native module. On web, it will be resolved to ThermalPrint.web.ts
// and on native platforms to ThermalPrint.ts
import { ChangeEventPayload } from "./ThermalPrint.types";
import ThermalPrintModule from "./ThermalPrintModule";

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

export async function sendToBluetoothThermalPrinterAsync(
  value: string,
  printerWidth: number,
  chunkSize: number,
  deviceMac: string,
  serviceUUID: string,
  characteristicUUID: string
) {
  return await ThermalPrintModule.sendToBluetoothThermalPrinterAsync(
    value,
    printerWidth,
    chunkSize,
    deviceMac,
    serviceUUID,
    characteristicUUID
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
