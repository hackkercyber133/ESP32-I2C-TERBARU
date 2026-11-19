package __PKG__

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object NexusControl {
    private const val AUTH_USER = "admin01"

    private fun jsonValue(v: Any): String = when (v) {
        is Double -> String.format(Locale.US, "%.1f", v)
        is Int -> v.toString()
        else -> JSONObject.quote(v.toString())
    }

    private fun queryValue(v: Any): String = when (v) {
        is Double -> String.format(Locale.US, "%.1f", v)
        else -> v.toString()
    }

    fun send(ctx: Context, params: Map<String, Any>): Pair<Boolean, String> {
        val s = NexusSnapshot.load(ctx)
        if (!s.configured) return Pair(false, "Buka app dulu dan hubungkan cooler")
        val result = if (s.mode == "WiFi") sendWifi(ctx, s, params) else sendBle(ctx, s, params)
        NexusWidgetProvider.refreshAll(ctx)
        return result
    }

    private fun sendWifi(ctx: Context, s: Snap, params: Map<String, Any>): Pair<Boolean, String> {
        val query = params.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(queryValue(it.value), "UTF-8")
        }
        val conn = URL("http://" + s.ip + "/set?" + query).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(0)
            val cred = Base64.encodeToString((AUTH_USER + ":" + s.pass).toByteArray(), Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic " + cred)
            conn.outputStream.close()
            val code = conn.responseCode
            if (code == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                applyStatus(ctx, body)
                Pair(true, "OK")
            } else {
                Pair(false, "ESP32 menolak perintah ($code)")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal konek ke ESP32, cek WiFi")
        } finally {
            conn.disconnect()
        }
    }

    private fun sendBle(ctx: Context, s: Snap, params: Map<String, Any>): Pair<Boolean, String> {
        val json = params.entries.joinToString(",", "{", "}") {
            JSONObject.quote(it.key) + ":" + jsonValue(it.value)
        }
        val r = BleWriter(ctx).write(s.mac, json)
        if (r.first) {
            val v = params["voltage"]
            val f = params["fanSpeed"]
            val l = params["ledMode"]
            NexusSnapshot.patch(
                ctx,
                voltage = if (v is Double) v else null,
                fan = if (f is Int) f else null,
                ledMode = if (l is String) l else null
            )
        }
        return r
    }

    fun refreshStatus(ctx: Context): Pair<Boolean, String> {
        val s = NexusSnapshot.load(ctx)
        if (!s.configured) return Pair(false, "Buka app dulu dan hubungkan cooler")
        if (s.mode != "WiFi") return Pair(false, "Mode Bluetooth: status diperbarui saat app dibuka")
        val conn = URL("http://" + s.ip + "/status").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                applyStatus(ctx, body)
                NexusWidgetProvider.refreshAll(ctx)
                Pair(true, "OK")
            } else {
                Pair(false, "ESP32 tidak merespons")
            }
        } catch (e: Exception) {
            Pair(false, "Gagal konek ke ESP32, cek WiFi")
        } finally {
            conn.disconnect()
        }
    }

    private fun applyStatus(ctx: Context, body: String) {
        try {
            val o = JSONObject(body)
            NexusSnapshot.patch(
                ctx,
                voltage = if (o.has("setVoltage")) o.optDouble("setVoltage") else null,
                fan = if (o.has("fanSpeed")) o.optInt("fanSpeed") else null,
                rpm = if (o.has("fanRpm")) o.optInt("fanRpm") else null,
                ledMode = if (o.has("ledMode")) o.optString("ledMode") else null
            )
        } catch (e: Exception) {
        }
    }
}
