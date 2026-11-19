package __PKG__

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class BleWriter(private val ctx: Context) {
    private val charUuid: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val handler = Handler(Looper.getMainLooper())

    fun write(mac: String, json: String, timeoutMs: Long = 12000): Pair<Boolean, String> {
        if (Build.VERSION.SDK_INT >= 31 &&
            ctx.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return Pair(false, "Izin Bluetooth belum diberikan, buka app sekali")
        }
        val mgr = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = mgr?.adapter
        if (adapter == null || !adapter.isEnabled) return Pair(false, "Bluetooth mati")
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: Exception) {
            return Pair(false, "Alamat Bluetooth tidak valid")
        }

        val done = CountDownLatch(1)
        var success = false
        var error = "Gagal kirim via Bluetooth"
        var chunks: List<ByteArray> = emptyList()
        var index = 0
        var mtu = 23

        fun tryWrite(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, attempt: Int) {
            val value = chunks[index]
            var code = -1
            var accepted = false
            try {
                if (Build.VERSION.SDK_INT >= 33) {
                    code = gatt.writeCharacteristic(ch, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    accepted = code == 0
                } else {
                    ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    ch.setValue(value)
                    accepted = gatt.writeCharacteristic(ch)
                    code = if (accepted) 0 else -1
                }
            } catch (e: Exception) {
                accepted = false
            }
            if (!accepted) {
                if (attempt < 8) {
                    handler.postDelayed({ tryWrite(gatt, ch, attempt + 1) }, 250)
                } else {
                    error = "Gagal menulis ke ESP32 (kode " + code + ")"
                    gatt.disconnect()
                }
            }
        }

        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    handler.postDelayed({
                        if (!gatt.requestMtu(247)) gatt.discoverServices()
                    }, 300)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    done.countDown()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, newMtu: Int, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
                handler.postDelayed({ gatt.discoverServices() }, 200)
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                var ch: BluetoothGattCharacteristic? = null
                for (svc in gatt.services) {
                    val c = svc.getCharacteristic(charUuid)
                    if (c != null) ch = c
                }
                if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                    error = "Karakteristik kontrol tidak ditemukan"
                    gatt.disconnect()
                    return
                }
                val payload = json.toByteArray(Charsets.UTF_8)
                val maxData = (mtu - 3 - 2).coerceAtLeast(10)
                val total = ((payload.size + maxData - 1) / maxData).coerceAtLeast(1)
                val list = ArrayList<ByteArray>()
                for (i in 0 until total) {
                    val start = i * maxData
                    val end = minOf(payload.size, start + maxData)
                    list.add(byteArrayOf(i.toByte(), total.toByte()) + payload.copyOfRange(start, end))
                }
                chunks = list
                index = 0
                val target: BluetoothGattCharacteristic = ch
                handler.postDelayed({ tryWrite(gatt, target, 0) }, 300)
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    error = "Perintah ditolak (kode " + status + "), pastikan HP sudah pairing lewat app"
                    gatt.disconnect()
                    return
                }
                index++
                if (index < chunks.size) {
                    handler.postDelayed({ tryWrite(gatt, characteristic, 0) }, 60)
                } else {
                    success = true
                    gatt.disconnect()
                }
            }
        }

        val gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
        val finished = done.await(timeoutMs, TimeUnit.MILLISECONDS)
        try {
            gatt.disconnect()
        } catch (e: Exception) {
        }
        try {
            gatt.close()
        } catch (e: Exception) {
        }
        if (!finished && !success) error = "Timeout, ESP32 tidak terjangkau"
        return if (success) Pair(true, "OK") else Pair(false, error)
    }
}
