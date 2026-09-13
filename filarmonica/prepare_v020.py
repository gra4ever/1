from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')

old = '''                display=display.replaceAll("(?iu);\\s*concert\\s+", " | Concert ");\n                display=display.replaceAll("(?iu);\\s*(?=\\d{1,2}[:.])", " | ");'''
new = '''                // Concert must always be displayed on its own row. The source may use\n                // either comma or semicolon before the word Concert.\n                display=display.replaceAll("(?iu)[,;]\\s*concert\\s+", " | Concert ");\n                display=display.replaceAll("(?iu);\\s*(?=\\d{1,2}[:.])", " | ");'''

if text.count(old) != 1:
    raise SystemExit(f'v0.20 patch failed: concert separator marker count={text.count(old)}')
text = text.replace(old, new, 1)

checks = [
    'display=display.replaceAll("(?iu)[,;]\\\\s*concert\\\\s+", " | Concert ")',
    'Concert must always be displayed on its own row',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.20 verification failed: {check}')

src.write_text(text, encoding='utf-8')

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 19" not in b or "versionName '0.19.0'" not in b:
    raise SystemExit('v0.20 version patch failed: expected v0.19 build.gradle')
b = b.replace('versionCode 19', 'versionCode 20').replace("versionName '0.19.0'", "versionName '0.20.0'")
build.write_text(b, encoding='utf-8')

print('v0.20 source prepared successfully')
