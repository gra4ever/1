from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')

lines = text.splitlines()
changed = 0
for i, line in enumerate(lines):
    if 'display=display.replaceAll' in line and 'concert' in line.lower() and '(?iu);' in line:
        lines[i] = line.replace('(?iu);', '(?iu)[,;]', 1)
        changed += 1

if changed != 1:
    raise SystemExit(f'v0.20 patch failed: expected one concert separator line, changed={changed}')

text = '\n'.join(lines) + ('\n' if text.endswith('\n') else '')

if '(?iu)[,;]' not in text or 'concert' not in text.lower():
    raise SystemExit('v0.20 verification failed: comma/semicolon concert splitter missing')

src.write_text(text, encoding='utf-8')

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 19" not in b or "versionName '0.19.0'" not in b:
    raise SystemExit('v0.20 version patch failed: expected v0.19 build.gradle')
b = b.replace('versionCode 19', 'versionCode 20').replace("versionName '0.19.0'", "versionName '0.20.0'")
build.write_text(b, encoding='utf-8')

print('v0.20 source prepared successfully')
