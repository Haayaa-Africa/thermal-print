import * as React from 'react';

import { ThermalPrintViewProps } from './ThermalPrint.types';

export default function ThermalPrintView(props: ThermalPrintViewProps) {
  return (
    <div>
      <span>{props.name}</span>
    </div>
  );
}
