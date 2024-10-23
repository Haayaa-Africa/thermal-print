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

export function hello(): string {
  return ThermalPrintModule.hello();
}

export async function setValueAsync(value: string) {
  return await ThermalPrintModule.setValueAsync(value);
}

export async function generateBytecodeAsync(
  value: string,
  printerWidth: number,
  chunkSize: number
) {
  return await ThermalPrintModule.generateBytecodeAsync(
    value,
    printerWidth,
    chunkSize
  );
}
export async function generateBytecodeBase64Async(
  value: string,
  printerWidth: number,
  chunkSize: number
) {
  return await ThermalPrintModule.generateBytecodeBase64Async(
    value,
    printerWidth,
    chunkSize
  );
}

export async function sendToUsbThermalPrinterAsync(
  value: string,
  printerWidth: number,
  chunkSize: number
) {
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
