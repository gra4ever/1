from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')


def req(old: str, new: str, label: str, count: int | None = 1):
    global text
    found = text.count(old)
    if count is not None and found != count:
        raise SystemExit(f'v0.17 patch failed: {label}: expected {count}, found {found}')
    if count is None and found < 1:
        raise SystemExit(f'v0.17 patch failed: {label}: not found')
    text = text.replace(old, new)


def method(start_sig: str, end_sig: str, replacement: str, label: str):
    global text
    a = text.find(start_sig)
    b = text.find(end_sig, a)
    if a < 0 or b <= a:
        raise SystemExit(f'v0.17 method patch failed: {label}')
    text = text[:a] + replacement + text[b:]

# Header: two intact lines, tighter line gap and much less empty space before Orchestra/Recitaluri.
req(
    '        titleBlock.addView(line2);',
    '''        LinearLayout.LayoutParams line2Lp = new LinearLayout.LayoutParams(-1, -2);\n        line2Lp.setMargins(0, -dp(6), 0, 0);\n        titleBlock.addView(line2, line2Lp);''',
    'tighter Filarmonica/Transilvania spacing'
)
req(
    '        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(140)));',
    '        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(122)));',
    'shorter hero'
)

# Date bars: solid blue and slightly wider overall cards.
req('        final int outer=14;', '        final int outer=10;', 'wider cards')
req(
    '        dateBar.setBackground(bg(Color.rgb(116,132,155),18));',
    '        dateBar.setBackground(bg(BLUE,18));',
    'solid date bar'
)

# Event card: header/meta share the first row, but program works below use the FULL card width.
method(
    '    private void addEventCard(Occurrence o, boolean open) {',
    '    private TextView soloistView(JSONObject so) {',
'''    private void addEventCard(Occurrence o, boolean open) {
        JSONObject e=o.event;
        final int outer=10;

        TextView dateBar=tv(dateLabel(o),14.6f,Color.WHITE,true);
        dateBar.setGravity(Gravity.CENTER);
        dateBar.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
        dateBar.setBackground(bg(BLUE,18));
        LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,dp(40));
        dpv.setMargins(dp(outer),0,dp(outer),dp(5));
        content.addView(dateBar,dpv);

        LinearLayout card=vertical();
        card.setBackground(strokeBg(Color.WHITE,18,1,BORDER));
        boolean undefined=isProgramUndefined(e);
        pad(card,12,11,8,11);

        if(undefined) {
            LinearLayout row=horizontal();
            TextView u=tv("Program nedefinitivat",17,GRAY,false);
            u.setTypeface(Typeface.create(Typeface.SERIF,Typeface.ITALIC));
            row.addView(u,new LinearLayout.LayoutParams(0,-2,1));
            TextView arrow=tv(open?"⌃":"⌄",23,NAVY,true);
            arrow.setGravity(Gravity.END);
            row.addView(arrow,new LinearLayout.LayoutParams(dp(42),-2));
            card.addView(row);

            LinearLayout details=vertical();
            details.setVisibility(open?View.VISIBLE:View.GONE);
            renderScheduleSection(details,e);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(dp(outer),0,dp(outer),open?dp(7):dp(12));
            content.addView(card,cp);
            LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-1,-2);
            detailLp.setMargins(dp(outer),0,dp(outer),dp(12));
            content.addView(details,detailLp);
            View.OnClickListener toggle=v->{
                boolean now=details.getVisibility()==View.VISIBLE;
                details.setVisibility(now?View.GONE:View.VISIBLE);
                arrow.setText(now?"⌄":"⌃");
                openOccurrenceKey=now?null:o.key;
            };
            row.setOnClickListener(toggle); arrow.setOnClickListener(toggle); dateBar.setOnClickListener(toggle);
            return;
        }

        LinearLayout headerRow=horizontal();
        headerRow.setGravity(Gravity.TOP);
        LinearLayout identity=vertical();
        JSONObject conductor=e.optJSONObject("conductor");
        String cn=s(conductor,"name");
        if(!cn.isEmpty()) {
            if(conductor!=null&&conductor.optBoolean("choir",false)) cn += " + COR";
            TextView c=tv(cn,20.5f,NAVY,true);
            c.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
            identity.addView(c);
        }
        JSONArray solo=e.optJSONArray("soloists");
        if(solo!=null) {
            for(int i=0;i<solo.length();i++) {
                TextView sv=soloistView(solo.optJSONObject(i));
                if(sv!=null) {
                    sv.setTextSize(15.2f);
                    if(i>0) pad(sv,0,2,0,0);
                    identity.addView(sv);
                }
            }
        }
        headerRow.addView(identity,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout meta=vertical();
        meta.setGravity(Gravity.RIGHT);
        String time=o.time;
        if(time.isEmpty()&&"orchestra".equals(s(e,"type"))) time="19:00";
        TextView tm=tv(time,14.2f,NAVY,true); tm.setGravity(Gravity.END); meta.addView(tm);
        String venue=s(e,"venue");
        if(shouldShowVenue(venue)) {
            TextView vv=tv(venue,11.5f,NAVY,false); vv.setGravity(Gravity.END); pad(vv,0,3,0,0); meta.addView(vv);
        }
        TextView arrow=tv(open?"⌃":"⌄",23,NAVY,true); arrow.setGravity(Gravity.END); pad(arrow,0,7,0,0); meta.addView(arrow);
        headerRow.addView(meta,new LinearLayout.LayoutParams(dp(58),-2));
        card.addView(headerRow);

        JSONArray works=e.optJSONArray("works");
        if(works!=null&&works.length()>0) {
            Space gap=new Space(this); card.addView(gap,new LinearLayout.LayoutParams(1,dp(7)));
            for(int i=0;i<works.length();i++) {
                String line=workLine(works.optJSONObject(i));
                if(!line.isEmpty()) {
                    TextView workView=tv(line,14.8f,NAVY,false);
                    workView.setTextScaleX(.98f);
                    card.addView(workView,new LinearLayout.LayoutParams(-1,-2));
                }
            }
        }

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(dp(outer),0,dp(outer),open?dp(7):dp(12));
        content.addView(card,cp);

        LinearLayout details=vertical();
        details.setVisibility(open?View.VISIBLE:View.GONE);
        details.setBackgroundColor(Color.TRANSPARENT);
        renderWorksSection(details,e);
        renderScheduleSection(details,e);
        LinearLayout.LayoutParams detailLp=new LinearLayout.LayoutParams(-1,-2);
        detailLp.setMargins(dp(outer),0,dp(outer),dp(12));
        content.addView(details,detailLp);

        if(open) { expandedDetails=details; expandedArrow=arrow; }
        View.OnClickListener toggle=v->{
            boolean now=details.getVisibility()==View.VISIBLE;
            if(now) {
                details.setVisibility(View.GONE); arrow.setText("⌄"); openOccurrenceKey=null;
                if(expandedDetails==details) { expandedDetails=null; expandedArrow=null; }
                cp.bottomMargin=dp(12); card.setLayoutParams(cp);
            } else {
                if(expandedDetails!=null&&expandedDetails!=details) {
                    expandedDetails.setVisibility(View.GONE);
                    if(expandedArrow!=null) expandedArrow.setText("⌄");
                }
                details.setVisibility(View.VISIBLE); arrow.setText("⌃"); openOccurrenceKey=o.key;
                expandedDetails=details; expandedArrow=arrow;
                cp.bottomMargin=dp(7); card.setLayoutParams(cp);
            }
        };
        headerRow.setOnClickListener(toggle);
        arrow.setOnClickListener(toggle);
        dateBar.setOnClickListener(toggle);
        card.setOnClickListener(toggle);

        if(targetEventId!=null&&targetEventId.equals(s(e,"id"))) targetView=dateBar;
    }

''',
    'full-width event program layout'
)

# Programul lucrărilor: keep large readable text, and make string distribution prominent/full-width.
req(
    '            if(!dist.isEmpty()) box.addView(tv(dist,11.8f,MUTED,false));',
    '            if(!dist.isEmpty()) addDistributionLine(box,dist);',
    'per-work distribution'
)
req(
    '            if(!d.isEmpty()) box.addView(tv(d,11.8f,MUTED,false));',
    '            if(!d.isEmpty()) addDistributionLine(box,d);',
    'global distribution'
)
marker='    private String cleanDistribution(String x) {'
if marker not in text:
    raise SystemExit('v0.17 patch failed: cleanDistribution marker missing')
helper='''    private void addDistributionLine(LinearLayout box, String distribution) {\n        String d=distribution==null?"":distribution\n                .replaceAll("(?iu)violoncel", "Cello")\n                .replaceAll("(?iu)violoncello", "Cello")\n                .replaceAll("\\\\s*•\\\\s*", " • ")\n                .replaceAll("\\\\s{2,}", " ")\n                .trim();\n        if(d.isEmpty()) return;\n        TextView line=tv(d,13.4f,NAVY,false);\n        line.setLineSpacing(0,1.06f);\n        pad(line,0,4,0,1);\n        box.addView(line,new LinearLayout.LayoutParams(-1,-2));\n    }\n\n'''
text=text.replace(marker,helper+marker,1)

checks=[
    'content.addView(hero, new LinearLayout.LayoutParams(-1, dp(122)))',
    'final int outer=10',
    'dateBar.setBackground(bg(BLUE,18))',
    'headerRow.addView(meta,new LinearLayout.LayoutParams(dp(58),-2))',
    'TextView workView=tv(line,14.8f,NAVY,false)',
    'sv.setTextSize(15.2f)',
    'TextView line=tv(d,13.4f,NAVY,false)',
    'replaceAll("(?iu)violoncel", "Cello")',
    'line2Lp.setMargins(0, -dp(6), 0, 0)',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.17 verification failed: {check}')

src.write_text(text,encoding='utf-8')

Path('app/build.gradle').write_text('''plugins {\n    id 'com.android.application'\n}\n\nandroid {\n    namespace 'ro.filarmonica.transilvania.stagiune'\n    compileSdk 35\n\n    defaultConfig {\n        applicationId 'ro.filarmonica.transilvania.stagiune'\n        minSdk 24\n        targetSdk 35\n        versionCode 17\n        versionName '0.17.0'\n    }\n\n    buildTypes {\n        debug { debuggable true }\n        release { minifyEnabled false }\n    }\n\n    compileOptions {\n        sourceCompatibility JavaVersion.VERSION_17\n        targetCompatibility JavaVersion.VERSION_17\n    }\n}\n''',encoding='utf-8')
print('v0.17 source prepared successfully')
