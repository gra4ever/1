from pathlib import Path

# v0.22: reliable Android launcher icon + short app label.
# Keep all previous UI / rehearsal fixes and only change launcher resources + version.

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text(encoding='utf-8')
for old in [
    'android:icon="@drawable/ic_launcher_v21"',
    'android:icon="@drawable/ic_launcher"',
]:
    m = m.replace(old, 'android:icon="@mipmap/ic_launcher_v22"')
for old in [
    'android:roundIcon="@drawable/ic_launcher_v21"',
    'android:roundIcon="@drawable/ic_launcher"',
]:
    m = m.replace(old, 'android:roundIcon="@mipmap/ic_launcher_v22_round"')
m = m.replace('android:label="Stagiune Filarmonica Transilvania"', 'android:label="Stagiune FST"')
if 'android:icon="@mipmap/ic_launcher_v22"' not in m:
    raise SystemExit('v0.22 manifest icon patch failed')
if 'android:roundIcon="@mipmap/ic_launcher_v22_round"' not in m:
    raise SystemExit('v0.22 manifest roundIcon patch failed')
if 'android:label="Stagiune FST"' not in m:
    raise SystemExit('v0.22 manifest label patch failed')
manifest.write_text(m, encoding='utf-8')

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 21" not in b or "versionName '0.21.0'" not in b:
    raise SystemExit('v0.22 version patch failed: expected v0.21 build.gradle')
b = b.replace('versionCode 21', 'versionCode 22').replace("versionName '0.21.0'", "versionName '0.22.0'")
build.write_text(b, encoding='utf-8')

res = Path('app/src/main/res')
(res / 'drawable').mkdir(parents=True, exist_ok=True)
(res / 'mipmap-anydpi').mkdir(parents=True, exist_ok=True)
(res / 'mipmap-anydpi-v26').mkdir(parents=True, exist_ok=True)
(res / 'values').mkdir(parents=True, exist_ok=True)

# Foreground: simple gold concert-stage / score icon deliberately designed for tiny launcher sizes.
foreground = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#00000000" android:strokeColor="#F0C86E" android:strokeWidth="5.2" android:strokeLineCap="round" android:pathData="M24,73 L24,45 C24,28 37,19 54,19 C71,19 84,28 84,45 L84,73"/>
    <path android:fillColor="#E5B75B" android:pathData="M18,74 L29,74 L29,82 L18,82 Z M79,74 L90,74 L90,82 L79,82 Z"/>
    <path android:fillColor="#F3D27F" android:pathData="M20,39 L30,39 L30,43 L20,43 Z M78,39 L88,39 L88,43 L78,43 Z"/>
    <path android:fillColor="#F2CC73" android:pathData="M39,47 L52,45 L52,64 L39,62 Z M56,45 L69,47 L69,62 L56,64 Z"/>
    <path android:fillColor="#D7A84A" android:pathData="M38,64 L70,64 L70,67 L38,67 Z M52,67 L56,67 L56,79 L52,79 Z M45,79 L63,79 L63,82 L45,82 Z"/>
    <path android:fillColor="#F3D27F" android:pathData="M47,29 C47,25 50,22 54,22 C58,22 61,25 61,29 C61,33 58,36 54,36 C50,36 47,33 47,29 Z"/>
    <path android:fillColor="#F3D27F" android:pathData="M50,28 L54,24 L58,28 L58,32 L54,35 L50,32 Z"/>
    <path android:fillColor="#D7A84A" android:pathData="M34,83 L74,83 L74,87 L34,87 Z"/>
</vector>
'''
(res / 'drawable' / 'ic_launcher_v22_foreground.xml').write_text(foreground, encoding='utf-8')

# Legacy icon: full navy tile + same gold stage. This is the fallback on pre-Android 8 launchers.
legacy = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#082A55" android:pathData="M0,0 L108,0 L108,108 L0,108 Z"/>
    <path android:fillColor="#00000000" android:strokeColor="#F0C86E" android:strokeWidth="5.2" android:strokeLineCap="round" android:pathData="M24,73 L24,45 C24,28 37,19 54,19 C71,19 84,28 84,45 L84,73"/>
    <path android:fillColor="#E5B75B" android:pathData="M18,74 L29,74 L29,82 L18,82 Z M79,74 L90,74 L90,82 L79,82 Z"/>
    <path android:fillColor="#F3D27F" android:pathData="M20,39 L30,39 L30,43 L20,43 Z M78,39 L88,39 L88,43 L78,43 Z"/>
    <path android:fillColor="#F2CC73" android:pathData="M39,47 L52,45 L52,64 L39,62 Z M56,45 L69,47 L69,62 L56,64 Z"/>
    <path android:fillColor="#D7A84A" android:pathData="M38,64 L70,64 L70,67 L38,67 Z M52,67 L56,67 L56,79 L52,79 Z M45,79 L63,79 L63,82 L45,82 Z"/>
    <path android:fillColor="#F3D27F" android:pathData="M47,29 C47,25 50,22 54,22 C58,22 61,25 61,29 C61,33 58,36 54,36 C50,36 47,33 47,29 Z"/>
    <path android:fillColor="#D7A84A" android:pathData="M34,83 L74,83 L74,87 L34,87 Z"/>
</vector>
'''
(res / 'mipmap-anydpi' / 'ic_launcher_v22.xml').write_text(legacy, encoding='utf-8')
(res / 'mipmap-anydpi' / 'ic_launcher_v22_round.xml').write_text(legacy, encoding='utf-8')

colors = '''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_v22_background">#082A55</color>
</resources>
'''
(res / 'values' / 'ic_launcher_v22_colors.xml').write_text(colors, encoding='utf-8')

adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_v22_background"/>
    <foreground android:drawable="@drawable/ic_launcher_v22_foreground"/>
</adaptive-icon>
'''
(res / 'mipmap-anydpi-v26' / 'ic_launcher_v22.xml').write_text(adaptive, encoding='utf-8')
(res / 'mipmap-anydpi-v26' / 'ic_launcher_v22_round.xml').write_text(adaptive, encoding='utf-8')

print('v0.22 adaptive launcher icon + Stagiune FST prepared successfully')
