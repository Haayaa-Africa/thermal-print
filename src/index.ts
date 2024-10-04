import {
  NativeModulesProxy,
  EventEmitter,
  Subscription,
} from "expo-modules-core";

// Import the native module. On web, it will be resolved to ThermalPrint.web.ts
// and on native platforms to ThermalPrint.ts
import {
  ChangeEventPayload,
  ThermalPrintViewProps,
} from "./ThermalPrint.types";
import ThermalPrintModule from "./ThermalPrintModule";
import ThermalPrintView from "./ThermalPrintView";

// Get the native constant value.
export const PI = ThermalPrintModule.PI;

export function hello(): string {
  return ThermalPrintModule.hello();
}

export async function setValueAsync(value: string) {
  return await ThermalPrintModule.setValueAsync(value);
}

export async function generateBytecodeAsync(value: string) {
  return await ThermalPrintModule.generateBytecodeAsync(value);
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

export { ThermalPrintView, ThermalPrintViewProps, ChangeEventPayload };
