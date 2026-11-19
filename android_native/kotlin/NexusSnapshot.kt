package __PKG__

import android.content.Context
import org.json.JSONObject
import java.util.UUID

data class Snap(
    val name: String,
    val mode: String,
    val ip: String,
    val pass: String,
    val mac: String,
    val voltage: Double,
    val fan: Int,
    val rpm: Int,
    val ledMode: String,
    val lastLed: String,
    val online: Boolean
) {
    val configured: Boolean
        get() = name.isNotEmpty() &&
            ((mode == "WiFi" && ip.isNotEmpty()) || (mode == "Bluetooth" && mac.isNotEmpty()))
}

object NexusSnapshot {
    private const val PREF = "nexus_widget"
    private const val KEY = "snap"
    private const val KEY_SECRET = "secret"

    fun load(ctx: Context): Snap {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        val o = try {
            if (raw == null) JSONObject() else JSONObject(raw)
        } catch (e: Exception) {
            JSONObject()
        }
        var lastLed = o.optString("lastLed", "")
        if (lastLed.isEmpty() || lastLed == "off") lastLed = "rainbowrun_rainbow"
        return Snap(
            name = o.optString("name", ""),
            mode = o.optString("mode", ""),
            ip = o.optString("ip", ""),
            pass = o.optString("pass", ""),
            mac = o.optString("mac", ""),
            voltage = o.optDouble("voltage", 5.0),
            fan = o.optInt("fan", 100),
            rpm = o.optInt("rpm", 0),
            ledMode = o.optString("ledMode", "off"),
            lastLed = lastLed,
            online = o.optBoolean("online", false)
        )
    }

    fun save(ctx: Context, json: String) {
        try {
            JSONObject(json)
        } catch (e: Exception) {
            return
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, json).apply()
    }

    fun patch(
        ctx: Context,
        voltage: Double? = null,
        fan: Int? = null,
        rpm: Int? = null,
        ledMode: String? = null
    ) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val o = try {
            JSONObject(p.getString(KEY, "{}") ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
        if (voltage != null) o.put("voltage", voltage)
        if (fan != null) o.put("fan", fan)
        if (rpm != null) o.put("rpm", rpm)
        if (ledMode != null) {
            o.put("ledMode", ledMode)
            if (ledMode != "off") o.put("lastLed", ledMode)
        }
        p.edit().putString(KEY, o.toString()).apply()
    }

    fun secret(ctx: Context): String {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        var s = p.getString(KEY_SECRET, null)
        if (s == null) {
            s = UUID.randomUUID().toString()
            p.edit().putString(KEY_SECRET, s).apply()
        }
        return s
    }
}
