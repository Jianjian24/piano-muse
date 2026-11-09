package com.music.pianocoach.midi

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BleMidiUuids {
    val MIDI_SERVICE: UUID = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700")
}

class BleMidiScanner(
    private val context: Context,
    private val onDevicesUpdated: (List<MidiBleDevice>) -> Unit,
    private val onScanError: (String) -> Unit,
) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager.adapter
    private val found = ConcurrentHashMap<String, MidiBleDevice>()
    private val systemInfos = ConcurrentHashMap<String, MidiDeviceInfo>()
    private var midiCallbacksRegistered = false

    private var scanner: BluetoothLeScanner? = null
    private var scanning = false

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addFromScanResult(result)
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { addFromScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            onScanError("BLE 扫描失败，错误码: $errorCode")
            scanning = false
        }
    }

    fun isBluetoothEnabled(): Boolean = adapter?.isEnabled == true

    fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun registerMidiTopologyCallbacks(onChanged: () -> Unit) {
        if (midiCallbacksRegistered) return
        val manager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return
        midiCallbacksRegistered = true
        manager.registerDeviceCallback(
            object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(device: MidiDeviceInfo) {
                    ingestSystemMidiInfo(device, online = true)
                    onChanged()
                }

                override fun onDeviceRemoved(device: MidiDeviceInfo) {
                    markSystemMidiOffline(device.id.toString())
                    onChanged()
                }

                override fun onDeviceStatusChanged(status: android.media.midi.MidiDeviceStatus) {
                    onChanged()
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

    data class RefreshStats(val bondedCount: Int, val systemOnlineCount: Int)

    fun refreshAllSources(): RefreshStats {
        val bonded = refreshPairedDevices()
        val online = refreshSystemMidiDevices()
        publish()
        return RefreshStats(bondedCount = bonded, systemOnlineCount = online)
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices(): Int {
        if (!hasConnectPermission()) return 0
        val bonded = adapter?.bondedDevices.orEmpty()
        bonded.forEach { device ->
            mergeDevice(
                address = device.address,
                name = device.name,
                bonded = device.bondState == BluetoothDevice.BOND_BONDED,
                advertisesBleMidi = false,
                systemMidiOnline = false,
            )
        }
        return bonded.size
    }

    fun refreshSystemMidiDevices(): Int {
        val manager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager ?: return 0
        val onlineIds = mutableSetOf<String>()
        @Suppress("DEPRECATION")
        val infos = manager.devices ?: emptyArray()
        for (info in infos) {
            ingestSystemMidiInfo(info, online = true)
            onlineIds.add(info.id.toString())
        }
        found.values.forEach { entry ->
            val sid = entry.systemMidiDeviceId ?: return@forEach
            if (sid !in onlineIds && entry.systemMidiOnline) {
                val key = entry.address
                found[key] = entry.copy(systemMidiOnline = false)
            }
        }
        return onlineIds.size
    }

    private fun ingestSystemMidiInfo(info: MidiDeviceInfo, online: Boolean) {
        val props = info.properties
        val name = props.getString(MidiDeviceInfo.PROPERTY_NAME)
            ?: props.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
        val bt = bluetoothFromProps(props)
        val address = bt?.address ?: "midi-${info.id}"
        mergeDevice(
            address = address,
            name = name,
            bonded = bt?.bondState == BluetoothDevice.BOND_BONDED,
            advertisesBleMidi = info.type == MidiDeviceInfo.TYPE_BLUETOOTH,
            systemMidiDeviceId = info.id.toString(),
            systemInfo = info,
            systemMidiOnline = online,
        )
    }

    private fun markSystemMidiOffline(systemId: String) {
        found.entries.forEach { (key, entry) ->
            if (entry.systemMidiDeviceId == systemId) {
                found[key] = entry.copy(systemMidiOnline = false)
            }
        }
        publish()
    }

    fun getSystemMidiDeviceInfo(systemId: String): MidiDeviceInfo? {
        systemInfos[systemId]?.let { return it }
        refreshSystemMidiDevices()
        return systemInfos[systemId]
    }

    private fun bluetoothFromProps(props: android.os.Bundle): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            props.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            props.getParcelable(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE) as? BluetoothDevice
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (scanning) return
        if (adapter == null || !adapter.isEnabled) {
            onScanError("请先打开手机蓝牙")
            return
        }
        if (!hasScanPermission()) {
            onScanError("缺少蓝牙扫描权限")
            return
        }
        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onScanError("此设备不支持 BLE 扫描")
            return
        }
        refreshAllSources()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        scanner?.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanner?.stopScan(scanCallback)
        scanning = false
    }

    @SuppressLint("MissingPermission")
    private fun addFromScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val name = record.deviceName ?: result.device.name
        val hasMidi = record.serviceUuids?.any { it.uuid == BleMidiUuids.MIDI_SERVICE } == true ||
            scanBytesContainMidiService(record.bytes)
        val bonded = result.device.bondState == BluetoothDevice.BOND_BONDED
        if (!shouldShowInList(name, bonded, hasMidi)) return
        mergeDevice(
            address = result.device.address,
            name = name,
            bonded = bonded,
            advertisesBleMidi = hasMidi,
            systemMidiOnline = false,
        )
    }

    private fun scanBytesContainMidiService(bytes: ByteArray?): Boolean {
        if (bytes == null) return false
        val target = ParcelUuid(BleMidiUuids.MIDI_SERVICE).uuid
        val targetBytes = uuidToAdvertisementBytes(target)
        return bytes.indices.any { i ->
            i + targetBytes.size <= bytes.size &&
                bytes.copyOfRange(i, i + targetBytes.size).contentEquals(targetBytes)
        }
    }

    private fun uuidToAdvertisementBytes(uuid: UUID): ByteArray {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val data = ByteArray(16)
        for (i in 0 until 8) {
            data[i] = ((msb shr ((7 - i) * 8)) and 0xff).toByte()
        }
        for (i in 0 until 8) {
            data[8 + i] = ((lsb shr ((7 - i) * 8)) and 0xff).toByte()
        }
        return data
    }

    private fun shouldShowInList(name: String?, bonded: Boolean, advertisesBleMidi: Boolean): Boolean {
        if (bonded) return true
        if (advertisesBleMidi) return true
        return looksLikePianoOrMidiPeripheral(name)
    }

    private fun looksLikePianoOrMidiPeripheral(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val n = name.uppercase()
        return listOf(
            "PIANO", "MIDI", "KEYBOARD", "DIGITAL", "BLE-MIDI", "MELLO",
        ).any { n.contains(it) }
    }

    @SuppressLint("MissingPermission")
    private fun mergeDevice(
        address: String,
        name: String?,
        bonded: Boolean,
        advertisesBleMidi: Boolean,
        systemMidiDeviceId: String? = null,
        systemInfo: MidiDeviceInfo? = null,
        systemMidiOnline: Boolean = false,
    ) {
        if (systemMidiDeviceId != null && systemInfo != null) {
            systemInfos[systemMidiDeviceId] = systemInfo
        }
        val displayName = name?.takeIf { it.isNotBlank() } ?: "未知设备"
        val previous = found[address]
        found[address] = MidiBleDevice(
            name = displayName,
            address = address,
            bonded = bonded || (previous?.bonded == true),
            advertisesBleMidi = advertisesBleMidi || (previous?.advertisesBleMidi == true),
            systemMidiDeviceId = systemMidiDeviceId ?: previous?.systemMidiDeviceId,
            systemMidiOnline = systemMidiOnline || (previous?.systemMidiOnline == true),
        )
    }

    private fun publish() {
        val list = found.values
            .sortedWith(
                compareByDescending<MidiBleDevice> { it.systemMidiOnline }
                    .thenByDescending { it.systemMidiDeviceId != null }
                    .thenByDescending { it.bonded }
                    .thenByDescending { it.advertisesBleMidi }
                    .thenBy { it.name.lowercase() },
            )
        onDevicesUpdated(list)
    }

    @SuppressLint("MissingPermission")
    fun getBluetoothDevice(address: String): BluetoothDevice? {
        if (!hasConnectPermission()) return null
        return adapter?.getRemoteDevice(address)
    }
}
