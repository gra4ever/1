from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')


def req(old: str, new: str, label: str, count: int | None = 1):
    global text
    found = text.count(old)
    if count is not None and found != count:
        raise SystemExit(f'v0.16 patch failed: {label}: expected {count}, found {found}')
    if count is None and found < 1:
        raise SystemExit(f'v0.16 patch failed: {label}: not found')
    text = text.replace(old, new)

# 1) Header: keep the two words intact, but halve the visual gap between them.
req(
    '        titleBlock.addView(line2);',
    '''        LinearLayout.LayoutParams line2Lp = new LinearLayout.LayoutParams(-1, -2);\n        line2Lp.setMargins(0, -dp(4), 0, 0);\n        titleBlock.addView(line2, line2Lp);''',
    'tighter Filarmonica/Transilvania spacing'
)

# 2) Main event/date cards: wider text area and restore the solid blue date bar.
req('        final int outer=14;', '        final int outer=10;', 'wider event cards')
req(
    '        dateBar.setBackground(bg(Color.rgb(116,132,155),18));',
    '        dateBar.setBackground(bg(BLUE,18));',
    'solid date bar color'
)
req('        pad(top,14,11,10,11);', '        pad(top,12,11,6,11);', 'smaller inner horizontal padding')
req(
    '                    if(!line.isEmpty()) info.addView(tv(line,13.5f,NAVY,false));',
    '''                    if(!line.isEmpty()) {\n                        TextView workView=tv(line,13.5f,NAVY,false);\n                        workView.setTextScaleX(.96f);\n                        info.addView(workView);\n                    }''',
    'wider work titles'
)
req(
    '        top.addView(meta,new LinearLayout.LayoutParams(dp(82),-2));',
    '        top.addView(meta,new LinearLayout.LayoutParams(dp(68),-2));',
    'narrower time column'
)

# 3) String distribution: preserve full violin/viola labels, use Cello, keep a clear bullet delimiter,
#    and fit the entire distribution on one line.
req(
    '            if(!dist.isEmpty()) box.addView(tv(dist,11.8f,MUTED,false));',
    '            if(!dist.isEmpty()) addDistributionLine(box,dist);',
    'per-work distribution line'
)
req(
    '            if(!d.isEmpty()) box.addView(tv(d,11.8f,MUTED,false));',
    '            if(!d.isEmpty()) addDistributionLine(box,d);',
    'global distribution line'
)

marker = '    private String cleanDistribution(String x) {'
if marker not in text:
    raise SystemExit('v0.16 patch failed: cleanDistribution marker missing')
helper = '''    private void addDistributionLine(LinearLayout box, String distribution) {\n        String compact=distribution==null?"":distribution\n                .replaceAll("(?iu)violoncel", "Cello")\n                .replaceAll("\\\\s*•\\\\s*", " • ")\n                .replaceAll("\\\\s{2,}", " ")\n                .trim();\n        if(compact.isEmpty()) return;\n        TextView line=tv(compact,10.4f,MUTED,false);\n        line.setSingleLine(true);\n        line.setTextScaleX(.92f);\n        box.addView(line,new LinearLayout.LayoutParams(-1,-2));\n    }\n\n'''
text = text.replace(marker, helper + marker, 1)

# Strong verification: fail instead of silently producing the old UI.
checks = [
    'final int outer=10',
    'dateBar.setBackground(bg(BLUE,18))',
    'new LinearLayout.LayoutParams(dp(68),-2)',
    'workView.setTextScaleX(.96f)',
    'replaceAll("(?iu)violoncel", "Cello")',
    'line.setSingleLine(true)',
    'line2Lp.setMargins(0, -dp(4), 0, 0)',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.16 verification failed: {check}')

src.write_text(text, encoding='utf-8')

# After the v0.15 transform has been materialized into the Java source, remove all compile-time
# source rewriting. The final v0.16 compilation therefore compiles exactly the source above.
Path('app/build.gradle').write_text('''plugins {\n    id 'com.android.application'\n}\n\nandroid {\n    namespace 'ro.filarmonica.transilvania.stagiune'\n    compileSdk 35\n\n    defaultConfig {\n        applicationId 'ro.filarmonica.transilvania.stagiune'\n        minSdk 24\n        targetSdk 35\n        versionCode 16\n        versionName '0.16.0'\n    }\n\n    buildTypes {\n        debug { debuggable true }\n        release { minifyEnabled false }\n    }\n\n    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }\n}\n''', encoding='utf-8')

print('v0.16 source prepared successfully')
