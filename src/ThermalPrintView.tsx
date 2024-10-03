import { requireNativeViewManager } from 'expo-modules-core';
import * as React from 'react';

import { ThermalPrintViewProps } from './ThermalPrint.types';

const NativeView: React.ComponentType<ThermalPrintViewProps> =
  requireNativeViewManager('ThermalPrint');

export default function ThermalPrintView(props: ThermalPrintViewProps) {
  return <NativeView {...props} />;
}
