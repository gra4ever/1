from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')


def method(start_sig: str, end_sig: str, replacement: str, label: str):
    global text
    a = text.find(start_sig)
    b = text.find(end_sig, a)
    if a < 0 or b <= a:
        raise SystemExit(f'v0.24 method patch failed: {label}')
    text = text[:a] + replacement + text[b:]


method(
    '    private void renderNews(JSONObject j) {',
    '    public void showSearch() {',
'''    private void renderNews(JSONObject j) {
        JSONArray items=j.optJSONArray("items");
        if(items==null||items.length()==0) {
            showMessage("Nicio modificare detectată","Modificările viitoare vor apărea aici.");
            return;
        }

        for(int i=0;i<items.length();i++) {
            JSONObject n=items.optJSONObject(i);
            if(n==null) continue;

            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,15,1,BORDER));
            pad(c,14,12,14,12);

            TextView title=tv("Program modificat",15.0f,NAVY,true);
            title.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
            c.addView(title);

            String wl=s(n,"week_label");
            if(!wl.isEmpty()) {
                TextView week=tv(wl,13.4f,GOLD,true);
                pad(week,0,4,0,0);
                c.addView(week);
            }

            String modified=newsModifiedLabel(n);
            if(!modified.isEmpty()) {
                TextView when=tv(modified,11.7f,MUTED,false);
                pad(when,0,7,0,0);
                c.addView(when);
            }

            String actor=s(n,"actor_label");
            if(!actor.isEmpty()) {
                TextView who=tv("de: "+actor,11.7f,MUTED,false);
                pad(who,0,2,0,0);
                c.addView(who);
            }

            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            content.addView(c,p);
        }
    }

    private String newsModifiedLabel(JSONObject n) {
        String iso=s(n,"modified_at");
        if(iso.isEmpty()) iso=s(n,"detected_at");
        if(iso.isEmpty()) return "";
        try {
            java.time.ZonedDateTime local=OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Europe/Bucharest"));
            DateTimeFormatter fmt=DateTimeFormatter.ofPattern("d MMMM yyyy · HH:mm",new Locale("ro","RO"));
            return "Modificat la: "+local.format(fmt);
        } catch(Exception ignored) {
            return "Modificat la: "+iso;
        }
    }

''',
    'Drive Activity timestamp in News'
)

if '"0.23.0"' not in text:
    raise SystemExit('v0.24 app version marker missing')
text = text.replace('"0.23.0"', '"0.24.0"', 1)

build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 23" not in b or "versionName '0.23.0'" not in b:
    raise SystemExit('v0.24 version patch failed: expected v0.23 build.gradle')
b = b.replace('versionCode 23', 'versionCode 24').replace("versionName '0.23.0'", "versionName '0.24.0'")
build.write_text(b, encoding='utf-8')

checks = [
    'String modified=newsModifiedLabel(n);',
    'String actor=s(n,"actor_label");',
    'ZoneId.of("Europe/Bucharest")',
    '"0.24.0"',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.24 verification failed: {check}')

src.write_text(text, encoding='utf-8')
print('v0.24 News timestamp UI prepared successfully')
