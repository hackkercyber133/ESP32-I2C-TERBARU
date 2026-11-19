import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';

class WidgetBridge {
  static const MethodChannel _channel = MethodChannel('nexus/widget');
  static String _lastKey = '';
  static DateTime _lastPush = DateTime.fromMillisecondsSinceEpoch(0);

  static Future<void> push({
    required String name,
    required String mode,
    required String ip,
    required String pass,
    required String mac,
    required double voltage,
    required int fan,
    required int rpm,
    required String ledMode,
    required String lastLed,
    required bool online,
  }) async {
    if (!Platform.isAndroid) return;
    final key = jsonEncode([name, mode, ip, pass, mac, voltage, fan, ledMode, lastLed, online]);
    final now = DateTime.now();
    final sinceLast = now.difference(_lastPush);
    if (key == _lastKey && sinceLast.inSeconds < 60) return;
    if (key != _lastKey && sinceLast.inMilliseconds < 800) return;
    _lastKey = key;
    _lastPush = now;
    final payload = jsonEncode({
      'name': name,
      'mode': mode,
      'ip': ip,
      'pass': pass,
      'mac': mac,
      'voltage': voltage,
      'fan': fan,
      'rpm': rpm,
      'ledMode': ledMode,
      'lastLed': lastLed,
      'online': online,
    });
    try {
      await _channel.invokeMethod('update', payload);
    } catch (_) {}
  }
}
