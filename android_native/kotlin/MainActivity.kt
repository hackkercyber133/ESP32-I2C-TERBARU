package __PKG__

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "nexus/widget")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "update" -> {
                        val json = call.arguments as? String
                        if (json != null) {
                            NexusSnapshot.save(applicationContext, json)
                            NexusWidgetProvider.refreshAll(applicationContext)
                        }
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
