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
        "App requires Bluetooth to connect and print with thermal printers.",
    };
    return config;
  });

  config = withAndroidManifest(config, (config) => {
    const androidManifest = config.modResults;

    if (!androidManifest || !androidManifest.manifest) {
      throw new Error(
        "AndroidManifest.xml is not properly parsed. Please check your configuration."
      );
    }

    const permissionsToAdd = [
      "android.permission.BLUETOOTH",
      "android.permission.BLUETOOTH_ADMIN",
      "android.permission.BLUETOOTH_SCAN",
      "android.permission.BLUETOOTH_CONNECT",
      "android.permission.ACCESS_FINE_LOCATION",
      "android.permission.USB_PERMISSION",
    ];

    const existingPermissions =
      androidManifest.manifest["uses-permission"]?.map(
        (permission) => permission.$["android:name"]
      ) || [];

    permissionsToAdd.forEach((permission) => {
      if (!existingPermissions.includes(permission)) {
        androidManifest.manifest["uses-permission"] = [
          ...(androidManifest.manifest["uses-permission"] || []),
          { $: { "android:name": permission } },
        ];
      }
    });

    return config;
  });

  return config;
};

export default withBluetoothPermissions;
