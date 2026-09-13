from pathlib import Path

src = Path('app/src/main/java/ro/filarmonica/transilvania/stagiune/FinalMainActivity.java')
text = src.read_text(encoding='utf-8')


def req(old: str, new: str, label: str, count: int | None = 1):
    global text
    found = text.count(old)
    if count is not None and found != count:
        raise SystemExit(f'v0.23 patch failed: {label}: expected {count}, found {found}')
    if count is None and found < 1:
        raise SystemExit(f'v0.23 patch failed: {label}: not found')
    text = text.replace(old, new, 1 if count == 1 else -1)


def method(start_sig: str, end_sig: str, replacement: str, label: str):
    global text
    a = text.find(start_sig)
    b = text.find(end_sig, a)
    if a < 0 or b <= a:
        raise SystemExit(f'v0.23 method patch failed: {label}')
    text = text[:a] + replacement + text[b:]

# Imports needed by the protected public API client.
req('import android.os.Bundle;\n', 'import android.os.Bundle;\nimport android.os.Build;\n', 'Build import')
req('import android.text.Editable;\n', 'import android.text.Editable;\nimport android.text.InputType;\n', 'InputType import')
req('import java.io.InputStreamReader;\n', 'import java.io.InputStreamReader;\nimport java.io.OutputStream;\n', 'OutputStream import')
req('import java.util.Set;\n', 'import java.util.Set;\nimport java.util.UUID;\n', 'UUID import')

# Public HTTPS endpoint. Program endpoints themselves remain protected by per-installation tokens.
req('    private static final String API_BASE = "http://192.168.0.191:8787";',
    '    private static final String API_BASE = "https://stagiune-api.accesorii-muzicale.ro";',
    'public API base')

req('    private static final String CACHE_SYNC = "sync_time";\n', '''    private static final String CACHE_SYNC = "sync_time";
    private static final String PREF_DEVICE_ID = "device_id_v23";
    private static final String PREF_DEVICE_TOKEN = "device_token_v23";
    private static final String PREF_USER_NAME = "access_name_v23";
    private static final String PREF_USER_EMAIL = "access_email_v23";
    private static final String PREF_APPROVED = "access_approved_v23";
    private static final String PREF_NOTIFICATIONS = "notifications_v23";
''', 'access preference keys')

req('    private Runnable searchRunnable;\n', '''    private Runnable searchRunnable;
    private Runnable accessPollRunnable;
''', 'access poll field')

# Gate the whole app behind the device approval flow. Old v0.22 cache is never shown to a new/unapproved installation.
method(
    '    @Override public void onCreate(Bundle b) {',
    '    private int dp(int v)',
'''    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        ensureDeviceIdentity();
        String name=getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_USER_NAME,"");
        String email=getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_USER_EMAIL,"");
        if(name.trim().isEmpty() || email.trim().isEmpty()) {
            clearPrivateCache();
            showAccessRequestForm(null);
            return;
        }
        checkAccessThenStart(true);
    }

    private void ensureDeviceIdentity() {
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        String id=p.getString(PREF_DEVICE_ID,"");
        String token=p.getString(PREF_DEVICE_TOKEN,"");
        if(id==null||id.length()<8) id=UUID.randomUUID().toString();
        if(token==null||token.length()<24) token=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");
        p.edit().putString(PREF_DEVICE_ID,id).putString(PREF_DEVICE_TOKEN,token).apply();
    }

    private void clearPrivateCache() {
        program=null;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .remove(CACHE_PROGRAM).remove(CACHE_NEWS).remove(CACHE_SYNC)
                .putBoolean(PREF_APPROVED,false).apply();
    }

    private LinearLayout accessRoot(String title, String subtitle) {
        LinearLayout root=vertical();
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true);
        LinearLayout body=vertical(); pad(body,20,34,20,28);
        TextView brand=tv("S T A G I U N E   F S T",10,GOLD,true); brand.setGravity(Gravity.CENTER); body.addView(brand);
        TextView h=tv(title,28,NAVY,true); h.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); h.setGravity(Gravity.CENTER); pad(h,0,12,0,7); body.addView(h);
        TextView sub=tv(subtitle,13,MUTED,false); sub.setGravity(Gravity.CENTER); sub.setLineSpacing(0,1.15f); body.addView(sub);
        Space sp=new Space(this); body.addView(sp,new LinearLayout.LayoutParams(1,dp(24)));
        sc.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root); root.requestApplyInsets();
        body.setTag(sc);
        return body;
    }

    private void showAccessRequestForm(String message) {
        cancelAccessPoll();
        LinearLayout body=accessRoot("Solicită acces","Programul este disponibil numai persoanelor aprobate de administrator.");
        if(message!=null&&!message.isEmpty()) {
            TextView m=tv(message,12.5f,Color.rgb(150,72,52),true); m.setGravity(Gravity.CENTER); pad(m,0,0,0,12); body.addView(m);
        }
        LinearLayout card=vertical(); card.setBackground(strokeBg(Color.WHITE,18,1,BORDER)); pad(card,16,16,16,16);
        TextView l1=tv("Nume și prenume",12,NAVY,true); card.addView(l1);
        EditText name=new EditText(this); name.setSingleLine(true); name.setTextSize(15); name.setTextColor(NAVY); name.setHint("Nume și prenume");
        name.setText(getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_USER_NAME,""));
        name.setBackground(strokeBg(SOFT,12,1,BORDER)); pad(name,12,10,12,10);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(50)); ip.setMargins(0,dp(6),0,dp(14)); card.addView(name,ip);

        card.addView(tv("Email",12,NAVY,true));
        EditText email=new EditText(this); email.setSingleLine(true); email.setTextSize(15); email.setTextColor(NAVY); email.setHint("nume@email.ro");
        email.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        email.setText(getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_USER_EMAIL,""));
        email.setBackground(strokeBg(SOFT,12,1,BORDER)); pad(email,12,10,12,10);
        LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(50)); ep.setMargins(0,dp(6),0,dp(16)); card.addView(email,ep);

        TextView send=tv("Solicită acces",15,Color.WHITE,true); send.setGravity(Gravity.CENTER); send.setBackground(blueGradient(14));
        card.addView(send,new LinearLayout.LayoutParams(-1,dp(50)));
        send.setOnClickListener(v->{
            String n=name.getText().toString().trim(); String em=email.getText().toString().trim();
            if(n.length()<2) { name.setError("Completează numele"); return; }
            if(!em.contains("@")||!em.substring(em.indexOf('@')+1).contains(".")) { email.setError("Email invalid"); return; }
            send.setEnabled(false); send.setAlpha(.6f); requestAccess(n,em);
        });

        TextView privacy=tv("Aplicația transmite numele, emailul, modelul telefonului, versiunea Android și datele de utilizare necesare administrării accesului. Nu solicită acces la contacte, locație, cameră, microfon sau fișiere.",10.5f,MUTED,false);
        privacy.setLineSpacing(0,1.12f); pad(privacy,2,14,2,0); card.addView(privacy);
        body.addView(card,new LinearLayout.LayoutParams(-1,-2));
    }

    private void requestAccess(String name,String email) {
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        p.edit().putString(PREF_USER_NAME,name).putString(PREF_USER_EMAIL,email).putBoolean(PREF_APPROVED,false).apply();
        new Thread(()->{
            try {
                JSONObject body=new JSONObject();
                body.put("device_id",p.getString(PREF_DEVICE_ID,""));
                body.put("device_token",p.getString(PREF_DEVICE_TOKEN,""));
                body.put("name",name); body.put("email",email);
                body.put("manufacturer",Build.MANUFACTURER==null?"":Build.MANUFACTURER);
                body.put("model",Build.MODEL==null?"":Build.MODEL);
                body.put("device_name",Build.DEVICE==null?"":Build.DEVICE);
                body.put("android_version",Build.VERSION.RELEASE==null?"":Build.VERSION.RELEASE);
                body.put("app_version","0.23.0");
                JSONObject result=postJson("/api/access/request",body);
                String status=s(result,"status");
                runOnUiThread(()->showAccessWaiting(status));
            } catch(Exception e) {
                runOnUiThread(()->showAccessRequestForm("Cererea nu a putut fi trimisă: "+shortError(e)));
            }
        }).start();
    }

    private void showAccessWaiting(String status) {
        cancelAccessPoll();
        String title="Cerere trimisă";
        String text="Cererea ta este în așteptarea aprobării. După aprobare, aplicația se va deschide automat.";
        if("denied".equals(status)) { title="Acces respins"; text="Administratorul a respins această solicitare."; }
        if("revoked".equals(status)) { title="Acces revocat"; text="Accesul acestui telefon a fost revocat de administrator."; }
        LinearLayout body=accessRoot(title,text);
        LinearLayout card=vertical(); card.setBackground(strokeBg(Color.WHITE,18,1,BORDER)); pad(card,16,16,16,16);
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        card.addView(tv(p.getString(PREF_USER_NAME,""),17,NAVY,true));
        card.addView(tv(p.getString(PREF_USER_EMAIL,""),12,MUTED,false));
        String phone=(Build.MANUFACTURER+" "+Build.MODEL).trim(); TextView ph=tv(phone+" · Android "+Build.VERSION.RELEASE,11,MUTED,false); pad(ph,0,7,0,12); card.addView(ph);
        TextView state=tv("pending".equals(status)||status.isEmpty()?"ÎN AȘTEPTARE":status.toUpperCase(Locale.ROOT),12,GOLD,true); card.addView(state);
        TextView retry=tv("Verifică acum",14,Color.WHITE,true); retry.setGravity(Gravity.CENTER); retry.setBackground(blueGradient(14));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(48)); rp.setMargins(0,dp(16),0,0); card.addView(retry,rp);
        retry.setOnClickListener(v->checkAccessThenStart(false));
        body.addView(card,new LinearLayout.LayoutParams(-1,-2));
        if("pending".equals(status)||status.isEmpty()) scheduleAccessPoll();
    }

    private void scheduleAccessPoll() {
        cancelAccessPoll();
        accessPollRunnable=()->checkAccessThenStart(false);
        handler.postDelayed(accessPollRunnable,5000);
    }

    private void cancelAccessPoll() {
        if(accessPollRunnable!=null) handler.removeCallbacks(accessPollRunnable);
        accessPollRunnable=null;
    }

    private void checkAccessThenStart(boolean allowOfflineCache) {
        new Thread(()->{
            try {
                JSONObject st=getJson("/api/access/status");
                String status=s(st,"status");
                if(st.optBoolean("approved",false)||"approved".equals(status)) {
                    getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(PREF_APPROVED,true).apply();
                    runOnUiThread(this::startApprovedApp);
                } else {
                    clearPrivateCache();
                    runOnUiThread(()->showAccessWaiting(status));
                }
            } catch(Exception e) {
                boolean wasApproved=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean(PREF_APPROVED,false);
                String cached=getSharedPreferences(PREFS,MODE_PRIVATE).getString(CACHE_PROGRAM,null);
                if(allowOfflineCache&&wasApproved&&cached!=null) {
                    try { program=new JSONObject(cached); chooseInitialMonth(); } catch(Exception ignored) {}
                    runOnUiThread(()->{ showProgram(false,null); Toast.makeText(this,"Folosesc copia salvată. Conexiunea va fi verificată din nou când revine internetul.",Toast.LENGTH_LONG).show(); });
                } else {
                    runOnUiThread(()->showAccessRequestForm("Nu pot verifica accesul acum: "+shortError(e)));
                }
            }
        }).start();
    }

    private void startApprovedApp() {
        cancelAccessPoll();
        String cached=getSharedPreferences(PREFS,MODE_PRIVATE).getString(CACHE_PROGRAM,null);
        syncTime=getSharedPreferences(PREFS,MODE_PRIVATE).getString(CACHE_SYNC,"—");
        if(cached!=null) try { program=new JSONObject(cached); chooseInitialMonth(); } catch(Exception ignored) {}
        showProgram(false,null);
        logAppOpen();
        loadProgramAndStatus();
    }

    private void logAppOpen() {
        new Thread(()->{ try { postJson("/api/activity/open",new JSONObject()); } catch(Exception ignored) {} }).start();
    }

    private int dp(int v)''',
    'approval-gated onCreate and access UI'
)

# Four navigation destinations; Settings hosts the notification master switch.
req(
'''        addNav("▣\\nProgram", "Program".equals(selected), () -> showProgram(recitalSelected, null));
        addNav("▤\\nNoutăți", "Noutăți".equals(selected), this::showNews);
        addNav("⌕\\nSearch", "Search".equals(selected), this::showSearch);''',
'''        addNav("▣\\nProgram", "Program".equals(selected), () -> showProgram(recitalSelected, null));
        addNav("▤\\nNoutăți", "Noutăți".equals(selected), this::showNews);
        addNav("⌕\\nCaută", "Search".equals(selected), this::showSearch);
        addNav("⚙\\nSetări", "Setări".equals(selected), this::showSettings);''',
    'bottom settings navigation')

# Search: show production week first, then people and the full program. One card per matched production/week.
method(
    '    private void localSearch(String query,LinearLayout results) {',
    '    private boolean matches(JSONObject e,String needle)',
'''    private void localSearch(String query,LinearLayout results) {
        results.removeAllViews();
        if(query.length()<2) { TextView x=tv("Scrie cel puțin 2 caractere.",12,MUTED,false); x.setGravity(Gravity.CENTER); results.addView(x); return; }
        if(program==null) return;
        logSearchQuery(query);
        String needle=norm(query); List<JSONObject> found=new ArrayList<>(); Set<String> seenResults=new HashSet<>(); JSONArray months=program.optJSONArray("months");
        if(months!=null) for(int mi=0;mi<months.length();mi++) {
            JSONObject m=months.optJSONObject(mi); if(m==null) continue;
            JSONArray weeks=m.optJSONArray("weeks");
            if(weeks!=null) for(int wi=0;wi<weeks.length();wi++) {
                JSONObject w=weeks.optJSONObject(wi); JSONArray ev=w==null?null:w.optJSONArray("events");
                if(ev!=null) for(int ei=0;ei<ev.length();ei++) {
                    JSONObject e=ev.optJSONObject(ei); if(e==null||!matches(e,needle)) continue;
                    String id=s(e,"id"); if(id.isEmpty()) id="m"+mi+"w"+wi+"e"+ei;
                    if(seenResults.add(id)) { tagMonth(e,mi); found.add(e); }
                }
            }
            JSONArray rec=m.optJSONArray("recitals"); if(rec==null) rec=m.optJSONArray("tmc_recitals");
            if(rec!=null) for(int ei=0;ei<rec.length();ei++) {
                JSONObject e=rec.optJSONObject(ei); if(e==null||!matches(e,needle)) continue;
                String id=s(e,"id"); if(id.isEmpty()) id="m"+mi+"r"+ei;
                if(seenResults.add(id)) { tagMonth(e,mi); found.add(e); }
            }
        }
        if(found.isEmpty()) { TextView x=tv("Niciun rezultat.",12,MUTED,false); x.setGravity(Gravity.CENTER); results.addView(x); return; }
        for(JSONObject e:found) {
            LinearLayout c=vertical(); c.setBackground(strokeBg(Color.WHITE,14,1,BORDER)); pad(c,14,11,14,12);
            String week=searchWeekLabel(e);
            if(!week.isEmpty()) {
                TextView wv=tv(week,15.5f,GOLD,true); wv.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); c.addView(wv);
            }
            String cn=s(e.optJSONObject("conductor"),"name"); if(!cn.isEmpty()) { TextView cv=tv(cn,15,NAVY,true); pad(cv,0,5,0,0); c.addView(cv); }
            JSONArray solo=e.optJSONArray("soloists");
            if(solo!=null) for(int i=0;i<solo.length();i++) {
                TextView sv=soloistView(solo.optJSONObject(i)); if(sv!=null) { sv.setTextSize(13.5f); pad(sv,0,3,0,0); c.addView(sv); }
            }
            JSONArray works=e.optJSONArray("works");
            if(works!=null&&works.length()>0) {
                TextView ph=tv("Program",11,GOLD,true); pad(ph,0,9,0,2); c.addView(ph);
                for(int i=0;i<works.length();i++) {
                    String line=workLine(works.optJSONObject(i)); if(!line.isEmpty()) { TextView w=tv(line,12.5f,NAVY,false); pad(w,0,2,0,0); c.addView(w); }
                }
            } else {
                TextView undef=tv("Program nedefinitivat",12.5f,GRAY,false); undef.setTypeface(Typeface.create(Typeface.SERIF,Typeface.ITALIC)); pad(undef,0,8,0,0); c.addView(undef);
            }
            c.setOnClickListener(v->openSearchResult(e));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),0,dp(14),dp(9)); results.addView(c,p);
        }
    }

    private String searchWeekLabel(JSONObject e) {
        JSONObject w=e.optJSONObject("week");
        String out=s(w,"collapsed_label"); if(out.isEmpty()) out=s(w,"label");
        if(out.isEmpty()) {
            Object wo=e.opt("week"); if(wo instanceof String) out=((String)wo).trim();
        }
        if(out.isEmpty()) out=s(e.optJSONObject("raw"),"date");
        if(out.isEmpty()) {
            List<Occurrence> occ=eventOccurrences(e,"orchestra".equals(s(e,"type")));
            if(!occ.isEmpty()) out=dateLabel(occ.get(0));
        }
        if(out.isEmpty()) return "Săptămână program";
        String n=out.trim();
        if(!norm(n).startsWith("saptamana")&&!norm(n).startsWith("perioada")) return "Perioada "+n;
        return n;
    }

    private void logSearchQuery(String query) {
        if(query==null||query.trim().length()<2) return;
        new Thread(()->{
            try {
                String q=java.net.URLEncoder.encode(query.trim(),StandardCharsets.UTF_8.toString());
                getJson("/api/search?q="+q+"&limit=1");
            } catch(Exception ignored) {}
        }).start();
    }

''',
    'week-first rich search results'
)

# Log a concert/result opening and preserve the existing navigate-to-event behavior.
req(
'''    private void openSearchResult(JSONObject e) {
        selectedMonthIndex=e.optInt("_ui_month_index",selectedMonthIndex); monthDropdownOpen=false; openOccurrenceKey=null;
        showProgram("recital".equals(s(e,"type")),s(e,"id"));
    }
''',
'''    private void openSearchResult(JSONObject e) {
        logEventOpen(e);
        selectedMonthIndex=e.optInt("_ui_month_index",selectedMonthIndex); monthDropdownOpen=false; openOccurrenceKey=null;
        showProgram("recital".equals(s(e,"type")),s(e,"id"));
    }

    private void logEventOpen(JSONObject e) {
        String id=s(e,"id"); if(id.isEmpty()) return;
        new Thread(()->{
            try {
                String q=java.net.URLEncoder.encode(id,StandardCharsets.UTF_8.toString());
                postJson("/api/activity/event-open?event_id="+q,new JSONObject());
            } catch(Exception ignored) {}
        }).start();
    }
''',
    'event-open audit')

# Opening a normal program card is also activity worth recording. This exact line comes from v0.17.
req('                details.setVisibility(View.VISIBLE); arrow.setText("⌃"); openOccurrenceKey=o.key;\n',
    '                details.setVisibility(View.VISIBLE); arrow.setText("⌃"); openOccurrenceKey=o.key; logEventOpen(e);\n',
    'program card open audit')

# Settings page with a backend-persisted notifications master switch.
insert_marker='    private void manualRefresh(TextView button) {'
if insert_marker not in text:
    raise SystemExit('v0.23 settings insertion marker missing')
settings='''    public void showSettings() {
        shell("Setări"); addSimplePageTitle("Setări");
        android.content.SharedPreferences p=getSharedPreferences(PREFS,MODE_PRIVATE);
        LinearLayout account=vertical(); account.setBackground(strokeBg(Color.WHITE,16,1,BORDER)); pad(account,14,12,14,12);
        account.addView(tv(p.getString(PREF_USER_NAME,""),16,NAVY,true));
        account.addView(tv(p.getString(PREF_USER_EMAIL,""),12,MUTED,false));
        TextView device=tv((Build.MANUFACTURER+" "+Build.MODEL).trim()+" · Android "+Build.VERSION.RELEASE,11,MUTED,false); pad(device,0,5,0,0); account.addView(device);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2); ap.setMargins(dp(14),0,dp(14),dp(10)); content.addView(account,ap);

        LinearLayout notif=vertical(); notif.setBackground(strokeBg(Color.WHITE,16,1,BORDER)); pad(notif,14,12,14,12);
        notif.addView(tv("Notificări",15,NAVY,true));
        TextView desc=tv("Primește notificări când apar modificări de program. Poți schimba această opțiune oricând.",11.5f,MUTED,false); pad(desc,0,4,0,10); notif.addView(desc);
        boolean enabled=p.getBoolean(PREF_NOTIFICATIONS,true);
        TextView toggle=tv(enabled?"Notificări pornite":"Notificări oprite",14,Color.WHITE,true); toggle.setGravity(Gravity.CENTER); toggle.setTag(Boolean.valueOf(enabled));
        toggle.setBackground(enabled?blueGradient(14):bg(GRAY,14)); notif.addView(toggle,new LinearLayout.LayoutParams(-1,dp(48)));
        toggle.setOnClickListener(v->{ boolean current=Boolean.TRUE.equals(toggle.getTag()); setNotificationPreference(!current,toggle); });
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2); np.setMargins(dp(14),0,dp(14),dp(10)); content.addView(notif,np);

        new Thread(()->{
            try {
                JSONObject pref=getJson("/api/preferences"); boolean server=pref.optBoolean("notifications_enabled",true);
                p.edit().putBoolean(PREF_NOTIFICATIONS,server).apply();
                runOnUiThread(()->updateNotificationButton(toggle,server));
            } catch(Exception ignored) {}
        }).start();
    }

    private void updateNotificationButton(TextView button,boolean enabled) {
        button.setTag(Boolean.valueOf(enabled));
        button.setText(enabled?"Notificări pornite":"Notificări oprite");
        button.setBackground(enabled?blueGradient(14):bg(GRAY,14));
    }

    private void setNotificationPreference(boolean enabled,TextView button) {
        button.setEnabled(false); button.setAlpha(.65f);
        new Thread(()->{
            try {
                JSONObject body=new JSONObject(); body.put("enabled",enabled);
                JSONObject result=postJson("/api/preferences/notifications",body);
                boolean actual=result.optBoolean("notifications_enabled",enabled);
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean(PREF_NOTIFICATIONS,actual).apply();
                runOnUiThread(()->{ button.setEnabled(true); button.setAlpha(1f); updateNotificationButton(button,actual); Toast.makeText(this,actual?"Notificările sunt pornite.":"Notificările sunt oprite.",Toast.LENGTH_SHORT).show(); });
            } catch(Exception e) {
                runOnUiThread(()->{ button.setEnabled(true); button.setAlpha(1f); Toast.makeText(this,"Nu am putut salva setarea: "+shortError(e),Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

'''
text=text.replace(insert_marker,settings+insert_marker,1)

# Replace the HTTP helper: all protected calls carry the installation token; POST is used for registration/activity/preferences.
method(
    '    private JSONObject getJson(String path) throws Exception {',
    '    private void updateSyncFromStatus(JSONObject st)',
'''    private JSONObject getJson(String path) throws Exception { return requestJson("GET",path,null); }
    private JSONObject postJson(String path,JSONObject body) throws Exception { return requestJson("POST",path,body); }

    private JSONObject requestJson(String method,String path,JSONObject body) throws Exception {
        URL url=new URL(API_BASE+path); HttpURLConnection c=(HttpURLConnection)url.openConnection(); c.setRequestMethod(method);
        c.setConnectTimeout(8000); c.setReadTimeout(24000); c.setRequestProperty("Accept","application/json");
        String token=getSharedPreferences(PREFS,MODE_PRIVATE).getString(PREF_DEVICE_TOKEN,"");
        if(token!=null&&!token.isEmpty()) c.setRequestProperty("Authorization","Bearer "+token);
        if(body!=null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type","application/json; charset=utf-8");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()) { out.write(bytes); }
        }
        int code=c.getResponseCode();
        java.io.InputStream stream=(code>=200&&code<300)?c.getInputStream():c.getErrorStream();
        StringBuilder sb=new StringBuilder();
        if(stream!=null) {
            BufferedReader r=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
            String line; while((line=r.readLine())!=null) sb.append(line); r.close();
        }
        c.disconnect();
        String raw=sb.toString();
        if(code<200||code>=300) {
            String detail="";
            try { detail=new JSONObject(raw).optString("detail",""); } catch(Exception ignored) {}
            throw new Exception("HTTP "+code+(detail.isEmpty()?"":" · "+detail));
        }
        return raw.trim().isEmpty()?new JSONObject():new JSONObject(raw);
    }

    private void updateSyncFromStatus(JSONObject st)''',
    'authenticated HTTP client'
)

# Version bump after v0.22. Keep the working adaptive icon and label.
build = Path('app/build.gradle')
b = build.read_text(encoding='utf-8')
if "versionCode 22" not in b or "versionName '0.22.0'" not in b:
    raise SystemExit('v0.23 version patch failed: expected v0.22 build.gradle')
b = b.replace('versionCode 22', 'versionCode 23').replace("versionName '0.22.0'", "versionName '0.23.0'")
build.write_text(b, encoding='utf-8')

checks=[
    'https://stagiune-api.accesorii-muzicale.ro',
    'Solicită acces',
    '/api/access/request',
    '/api/access/status',
    'Authorization","Bearer "+token',
    'Perioada "+n',
    'Notificări pornite',
    '/api/preferences/notifications',
    'logSearchQuery(query)',
    'logEventOpen(e)',
]
for check in checks:
    if check not in text:
        raise SystemExit(f'v0.23 verification failed: {check}')

src.write_text(text,encoding='utf-8')
print('v0.23 protected public API, approval flow, rich search and notification settings prepared successfully')
