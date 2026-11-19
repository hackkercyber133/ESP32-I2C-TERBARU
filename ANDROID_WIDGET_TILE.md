# Quick Tile + Widget Android

Fitur ini otomatis ikut ter-build di Codemagic (langkah "Pasang Quick Tile + Widget Android"
di `codemagic.yaml`). Tidak ada dependency baru di `pubspec.yaml`.

## Cara pakai

Buka app sekali dan hubungkan cooler sampai statusnya Online. Dari situ app menyimpan data
cooler (nama, IP/alamat Bluetooth, password) supaya tile dan widget bisa jalan tanpa membuka app.

**Quick Tile** (panel notifikasi): geser dari atas layar, tekan ikon pensil/edit, lalu seret
tile ke panel aktif.
- **Voltase**: tiap tap mengganti 5V, 9V, 12V, lalu kembali ke 5V. 15V sengaja tidak masuk
  siklus tile supaya tidak sengaja kepencet, pakai widget atau app untuk 15V.
- **Kipas**: tiap tap mengganti 40%, 70%, 100%.
- **LED RGB**: tap untuk ON/OFF. ON memakai efek terakhir yang dipakai di app.

**Widget** (layar utama): tekan lama area kosong, pilih Widgets, cari "Mod And TroubleShoot".
Tombol 5V/9V/12V/15V, KIPAS (siklus 40/70/100%), LED ON/OFF, dan tombol refresh di pojok.
Tap judul atau baris status untuk membuka app.

## Mode koneksi
- **WiFi**: perintah dikirim lewat HTTP ke ESP32 (HP harus satu WiFi dengan ESP32). Status
  di widget ikut diperbarui dari respons ESP32.
- **Bluetooth**: tile/widget menyambung ke ESP32 sebentar, menulis perintah, lalu memutus
  lagi. HP harus sudah pernah pairing lewat app, izin Bluetooth sudah diberikan, dan app
  sebaiknya tidak sedang terhubung ke ESP32 pada saat yang sama. Status di widget mengikuti
  data terakhir dari app.

## Berkas
- `android_native/kotlin/` kode Kotlin (tile, widget, kirim HTTP/BLE, MainActivity channel).
- `android_native/res/` layout, ikon, dan konfigurasi widget.
- `tools/apply_android_native.py` menyalin berkas di atas ke `android/` hasil `flutter create`
  dan menambah service/receiver ke `AndroidManifest.xml`.
- `lib/widget_bridge.dart` mengirim data cooler dari Flutter ke sisi native.
