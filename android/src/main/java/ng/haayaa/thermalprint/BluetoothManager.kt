package ng.haayaa.thermalprint

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.le.ScanCallback
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import expo.modules.kotlin.Promise
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.UUID

class BluetoothManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val devices = mutableMapOf<String, String>() // Stores device ID and name
    private var scanSubscription: Disposable? = null
    private val rxBleClient: RxBleClient = RxBleClient.create(context)
    private val compositeDisposable = CompositeDisposable()
    private var writeAbleUUIDS: List<UUID> = listOf()
    private var device: RxBleDevice? = null

    private var connection:RxBleConnection?  = null;

    companion object {
        const val REQUEST_BLUETOOTH_PERMISSIONS = 1
    }

    private val knownWritableUUIDs = listOf(
        UUID.fromString("00002AF1-0000-1000-8000-00805F9B34FB")
    )

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun arePermissionsGranted(permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestBluetoothPermissions(activity: Activity, promise: Promise) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        if (arePermissionsGranted(permissions)) {
            promise.resolve(true) // Permissions are already granted
            return
        }

        ActivityCompat.requestPermissions(
            activity,
            permissions,
            REQUEST_BLUETOOTH_PERMISSIONS
        )


        if (arePermissionsGranted(permissions)) {
            promise.resolve(true) // Permissions are already granted
            return
        }

        promise.resolve(false)
    }

    fun startScanning(onDeviceFound: (List<Map<String, String>>) -> Unit, promise: Promise) {
        val devicesFound = mutableListOf<Map<String, String>>()
        scanSubscription?.dispose()
        devices.clear()

        try {
            scanSubscription = rxBleClient.scanBleDevices(
                com.polidea.rxandroidble3.scan.ScanSettings.Builder()
                    .setCallbackType(com.polidea.rxandroidble3.scan.ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                    .build()
            ).subscribe(
                { scanResult ->
                    val deviceName = scanResult.bleDevice.name
                    val deviceId = scanResult.bleDevice.macAddress
                    if (!deviceName.isNullOrBlank() && !devices.containsKey(deviceId)) {
                        devices[deviceId] = deviceName
                        devicesFound.add(mapOf("id" to deviceId, "name" to deviceName))
                        onDeviceFound(devicesFound)
                    }
                },
                { throwable ->
                    Log.d("BluetoothManager", "Device Error: ${throwable.message}")
                    promise.reject(
                        "BLUETOOTH_ERROR",
                        "Error while scanning for devices: ${throwable.message}",
                        throwable
                    )
                }
            )

            promise.resolve(true)

        } catch (e: Exception) {
            Log.d("BluetoothManager", "Device Error: ${e.message}")
            promise.reject(
                "BLUETOOTH_ERROR",
                "Error while scanning for devices: ${e.message}",
                e
            )
        }
    }

    fun suspendScanning(promise: Promise) {
        try {
            if (scanSubscription != null && !scanSubscription!!.isDisposed) {
                scanSubscription!!.dispose() // Dispose of the current scan
                scanSubscription = null
                devices.clear() // Optionally clear discovered devices
                Log.d("BluetoothManager", "Scanning has been suspended successfully.")
                promise.resolve(true)
            } else {
                Log.d("BluetoothManager", "No active scan to suspend.")
                promise.resolve(false)
            }
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while suspending scan: ${e.message}")
            promise.reject(
                "SCAN_SUSPEND_ERROR",
                "Failed to suspend scanning: ${e.message}",
                e
            )
        }
    }

    fun connectToDevice(deviceId: String, promise: Promise) {
        device = rxBleClient.getBleDevice(deviceId)

        Log.d("BluetoothManager", "Device State: ${device?.connectionState}")

        val disposable = device?.establishConnection(false)?.subscribe(
            { rxBleConnection ->
                discoverServices(rxBleConnection)
                promise.resolve(true)
            },
            { throwable ->
                Log.e("BluetoothManager", "Connection error: ${throwable.message}")
                promise.reject(
                    "BLUETOOTH_ERROR",
                    "Error while connecting to device: ${throwable.message}",
                    throwable
                )
            }
        )

        if (disposable != null) compositeDisposable.add(disposable)
    }

    fun disconnect(promise: Promise) {
        if (connection == null) {
            Log.d("BluetoothManager", "No active connection to disconnect.")
            return
        }

        try {
            // Dispose of the active connection to disconnect
            compositeDisposable.clear()
            connection = null
            device = null
            Log.d("BluetoothManager", "Disconnected successfully.")
            promise.resolve(true)
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while disconnecting: ${e.message}")
            promise.reject(
                "BLUETOOTH_ERROR",
                "Error while disconnecting: ${e.message}",
                e
            )
        }
    }

    fun printWithDevice(lines: List<ByteArray>, promise: Promise) {
        if (connection == null) {
            return promise.reject(
                "BLUETOOTH_ERROR",
                "No active connection to print with.",
                null
            )
        }

        Log.d("BluetoothManager", "Device State: ${device?.connectionState}")

        for ( line in lines){
            sendPrintData(line)
        }

        promise.resolve(true)
    }

    fun getAllowedMtu(): Int {
        return connection?.mtu ?: 20
    }

    private fun sendPrintData(byteArray: ByteArray) {
        if (connection === null) return;

        Log.d("BluetoothManager", "Printing with device with MTU ${connection?.mtu}")

        val disposable = connection?.createNewLongWriteBuilder()
            ?.setCharacteristicUuid(knownWritableUUIDs.first())
            ?.setBytes(byteArray)
            ?.build()
            ?.subscribe(
                { Log.d("BluetoothManager", "Data successfully sent using existing connection.") },
                { throwable ->
                    Log.e("BluetoothManager", "Error while printing: ${throwable.message}")
                }
            )

        if (disposable != null) compositeDisposable.add(disposable)
    }

    private fun discoverServices(rxBleConnection: RxBleConnection) {
        val mtuRequestDisposable = rxBleConnection.requestMtu(512)
            .subscribe({
                Log.d("BluetoothManager", "MTU successfully set to $it")
            }, { throwable ->
                Log.e("BluetoothManager", "Error while setting MTU: ${throwable.message}")
            })

        compositeDisposable.add(mtuRequestDisposable)

        val disposable = rxBleConnection.discoverServices()
            .subscribe(
                { services ->
                    services.bluetoothGattServices.forEach { service ->
                        service.characteristics.forEach { characteristic ->
                            if (isCharacteristicWritable(characteristic)) {
                                writeAbleUUIDS = writeAbleUUIDS + characteristic.uuid
                            }
                        }
                    }
                },
                { throwable ->
                    Log.e("BluetoothManager", "Service discovery failed: ${throwable.message}")
                }
            )

        connection = rxBleConnection;

        compositeDisposable.add(disposable)

    }

    private fun isCharacteristicWritable(characteristic: BluetoothGattCharacteristic): Boolean {
        val properties = characteristic.properties
        return (properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
    }
}