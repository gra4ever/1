from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')

marker = '                display=display.replaceAll("(?iu)[,;]\\\\s*concert\\\\s+", " | Concert ");\n'
insert = '''                display=display.replaceAll("(?iu)[,;]\\\\s*concert\\\\s+", " | Concert ");
                // Some source rows place the word Concert after the second time interval,
                // e.g. "10:00–13:00,19:00–21:00 Concert". Split before that second interval too.
                display=display.replaceAll("(?iu)[,;]\\\\s*(?=\\\\d{1,2}[:.]\\\\d{2}\\\\s*[–—-]\\\\s*\\\\d{1,2}[:.]\\\\d{2}\\\\s*Concert\\\\b)", " | ");
'''

if text.count(marker) != 1:
    raise SystemExit(f'v0.21 patch failed: v0.20 concert marker count={text.count(marker)}')
text = text.replace(marker, insert, 1)

check = 'display=display.replaceAll("(?iu)[,;]\\\\s*(?=\\\\d{1,2}[:.]\\\\d{2}\\\\s*[–—-]\\\\s*\\\\d{1,2}[:.]\\\\d{2}\\\\s*Concert\\\\b)", " | ")'
if check not in text:
    raise SystemExit('v0.21 verification failed: second time-interval split not found')

src.write_text(text, encoding='utf-8')

manifest = Path('app/src/main/AndroidManifest.xml')
m = manifest.read_text(encoding='utf-8')
m = m.replace('android:icon="@drawable/ic_launcher"', 'android:icon="@drawable/ic_launcher_v21"')
m = m.replace('android:roundIcon="@drawable/ic_launcher"', 'android:roundIcon="@drawable/ic_launcher_v21"')
manifest.write_text(m, encoding='utf-8')

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 20" not in b or "versionName '0.20.0'" not in b:
    raise SystemExit('v0.21 version patch failed: expected v0.20 build.gradle')
b = b.replace('versionCode 20', 'versionCode 21').replace("versionName '0.20.0'", "versionName '0.21.0'")
build.write_text(b, encoding='utf-8')

print('v0.21 source prepared successfully')
