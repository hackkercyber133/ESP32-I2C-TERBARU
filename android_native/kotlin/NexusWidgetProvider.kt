package __PKG__

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import android.widget.Toast

class NexusWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action != ACTION_VOLT && action != ACTION_FAN && action != ACTION_LED && action != ACTION_REFRESH) return
        if (intent.getStringExtra(EXTRA_KEY) != NexusSnapshot.secret(context)) return

        val appCtx = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                val s = NexusSnapshot.load(appCtx)
                val result: Pair<Boolean, String> = when (action) {
                    ACTION_VOLT -> NexusControl.send(appCtx, mapOf("voltage" to intent.getDoubleExtra(EXTRA_VOLT, 5.0)))
                    ACTION_FAN -> NexusControl.send(appCtx, mapOf("fanSpeed" to nextFan(s.fan)))
                    ACTION_LED -> NexusControl.send(
                        appCtx,
                        mapOf("ledMode" to (if (s.ledMode != "off") "off" else s.lastLed))
                    )
                    else -> NexusControl.refreshStatus(appCtx)
                }
                if (!result.first) toast(appCtx, result.second)
                refreshAll(appCtx)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_VOLT = "nexus.widget.VOLT"
        const val ACTION_FAN = "nexus.widget.FAN"
        const val ACTION_LED = "nexus.widget.LED"
        const val ACTION_REFRESH = "nexus.widget.REFRESH"
        const val EXTRA_VOLT = "volt"
        const val EXTRA_KEY = "k"

        fun nextFan(cur: Int): Int {
            val steps = listOf(40, 70, 100)
            return steps.firstOrNull { it > cur } ?: steps.first()
        }

        fun toast(ctx: Context, msg: String) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            }
        }

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, NexusWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                val views = buildViews(ctx)
                for (id in ids) mgr.updateAppWidget(id, views)
            }
            if (Build.VERSION.SDK_INT >= 24) {
                val tiles = listOf<Class<*>>(
                    VoltageTileService::class.java,
                    FanTileService::class.java,
                    LedTileService::class.java
                )
                for (c in tiles) {
                    try {
                        TileService.requestListeningState(ctx, ComponentName(ctx, c))
                    } catch (e: Exception) {
                    }
                }
            }
        }

        private fun piBroadcast(ctx: Context, action: String, code: Int, volt: Double? = null): PendingIntent {
            val i = Intent(ctx, NexusWidgetProvider::class.java)
            i.action = action
            i.putExtra(EXTRA_KEY, NexusSnapshot.secret(ctx))
            if (volt != null) i.putExtra(EXTRA_VOLT, volt)
            return PendingIntent.getBroadcast(
                ctx, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val s = NexusSnapshot.load(ctx)
            val v = RemoteViews(ctx.packageName, R.layout.nexus_widget)

            val title = if (s.configured) s.name else "Buka app dulu"
            v.setTextViewText(R.id.w_title, title)

            val status = if (s.configured) {
                val sb = StringBuilder()
                sb.append(s.voltage.toInt()).append("V  •  Kipas ").append(s.fan).append("%")
                if (s.rpm > 0) sb.append("  •  ").append(s.rpm).append(" RPM")
                sb.append("  •  LED ").append(if (s.ledMode == "off") "OFF" else "ON")
                if (s.mode == "Bluetooth") sb.append("  •  BT")
                if (!s.online) sb.append("  •  offline")
                sb.toString()
            } else {
                "Hubungkan cooler di app untuk mengaktifkan widget"
            }
            v.setTextViewText(R.id.w_status, status)

            val volts = listOf(
                Pair(R.id.w_btn_5, 5),
                Pair(R.id.w_btn_9, 9),
                Pair(R.id.w_btn_12, 12),
                Pair(R.id.w_btn_15, 15)
            )
            for ((viewId, volt) in volts) {
                val active = s.configured && s.voltage.toInt() == volt
                v.setInt(
                    viewId,
                    "setBackgroundResource",
                    if (active) R.drawable.nexus_widget_btn_active else R.drawable.nexus_widget_btn
                )
                v.setOnClickPendingIntent(viewId, piBroadcast(ctx, ACTION_VOLT, 100 + volt, volt.toDouble()))
            }

            v.setTextViewText(R.id.w_btn_fan, "KIPAS " + s.fan + "%")
            v.setOnClickPendingIntent(R.id.w_btn_fan, piBroadcast(ctx, ACTION_FAN, 200))

            val ledOn = s.ledMode != "off"
            v.setTextViewText(R.id.w_btn_led, if (ledOn) "LED ON" else "LED OFF")
            v.setInt(
                R.id.w_btn_led,
                "setBackgroundResource",
                if (ledOn) R.drawable.nexus_widget_btn_active else R.drawable.nexus_widget_btn
            )
            v.setOnClickPendingIntent(R.id.w_btn_led, piBroadcast(ctx, ACTION_LED, 201))

            v.setOnClickPendingIntent(R.id.w_refresh, piBroadcast(ctx, ACTION_REFRESH, 202))

            val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            if (launch != null) {
                val open = PendingIntent.getActivity(
                    ctx, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                v.setOnClickPendingIntent(R.id.w_title, open)
                v.setOnClickPendingIntent(R.id.w_status, open)
            }
            return v
        }
    }
}
