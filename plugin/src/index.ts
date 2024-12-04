import {
  ConfigPlugin,
  withAndroidManifest,
  withInfoPlist,
} from "expo/config-plugins";

const withBluetoothPermissions: ConfigPlugin = (config) => {
  config = withInfoPlist(config, (config) => {
    config.modResults = {
      ...config.modResults,
      NSBluetoothAlwaysUsageDescription:
        "App requires Bluetooth to connect and print thermal printers.",
    };
    return config;
  });

  config = withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;

    androidManifest.manifest["uses-permission"] = [
      ...(androidManifest.manifest["uses-permission"] || []),
      { $: { "android:name": "android.permission.BLUETOOTH" } },
      { $: { "android:name": "android.permission.BLUETOOTH_ADMIN" } },
      { $: { "android:name": "android.permission.BLUETOOTH_SCAN" } },
      { $: { "android:name": "android.permission.BLUETOOTH_CONNECT" } },
      { $: { "android:name": "android.permission.ACCESS_FINE_LOCATION" } },
      { $: { "android:name": "android.permission.USB_PERMISSION" } },
    ];

    return config;
  });

  return config;
};

export default withBluetoothPermissions;
