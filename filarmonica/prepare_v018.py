from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')


def req(old: str, new: str, label: str, count: int | None = 1):
    global text
    found = text.count(old)
    if count is not None and found != count:
        raise SystemExit(f'v0.18 patch failed: {label}: expected {count}, found {found}')
    if count is None and found < 1:
        raise SystemExit(f'v0.18 patch failed: {label}: not found')
    text = text.replace(old, new)

# Rehearsal day names must never break (e.g. Miercuri).
req(
    '            dayRow.addView(tv(day,14.8f,NAVY,true),new LinearLayout.LayoutParams(dp(48),-2));',
    '''            TextView dayView=tv(day,14.2f,NAVY,true);\n            dayView.setSingleLine(true);\n            dayRow.addView(dayView,new LinearLayout.LayoutParams(dp(60),-2));''',
    'single-line rehearsal day'
)

# Give back a little space to the description column after widening the day column.
req(
    '                one.addView(time,new LinearLayout.LayoutParams(dp(92),-2));',
    '                one.addView(time,new LinearLayout.LayoutParams(dp(86),-2));',
    'slightly narrower rehearsal time column'
)

# String distribution stays fully spelled out, but auto-sizes just enough to fit on ONE line.
req(
    '''        TextView line=tv(d,13.4f,NAVY,false);\n        line.setLineSpacing(0,1.06f);\n        pad(line,0,4,0,1);\n        box.addView(line,new LinearLayout.LayoutParams(-1,-2));''',
    '''        TextView line=tv(d,12.8f,NAVY,false);\n        line.setSingleLine(true);\n        line.setAutoSizeTextTypeUniformWithConfiguration(11,13,1,android.util.TypedValue.COMPLEX_UNIT_SP);\n        line.setLineSpacing(0,1.04f);\n        pad(line,0,4,0,1);\n        box.addView(line,new LinearLayout.LayoutParams(-1,-2));''',
    'single-line auto-sized distribution'
)

checks = [
    'dayView.setSingleLine(true)',
    'new LinearLayout.LayoutParams(dp(60),-2)',
    'new LinearLayout.LayoutParams(dp(86),-2)',
    'line.setSingleLine(true)',
    'setAutoSizeTextTypeUniformWithConfiguration(11,13,1,android.util.TypedValue.COMPLEX_UNIT_SP)',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.18 verification failed: {check}')

src.write_text(text, encoding='utf-8')

# v0.17 already removed compile-time source transforms; only bump the app version.
build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 17" not in b or "versionName '0.17.0'" not in b:
    raise SystemExit('v0.18 version patch failed: expected v0.17 build.gradle')
b = b.replace('versionCode 17', 'versionCode 18').replace("versionName '0.17.0'", "versionName '0.18.0'")
build.write_text(b, encoding='utf-8')

print('v0.18 source prepared successfully')
