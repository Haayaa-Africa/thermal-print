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

    fun requestBluetoothPermissions(activity: Activity) {
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

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                missingPermissions.toTypedArray(),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }

    fun startScanning(onDeviceFound: (List<Map<String, String>>) -> Unit) {
        val devicesFound = mutableListOf<Map<String, String>>()
        scanSubscription?.dispose()
        devices.clear()

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
            }
        )
    }

    fun connectToDevice(deviceId: String) {
        device = rxBleClient.getBleDevice(deviceId)

        Log.d("BluetoothManager", "Device State: ${device?.connectionState}")

        val disposable = device?.establishConnection(false)?.subscribe(
            { rxBleConnection ->
                discoverServices(rxBleConnection)
            },
            { throwable ->
                Log.e("BluetoothManager", "Connection error: ${throwable.message}")
            }
        )

        if (disposable != null) compositeDisposable.add(disposable)
    }

    fun disconnect() {
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
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Error while disconnecting: ${e.message}")
        }
    }

    fun printWithDevice(lines: List<ByteArray>) {
        if (connection == null) {
            return
        }

        Log.d("BluetoothManager", "Device State: ${device?.connectionState}")

        for ( line in lines){
            sendPrintData(line)
        }
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