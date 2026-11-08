package com.music.pianocoach.midi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * 部分电子琴在未先建立 GATT 会话时，[android.media.midi.MidiManager.openBluetoothDevice] 会失败。
 * 先探测标准 BLE MIDI 服务并立即关闭 GATT，再交给系统 MIDI 打开。
 */
class BleMidiGattWake(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun ensureBleMidiReady(device: BluetoothDevice, timeoutMs: Long = 12_000): Result<Unit> {
        val latch = CountDownLatch(1)
        val result = AtomicReference<Result<Unit>>(
            Result.failure(IllegalStateException("BLE MIDI 唤醒未完成")),
        )
        val handler = Handler(Looper.getMainLooper())
        var gatt: BluetoothGatt? = null

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        if (latch.count > 0) {
                            result.set(
                                Result.failure(
                                    IllegalStateException("GATT 已断开 (status=$status)"),
                                ),
                            )
                            latch.countDown()
                        }
                    }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                val hasMidi = g.getService(BleMidiUuids.MIDI_SERVICE) != null
                if (status == BluetoothGatt.GATT_SUCCESS && hasMidi) {
                    result.set(Result.success(Unit))
                } else {
                    result.set(
                        Result.failure(
                            IllegalStateException("设备未暴露标准 BLE MIDI 服务"),
                        ),
                    )
                }
                try {
                    g.close()
                } catch (_: Exception) {
                }
                latch.countDown()
            }
        }

        handler.post {
            gatt = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } else {
                @Suppress("DEPRECATION")
                device.connectGatt(context, false, callback)
            }
        }

        val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (!completed) {
            try {
                gatt?.close()
            } catch (_: Exception) {
            }
            return Result.failure(IllegalStateException("唤醒 BLE MIDI 超时"))
        }
        return result.get()
    }
}
