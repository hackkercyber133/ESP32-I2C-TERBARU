import os
import re
import shutil
import sys

ROOT = os.getcwd()
NATIVE = os.path.join(ROOT, "android_native")
APP = os.path.join(ROOT, "android", "app")
MAIN = os.path.join(APP, "src", "main")
MANIFEST = os.path.join(MAIN, "AndroidManifest.xml")


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def detect_package():
    for name in ("build.gradle.kts", "build.gradle"):
        p = os.path.join(APP, name)
        if os.path.exists(p):
            m = re.search(r'namespace\s*=?\s*["\']([^"\']+)["\']', read(p))
            if m:
                return m.group(1)
    return "com.coolerapp.app_controller"


def main():
    if not os.path.isdir(NATIVE):
        print("android_native/ tidak ditemukan, dilewati")
        return 0
    if not os.path.exists(MANIFEST):
        print("android/app/src/main/AndroidManifest.xml tidak ada, jalankan setelah flutter create")
        return 1

    pkg = detect_package()
    print("Package aplikasi:", pkg)

    kotlin_dir = os.path.join(MAIN, "kotlin", *pkg.split("."))
    java_dir = os.path.join(MAIN, "java", *pkg.split("."))
    for old in (os.path.join(java_dir, "MainActivity.java"), os.path.join(java_dir, "MainActivity.kt")):
        if os.path.exists(old):
            os.remove(old)
            print("Hapus", old)

    src_kotlin = os.path.join(NATIVE, "kotlin")
    for fn in sorted(os.listdir(src_kotlin)):
        if not fn.endswith(".kt"):
            continue
        content = read(os.path.join(src_kotlin, fn)).replace("__PKG__", pkg)
        write(os.path.join(kotlin_dir, fn), content)
        print("Tulis", os.path.join(kotlin_dir, fn))

    src_res = os.path.join(NATIVE, "res")
    for dirpath, _, files in os.walk(src_res):
        rel = os.path.relpath(dirpath, src_res)
        for fn in files:
            dst = os.path.join(MAIN, "res", rel, fn)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(os.path.join(dirpath, fn), dst)
            print("Salin res", os.path.join(rel, fn))

    manifest = read(MANIFEST)

    if "usesCleartextTraffic" not in manifest:
        manifest = re.sub(r"<application", '<application android:usesCleartextTraffic="true"', manifest, count=1)
        print("usesCleartextTraffic diaktifkan (widget/tile memakai HTTP ke ESP32)")

    if "NexusWidgetProvider" not in manifest:
        def tile(cls, label, icon):
            return (
                '        <service\n'
                '            android:name="%s.%s"\n'
                '            android:exported="true"\n'
                '            android:icon="@drawable/%s"\n'
                '            android:label="%s"\n'
                '            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">\n'
                '            <intent-filter>\n'
                '                <action android:name="android.service.quicksettings.action.QS_TILE" />\n'
                '            </intent-filter>\n'
                '        </service>\n'
            ) % (pkg, cls, icon, label)

        block = (
            tile("VoltageTileService", "Voltase", "ic_nexus_bolt")
            + tile("FanTileService", "Kipas", "ic_nexus_fan")
            + tile("LedTileService", "LED RGB", "ic_nexus_bulb")
            + '        <receiver\n'
            '            android:name="%s.NexusWidgetProvider"\n'
            '            android:exported="true"\n'
            '            android:label="Mod And TroubleShoot">\n'
            '            <intent-filter>\n'
            '                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />\n'
            '            </intent-filter>\n'
            '            <meta-data\n'
            '                android:name="android.appwidget.provider"\n'
            '                android:resource="@xml/nexus_widget_info" />\n'
            '        </receiver>\n' % pkg
        )
        manifest = manifest.replace("</application>", block + "    </application>", 1)
        print("Service tile + receiver widget ditambahkan ke manifest")
    else:
        print("Manifest sudah berisi komponen widget/tile, dilewati")

    write(MANIFEST, manifest)
    return 0


if __name__ == "__main__":
    sys.exit(main())
