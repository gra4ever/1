from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')

old = '''                String display=normalizeConcertLabel(s(l,"display"));\n                if(display.isEmpty()) continue;\n                String[] parts=display.split("\\\\s*\\\\|\\\\s*");'''
new = '''                String display=normalizeConcertLabel(s(l,"display"));\n                if(display.isEmpty()) continue;\n                // A concert is its own schedule block. Source rows often use a semicolon\n                // instead of an explicit separator, e.g. "10:00–13:00;Concert 19:00–21:00"\n                // or "10:00–13:00 - ordinea de Concert; 19:00–21:00 Concert".\n                display=display.replaceAll("(?iu);\\\\s*concert\\\\s+", " | Concert ");\n                display=display.replaceAll("(?iu);\\\\s*(?=\\\\d{1,2}[:.])", " | ");\n                String[] parts=display.split("\\\\s*\\\\|\\\\s*");'''

if text.count(old) != 1:
    raise SystemExit(f'v0.19 patch failed: schedule split marker count={text.count(old)}')
text = text.replace(old, new, 1)

checks = [
    'display=display.replaceAll("(?iu);\\\\s*concert\\\\s+", " | Concert ")',
    'display=display.replaceAll("(?iu);\\\\s*(?=\\\\d{1,2}[:.])", " | ")',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.19 verification failed: {check}')

src.write_text(text, encoding='utf-8')

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 18" not in b or "versionName '0.18.0'" not in b:
    raise SystemExit('v0.19 version patch failed: expected v0.18 build.gradle')
b = b.replace('versionCode 18', 'versionCode 19').replace("versionName '0.18.0'", "versionName '0.19.0'")
build.write_text(b, encoding='utf-8')

print('v0.19 source prepared successfully')
