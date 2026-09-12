package ro.filarmonica.transilvania.stagiune;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FinalMainActivity extends Activity {
    private static final String API_BASE = "http://192.168.0.191:8787";
    private static final String PREFS = "stagiune_prefs_v4";
    private static final String CACHE_PROGRAM = "program_json";
    private static final String CACHE_NEWS = "news_json";
    private static final String CACHE_SYNC = "sync_time";

    private final int NAVY = Color.rgb(13, 45, 88);
    private final int NAVY_2 = Color.rgb(22, 67, 119);
    private final int GOLD = Color.rgb(173, 124, 45);
    private final int PAPER = Color.rgb(250, 248, 243);
    private final int WHITE = Color.rgb(255, 255, 255);
    private final int MUTED = Color.rgb(92, 103, 120);
    private final int BORDER = Color.rgb(220, 216, 207);
    private final int SOFT = Color.rgb(246, 244, 239);
    private final int GRAY = Color.rgb(177, 174, 167);

    private LinearLayout content;
    private LinearLayout bottom;
    private ScrollView scroll;
    private JSONObject program;
    private int selectedMonthIndex = 0;
    private boolean recitalSelected = false;
    private boolean monthDropdownOpen = false;
    private String openOccurrenceKey = null;
    private String targetEventId = null;
    private View targetView = null;
    private String syncTime = "—";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    private static class Occurrence {
        JSONObject event;
        LocalDate date;
        String time;
        String key;
        String dayOverride;
        String altDate;
        Occurrence(JSONObject event, LocalDate date, String time, String key) {
            this.event = event;
            this.date = date;
            this.time = time == null ? "" : time;
            this.key = key;
        }
    }

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        String cached = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_PROGRAM, null);
        syncTime = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_SYNC, "—");
        if (cached != null) {
            try {
                program = new JSONObject(cached);
                chooseInitialMonth();
            } catch (Exception ignored) {}
        }
        showProgram(false, null);
        loadProgramAndStatus();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }
    private LinearLayout vertical() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.VERTICAL); return x; }
    private LinearLayout horizontal() { LinearLayout x = new LinearLayout(this); x.setOrientation(LinearLayout.HORIZONTAL); x.setGravity(Gravity.CENTER_VERTICAL); return x; }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text == null ? "" : text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.03f);
        return t;
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable strokeBg(int color, int radius, int stroke, int strokeColor) {
        GradientDrawable g = bg(color, radius);
        g.setStroke(dp(stroke), strokeColor);
        return g;
    }

    private String s(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        String v = o.optString(key, "");
        if (v == null || "null".equalsIgnoreCase(v.trim())) return "";
        return v.trim();
    }

    private void shell(String selected) {
        LinearLayout root = vertical();
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        content = vertical();
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = horizontal();
        bottom.setBackgroundColor(Color.WHITE);
        pad(bottom, 8, 3, 8, 3);
        addNav("▣\nProgram", "Program".equals(selected), () -> showProgram(recitalSelected, null));
        addNav("▤\nNoutăți", "Noutăți".equals(selected), this::showNews);
        addNav("⌕\nSearch", "Search".equals(selected), this::showSearch);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(62)));

        setContentView(root);
        root.requestApplyInsets();
    }

    private void addNav(String label, boolean selected, Runnable action) {
        TextView b = tv(label, 11.5f, selected ? NAVY_2 : MUTED, selected);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> action.run());
        bottom.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addHeroHeader() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackgroundColor(PAPER);
        hero.addView(new BuildingBackdrop(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        pad(row, 20, 15, 14, 13);

        LinearLayout left = vertical();
        left.addView(tv("S T A G I U N E", 9.5f, GOLD, true));

        TextView title = tv("Filarmonica\nTransilvania", 35, NAVY, true);
        title.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        title.setLineSpacing(-dp(3), .94f);
        pad(title, 0, 4, 0, 6);
        left.addView(title);
        left.addView(tv("M U Z I C Ă   ·   O A M E N I   ·   C O M U N I T A T E", 7.5f, GOLD, true));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout right = vertical();
        right.setGravity(Gravity.END);
        TextView updated = tv("Actualizat " + syncTime, 11, NAVY, false);
        updated.setGravity(Gravity.END);
        right.addView(updated, new LinearLayout.LayoutParams(-1, -2));

        TextView refresh = tv("↻  Actualizează", 11.5f, GOLD, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(strokeBg(Color.TRANSPARENT, 24, 1, GOLD));
        pad(refresh, 11, 8, 11, 8);
        refresh.setOnClickListener(v -> manualRefresh(refresh));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(124), dp(42));
        rp.setMargins(0, dp(7), 0, 0);
        right.addView(refresh, rp);
        row.addView(right, new LinearLayout.LayoutParams(dp(136), -2));

        hero.addView(row, new FrameLayout.LayoutParams(-1, -1));
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(205)));
    }

    private class BuildingBackdrop extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        BuildingBackdrop(Activity c) {
            super(c);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(dp(1));
            p.setColor(Color.rgb(223, 211, 191));
            p.setAlpha(130);
        }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w = getWidth(), h = getHeight();
            float x0 = w * .42f, y0 = h * .27f;
            Path roof = new Path();
            roof.moveTo(x0, y0 + h*.18f);
            roof.lineTo(w*.62f, y0);
            roof.lineTo(w*.88f, y0+h*.20f);
            roof.lineTo(w*.96f, y0+h*.22f);
            c.drawPath(roof, p);
            c.drawRect(x0, y0+h*.18f, w*.94f, h*.89f, p);
            c.drawLine(x0, h*.55f, w*.94f, h*.55f, p);
            c.drawLine(x0, h*.72f, w*.94f, h*.72f, p);
            for (int i=0;i<8;i++) {
                float x=x0+dp(18)+i*((w*.94f-x0-dp(36))/7f);
                c.drawRect(x-dp(5), h*.59f, x+dp(5), h*.70f, p);
                c.drawRect(x-dp(5), h*.75f, x+dp(5), h*.87f, p);
            }
            float towerL=w*.68f, towerR=w*.76f;
            c.drawRect(towerL, h*.16f, towerR, h*.55f, p);
            Path towerRoof=new Path();
            towerRoof.moveTo(towerL-dp(5), h*.16f);
            towerRoof.lineTo((towerL+towerR)/2, h*.05f);
            towerRoof.lineTo(towerR+dp(5), h*.16f);
            c.drawPath(towerRoof,p);
            c.drawLine((towerL+towerR)/2, h*.05f, (towerL+towerR)/2, h*.01f, p);
            c.drawCircle((towerL+towerR)/2, h*.29f, dp(9), p);
            for (int i=0;i<5;i++) c.drawLine(w*.80f+i*dp(20), h*.31f+i*dp(2), w*.80f+i*dp(20), h*.89f, p);
        }
    }

    private void addSegments() {
        LinearLayout seg = horizontal();
        seg.setBackground(strokeBg(Color.rgb(248,247,244), 18, 1, BORDER));
        TextView orch = segment("♬  Orchestră", !recitalSelected);
        TextView rec = segment("▱  TMC & Recitaluri", recitalSelected);
        orch.setOnClickListener(v -> { recitalSelected=false; monthDropdownOpen=false; openOccurrenceKey=null; showProgram(false, null); });
        rec.setOnClickListener(v -> { recitalSelected=true; monthDropdownOpen=false; openOccurrenceKey=null; showProgram(true, null); });
        seg.addView(orch, new LinearLayout.LayoutParams(0, dp(52), 1));
        seg.addView(rec, new LinearLayout.LayoutParams(0, dp(52), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52));
        p.setMargins(dp(14), dp(2), dp(14), dp(9));
        content.addView(seg, p);
    }

    private TextView segment(String text, boolean active) {
        TextView t = tv(text, 13.5f, active ? Color.WHITE : MUTED, active);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(active ? NAVY_2 : Color.TRANSPARENT, 18));
        return t;
    }

    private void addMonthSelector() {
        JSONObject month = selectedMonth();
        String label = month == null ? "Alege luna" : monthDisplayLabel(month);
        TextView selector = tv(label + (monthDropdownOpen ? "  ⌃" : "  ⌄"), 18, NAVY, true);
        selector.setGravity(Gravity.CENTER);
        selector.setBackground(strokeBg(Color.WHITE, 16, 1, NAVY_2));
        selector.setOnClickListener(v -> { monthDropdownOpen = !monthDropdownOpen; showProgram(recitalSelected, null); });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(58));
        p.setMargins(dp(14), 0, dp(14), dp(10));
        content.addView(selector, p);

        if (monthDropdownOpen && program != null) {
            JSONArray months = program.optJSONArray("months");
            if (months != null) {
                LinearLayout list = vertical();
                list.setBackground(strokeBg(Color.WHITE, 16, 1, BORDER));
                for (int i=0;i<months.length();i++) {
                    final int index=i;
                    JSONObject m=months.optJSONObject(i);
                    TextView row=tv(monthDisplayLabel(m), 15, i==selectedMonthIndex?NAVY:GOLD, i==selectedMonthIndex);
                    pad(row, 18, 12, 18, 12);
                    row.setOnClickListener(v -> {
                        selectedMonthIndex=index;
                        monthDropdownOpen=false;
                        openOccurrenceKey=null;
                        targetEventId=null;
                        showProgram(recitalSelected, null);
                    });
                    list.addView(row);
                    if (i<months.length()-1) list.addView(rule());
                }
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(dp(14), -dp(5), dp(14), dp(10));
                content.addView(list, lp);
            }
        }
    }

    private String monthDisplayLabel(JSONObject m) {
        if (m == null) return "";
        String name=s(m,"name");
        if (name.isEmpty()) name=s(m,"label");
        int y=m.optInt("year",0);
        if (!name.isEmpty()) name=name.substring(0,1).toUpperCase(Locale.ROOT)+name.substring(1);
        if (name.matches(".*\\d{4}.*")) return name;
        return y>0 ? name+" "+y : name;
    }

    public void showProgram(boolean recital, String targetId) {
        recitalSelected=recital;
        targetEventId=targetId;
        targetView=null;
        shell("Program");
        addHeroHeader();
        addSegments();
        addMonthSelector();

        if (program == null) {
            showMessage("Se încarcă programul…", "Citesc ultima versiune disponibilă.");
            return;
        }
        JSONObject month=selectedMonth();
        if (month==null) {
            showMessage("Program indisponibil", "Nu există program pentru luna selectată.");
            return;
        }
        if (recitalSelected) addRecitalList(month); else addOrchestraList(month);
        Space sp=new Space(this);
        content.addView(sp,new LinearLayout.LayoutParams(1,dp(14)));

        if (targetView!=null) {
            View v=targetView;
            scroll.postDelayed(() -> scroll.smoothScrollTo(0,Math.max(0,v.getTop()-dp(12))),140);
            targetEventId=null;
        }
    }

    private JSONObject selectedMonth() {
        if (program==null) return null;
        JSONArray months=program.optJSONArray("months");
        if (months==null || months.length()==0) return null;
        if (selectedMonthIndex<0 || selectedMonthIndex>=months.length()) selectedMonthIndex=0;
        return months.optJSONObject(selectedMonthIndex);
    }

    private void addOrchestraList(JSONObject month) {
        List<Occurrence> list=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        JSONArray weeks=month.optJSONArray("weeks");
        if (weeks!=null) {
            for (int wi=0;wi<weeks.length();wi++) {
                JSONObject w=weeks.optJSONObject(wi);
                if (w==null) continue;
                JSONArray events=w.optJSONArray("events");
                if (events==null) continue;
                for (int ei=0;ei<events.length();ei++) {
                    JSONObject e=events.optJSONObject(ei);
                    if (e==null) continue;
                    String id=s(e,"id");
                    if (!id.isEmpty() && !seen.add(id)) continue;
                    list.addAll(eventOccurrences(e,true));
                }
            }
        }
        Collections.sort(list,(a,b)->a.date.compareTo(b.date));
        if (list.isEmpty()) {
            showMessage("Niciun concert", "Nu există concerte orchestrale în luna selectată.");
            return;
        }

        if (targetEventId!=null) {
            for (Occurrence o:list) {
                if (targetEventId.equals(s(o.event,"id"))) {
                    openOccurrenceKey=o.key;
                    break;
                }
            }
        }
        if (openOccurrenceKey==null && !list.isEmpty()) openOccurrenceKey=list.get(0).key;

        for (Occurrence o:list) addConcertCard(o, o.key.equals(openOccurrenceKey));
    }

    private List<Occurrence> eventOccurrences(JSONObject e, boolean orchestra) {
        List<Occurrence> out=new ArrayList<>();
        Set<String> used=new HashSet<>();
        JSONArray concerts=e.optJSONArray("concerts");
        if (concerts!=null) {
            for (int i=0;i<concerts.length();i++) {
                JSONObject c=concerts.optJSONObject(i);
                if (c==null) continue;
                String d=s(c,"date"), time=s(c,"time");
                if (!d.isEmpty()) addOccurrence(out,used,e,d,time,"","");
                else {
                    JSONArray cand=c.optJSONArray("candidate_dates");
                    if (cand!=null && cand.length()>0) {
                        String a=cand.optString(0,"");
                        String alt=cand.length()>1?cand.optString(1,""):"";
                        addOccurrence(out,used,e,a,time,"?",alt);
                    }
                }
            }
        }
        if (out.isEmpty()) {
            JSONObject d=e.optJSONObject("date");
            JSONArray dates=d==null?null:d.optJSONArray("dates");
            if (dates!=null) {
                for (int i=0;i<dates.length();i++) addOccurrence(out,used,e,dates.optString(i,""),orchestra?"19:00":"","","");
            }
            JSONArray cand=d==null?null:d.optJSONArray("candidate_dates");
            if (out.isEmpty() && cand!=null && cand.length()>0) {
                String a=cand.optString(0,"");
                String alt=cand.length()>1?cand.optString(1,""):"";
                addOccurrence(out,used,e,a,orchestra?"19:00":"","?",alt);
            }
        }
        return out;
    }

    private void addOccurrence(List<Occurrence> out, Set<String> used, JSONObject e, String iso, String time, String dayOverride, String alt) {
        if (iso==null || iso.isEmpty()) return;
        try {
            LocalDate d=LocalDate.parse(iso);
            String k=s(e,"id")+"@"+iso;
            if (!used.add(k)) return;
            Occurrence o=new Occurrence(e,d,time,k);
            o.dayOverride=dayOverride;
            o.altDate=alt;
            out.add(o);
        } catch (Exception ignored) {}
    }

    private void addConcertCard(Occurrence o, boolean open) {
        JSONObject e=o.event;
        LinearLayout card=vertical();
        card.setBackground(strokeBg(Color.WHITE,18,1,BORDER));

        LinearLayout top=horizontal();
        top.setGravity(Gravity.TOP);
        top.setMinimumHeight(dp(126));

        boolean undefined=isProgramUndefined(e);
        LinearLayout date=vertical();
        date.setGravity(Gravity.CENTER);
        date.setBackground(bg(undefined?GRAY:NAVY_2,16));
        TextView mon=tv(monthAbbr(o.date),11.5f,Color.WHITE,false);
        mon.setGravity(Gravity.CENTER);
        String day=(o.dayOverride!=null&&!o.dayOverride.isEmpty()
                ?String.format(Locale.ROOT,"%02d%s",o.date.getDayOfMonth(),o.dayOverride)
                :String.format(Locale.ROOT,"%02d",o.date.getDayOfMonth()));
        TextView dd=tv(day,31,Color.WHITE,false);
        dd.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
        dd.setGravity(Gravity.CENTER);
        TextView wd=tv(weekdayAbbr(o.date),11.5f,Color.WHITE,false);
        wd.setGravity(Gravity.CENTER);
        date.addView(mon);
        date.addView(dd);
        date.addView(wd);
        if (o.altDate!=null&&!o.altDate.isEmpty()) {
            try {
                LocalDate alt=LocalDate.parse(o.altDate);
                TextView al=tv("sau "+alt.getDayOfMonth(),8.5f,Color.WHITE,false);
                al.setGravity(Gravity.CENTER);
                date.addView(al);
            } catch(Exception ignored){}
        }
        top.addView(date,new LinearLayout.LayoutParams(dp(94),-1));

        LinearLayout info=vertical();
        pad(info,16,12,8,12);
        if (undefined) {
            TextView u=tv("Program nedefinitivat",17,GRAY,false);
            u.setTypeface(Typeface.create(Typeface.SERIF,Typeface.NORMAL));
            info.addView(u);
        } else {
            JSONObject conductor=e.optJSONObject("conductor");
            String cn=s(conductor,"name");
            if (!cn.isEmpty()) {
                if (conductor!=null && conductor.optBoolean("choir",false)) cn += " + COR";
                TextView c=tv(cn,17,NAVY,true);
                c.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
                info.addView(c);
            }
            JSONArray solo=e.optJSONArray("soloists");
            if (solo!=null) {
                for (int i=0;i<solo.length();i++) {
                    JSONObject so=solo.optJSONObject(i);
                    String line=soloistLine(so);
                    if (!line.isEmpty()) info.addView(tv(line,12.5f,NAVY,false));
                }
            }
            JSONArray works=e.optJSONArray("works");
            if (works!=null) {
                for (int i=0;i<works.length();i++) {
                    JSONObject w=works.optJSONObject(i);
                    String line=workLine(w);
                    if (!line.isEmpty()) info.addView(tv(line,12.5f,NAVY,false));
                }
            }
        }
        top.addView(info,new LinearLayout.LayoutParams(0,-2,1));

        LinearLayout meta=vertical();
        meta.setGravity(Gravity.RIGHT);
        pad(meta,0,12,10,8);
        String time=o.time;
        if (time.isEmpty() && "orchestra".equals(s(e,"type"))) time="19:00";
        TextView tm=tv(time,12.5f,NAVY,false);
        tm.setGravity(Gravity.END);
        meta.addView(tm);
        String venue=s(e,"venue");
        if (shouldShowVenue(venue)) {
            TextView vv=tv(venue,10.5f,NAVY_2,false);
            vv.setGravity(Gravity.END);
            pad(vv,0,3,0,0);
            meta.addView(vv);
        }
        TextView arrow=tv(open?"⌃":"⌄",22,NAVY,true);
        arrow.setGravity(Gravity.END);
        pad(arrow,0,9,0,0);
        meta.addView(arrow);
        top.addView(meta,new LinearLayout.LayoutParams(dp(94),-2));
        card.addView(top);

        LinearLayout details=vertical();
        details.setVisibility(open?View.VISIBLE:View.GONE);
        details.setBackgroundColor(SOFT);
        pad(details,8,8,8,10);
        if (!undefined) renderWorksSection(details,e);
        renderScheduleSection(details,e);
        card.addView(details);

        View.OnClickListener toggle=v->{
            boolean now=details.getVisibility()==View.VISIBLE;
            details.setVisibility(now?View.GONE:View.VISIBLE);
            arrow.setText(now?"⌄":"⌃");
            openOccurrenceKey=now?null:o.key;
        };
        top.setOnClickListener(toggle);
        arrow.setOnClickListener(toggle);

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(dp(14),0,dp(14),dp(10));
        content.addView(card,cp);
        if (targetEventId!=null && targetEventId.equals(s(e,"id"))) targetView=card;
    }

    private boolean shouldShowVenue(String venue) {
        if (venue==null || venue.trim().isEmpty()) return false;
        String n=norm(venue);
        return !n.contains("sala mare") && !n.equals("filarmonica transilvania");
    }

    private boolean isProgramUndefined(JSONObject e) {
        String st=s(e,"program_status");
        JSONArray works=e.optJSONArray("works");
        return "uncompleted".equals(st) || ((works==null || works.length()==0) && ("partial".equals(st) || s(e.optJSONObject("raw"),"program").isEmpty()));
    }

    private String soloistLine(JSONObject so) {
        if (so==null) return "";
        String line=s(so,"name"), inst=s(so,"instrument"), role=s(so,"role");
        if (line.isEmpty()) line=s(so,"raw");
        if (!inst.isEmpty() && !norm(line).contains(norm(inst))) line += (line.isEmpty()?"":" — ")+inst;
        if (!role.isEmpty() && !line.contains(role)) line += " "+role;
        return line;
    }

    private String workLine(JSONObject w) {
        if (w==null) return "";
        String c=s(w,"composer"), t=s(w,"title");
        if (c.isEmpty()) return t;
        if (t.isEmpty()) return c;
        return c+" — "+t;
    }

    private void renderWorksSection(LinearLayout parent, JSONObject e) {
        JSONArray works=e.optJSONArray("works");
        if (works==null || works.length()==0) return;
        LinearLayout box=vertical();
        box.setBackground(strokeBg(Color.WHITE,14,1,Color.rgb(232,228,220)));
        pad(box,12,10,12,9);
        TextView head=tv("♫  Programul lucrărilor",15,GOLD,true);
        head.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
        box.addView(head);
        for (int i=0;i<works.length();i++) {
            JSONObject w=works.optJSONObject(i);
            if (w==null) continue;
            LinearLayout row=horizontal();
            row.setGravity(Gravity.TOP);
            pad(row,0,9,0,8);
            TextView comp=tv(s(w,"composer"),12.5f,NAVY,true);
            row.addView(comp,new LinearLayout.LayoutParams(dp(108),-2));
            LinearLayout right=vertical();
            TextView title=tv(s(w,"title"),12.5f,NAVY,true);
            title.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD_ITALIC));
            right.addView(title);
            JSONArray orch=w.optJSONArray("orchestrations");
            if (orch!=null) {
                for (int j=0;j<orch.length();j++) {
                    JSONObject x=orch.optJSONObject(j);
                    if (x==null) continue;
                    String label=s(x,"label"), disp=s(x,"display");
                    if (!label.isEmpty()) right.addView(tv(label,10.5f,MUTED,true));
                    if (!disp.isEmpty()) right.addView(tv(disp,10.7f,MUTED,false));
                }
            }
            JSONArray det=w.optJSONArray("details");
            if (det!=null) {
                for (int j=0;j<det.length();j++) {
                    String x=det.optString(j,"");
                    if (!x.isEmpty() && !"null".equalsIgnoreCase(x)) right.addView(tv(x,10.5f,MUTED,false));
                }
            }
            JSONObject sd=w.optJSONObject("string_distribution");
            String dist=cleanDistribution(s(sd,"display"));
            if (!dist.isEmpty()) right.addView(tv(dist,10.5f,MUTED,false));
            row.addView(right,new LinearLayout.LayoutParams(0,-2,1));
            box.addView(row);
            if (i<works.length()-1) box.addView(rule());
        }
        JSONArray global=e.optJSONArray("string_distributions");
        if (global!=null) {
            for (int i=0;i<global.length();i++) {
                JSONObject x=global.optJSONObject(i);
                if (x==null || !s(x,"applies_to").isEmpty()) continue;
                String d=cleanDistribution(s(x,"display"));
                if (!d.isEmpty()) {
                    TextView z=tv(d,10.5f,MUTED,false);
                    pad(z,108,4,0,3);
                    box.addView(z);
                }
            }
        }
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2));
    }

    private String cleanDistribution(String x) {
        if (x==null) return "";
        return x.replace("Cordari","").replace("cordari","").replaceAll("^\\s*[:\-–]\\s*","").trim();
    }

    private void renderScheduleSection(LinearLayout parent, JSONObject e) {
        JSONArray reps=e.optJSONArray("rehearsals");
        if (reps==null || reps.length()==0) return;
        LinearLayout box=vertical();
        box.setBackground(strokeBg(Color.WHITE,14,1,Color.rgb(232,228,220)));
        pad(box,12,10,12,8);
        TextView head=tv("◷  Programul zilei",15,GOLD,true);
        head.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
        box.addView(head);
        for (int i=0;i<reps.length();i++) {
            JSONObject r=reps.optJSONObject(i);
            if (r==null) continue;
            String day=s(r,"day"), ann=s(r,"annotation");
            if (!ann.isEmpty()) day += (day.isEmpty()?"":" ")+ann;
            JSONArray lines=r.optJSONArray("lines");
            List<String> vals=new ArrayList<>();
            if (lines!=null) {
                for (int j=0;j<lines.length();j++) {
                    JSONObject l=lines.optJSONObject(j);
                    String d=stripConcertWord(s(l,"display"));
                    if (!d.isEmpty()) vals.add(d);
                }
            }
            String text=join(vals,"   |   ");
            if (day.isEmpty() && text.isEmpty()) continue;
            LinearLayout row=horizontal();
            row.setGravity(Gravity.TOP);
            pad(row,0,6,0,6);
            row.addView(tv(day,11.5f,NAVY,true),new LinearLayout.LayoutParams(dp(94),-2));
            row.addView(tv(text,10.8f,MUTED,false),new LinearLayout.LayoutParams(0,-2,1));
            box.addView(row);
            if (i<reps.length()-1) box.addView(rule());
        }
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(8),0,0);
        parent.addView(box,p);
    }

    private String stripConcertWord(String x) {
        if (x==null) return "";
        return x.replaceAll("(?iu)\\bconcert(?:ul)?\\b\\s*","").replaceAll("\\s{2,}"," ").trim();
    }

    private String join(List<String> xs, String sep) {
        StringBuilder b=new StringBuilder();
        for (String x:xs) {
            if (x==null||x.isEmpty()) continue;
            if (b.length()>0) b.append(sep);
            b.append(x);
        }
        return b.toString();
    }

    private void addRecitalList(JSONObject month) {
        JSONArray arr=month.optJSONArray("recitals");
        if (arr==null) arr=month.optJSONArray("tmc_recitals");
        if (arr==null || arr.length()==0) {
            showMessage("Niciun recital", "Nu există recitaluri în luna selectată.");
            return;
        }
        List<Occurrence> list=new ArrayList<>();
        for (int i=0;i<arr.length();i++) {
            JSONObject e=arr.optJSONObject(i);
            if (e!=null) list.addAll(eventOccurrences(e,false));
        }
        Collections.sort(list,(a,b)->a.date.compareTo(b.date));
        for (Occurrence o:list) addRecitalCard(o);
    }

    private void addRecitalCard(Occurrence o) {
        JSONObject e=o.event;
        LinearLayout card=horizontal();
        card.setGravity(Gravity.TOP);
        card.setBackground(strokeBg(Color.WHITE,18,1,BORDER));
        card.setMinimumHeight(dp(102));
        LinearLayout date=vertical();
        date.setGravity(Gravity.CENTER);
        date.setBackground(bg(NAVY_2,16));
        TextView mon=tv(monthAbbr(o.date),10.5f,Color.WHITE,false);
        mon.setGravity(Gravity.CENTER);
        TextView dd=tv(String.format(Locale.ROOT,"%02d",o.date.getDayOfMonth()),27,Color.WHITE,false);
        dd.setGravity(Gravity.CENTER);
        TextView wd=tv(weekdayAbbr(o.date),10.5f,Color.WHITE,false);
        wd.setGravity(Gravity.CENTER);
        date.addView(mon);
        date.addView(dd);
        date.addView(wd);
        card.addView(date,new LinearLayout.LayoutParams(dp(82),-1));

        LinearLayout info=vertical();
        pad(info,14,11,8,11);
        JSONObject cond=e.optJSONObject("conductor");
        String cn=s(cond,"name");
        if (!cn.isEmpty()) info.addView(tv(cn,15,NAVY,true));
        JSONArray sols=e.optJSONArray("soloists");
        if (sols!=null) {
            for(int i=0;i<sols.length();i++) {
                String x=soloistLine(sols.optJSONObject(i));
                if(!x.isEmpty()) info.addView(tv(x,12,NAVY,false));
            }
        }
        JSONArray works=e.optJSONArray("works");
        if (works!=null) {
            for(int i=0;i<works.length();i++) {
                String x=workLine(works.optJSONObject(i));
                if(!x.isEmpty()) info.addView(tv(x,12,NAVY,false));
            }
        }
        if (cn.isEmpty() && (works==null || works.length()==0)) {
            String raw=s(e.optJSONObject("raw"),"program");
            if (!raw.isEmpty()) {
                String[] lines=raw.split("\\n");
                for (int i=0;i<Math.min(lines.length,4);i++) {
                    if (!lines[i].trim().isEmpty()) info.addView(tv(lines[i].trim(),12,NAVY,i==0));
                }
            }
        }
        card.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout meta=vertical();
        pad(meta,0,12,10,8);
        TextView time=tv(o.time,12,NAVY,false);
        time.setGravity(Gravity.END);
        meta.addView(time);
        String venue=s(e,"venue");
        if (!venue.isEmpty()) {
            TextView v=tv(venue,10,MUTED,false);
            v.setGravity(Gravity.END);
            meta.addView(v);
        }
        card.addView(meta,new LinearLayout.LayoutParams(dp(100),-2));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(14),0,dp(14),dp(10));
        content.addView(card,p);
    }

    private void showMessage(String title, String text) {
        LinearLayout c=vertical();
        c.setBackground(strokeBg(Color.WHITE,16,1,BORDER));
        pad(c,20,22,20,22);
        TextView a=tv(title,16,NAVY,true);
        a.setGravity(Gravity.CENTER);
        c.addView(a);
        TextView b=tv(text,12,MUTED,false);
        b.setGravity(Gravity.CENTER);
        pad(b,0,8,0,0);
        c.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(14),0,dp(14),dp(10));
        content.addView(c,p);
    }

    private View rule() {
        View v=new View(this);
        v.setBackgroundColor(Color.rgb(233,230,224));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        v.setLayoutParams(p);
        return v;
    }

    private String monthAbbr(LocalDate d) {
        String[] m={"","IAN","FEB","MAR","APR","MAI","IUN","IUL","AUG","SEP","OCT","NOI","DEC"};
        return m[d.getMonthValue()];
    }

    private String weekdayAbbr(LocalDate d) {
        String[] x={"LUN","MAR","MIE","JOI","VIN","SÂM","DUM"};
        return x[d.getDayOfWeek().getValue()-1];
    }

    public void showNews() {
        shell("Noutăți");
        addSimplePageTitle("Noutăți");
        String cached=getSharedPreferences(PREFS,MODE_PRIVATE).getString(CACHE_NEWS,null);
        if (cached!=null) {
            try { renderNews(new JSONObject(cached)); } catch(Exception ignored){}
        }
        new Thread(()->{
            try {
                JSONObject j=getJson("/api/news?limit=200");
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_NEWS,j.toString()).apply();
                runOnUiThread(()->{
                    shell("Noutăți");
                    addSimplePageTitle("Noutăți");
                    renderNews(j);
                });
            } catch(Exception ignored){}
        }).start();
    }

    private void addSimplePageTitle(String title) {
        LinearLayout h=vertical();
        pad(h,18,22,18,14);
        h.addView(tv("Filarmonica Transilvania",11,GOLD,true));
        TextView t=tv(title,29,NAVY,true);
        t.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
        h.addView(t);
        content.addView(h);
    }

    private void renderNews(JSONObject j) {
        JSONArray items=j.optJSONArray("items");
        if (items==null || items.length()==0) {
            showMessage("Nicio modificare detectată", "Modificările viitoare vor apărea aici.");
            return;
        }
        for(int i=0;i<items.length();i++) {
            JSONObject n=items.optJSONObject(i);
            if(n==null) continue;
            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,15,1,BORDER));
            pad(c,14,11,14,11);
            c.addView(tv("Program modificat",14,NAVY,true));
            String wl=s(n,"week_label");
            if(!wl.isEmpty()) c.addView(tv(wl,11,MUTED,true));
            JSONArray changes=n.optJSONArray("changes");
            if(changes!=null) {
                for(int k=0;k<changes.length();k++) {
                    JSONObject ch=changes.optJSONObject(k);
                    String l=s(ch,"label");
                    if(!l.isEmpty()) c.addView(tv("• "+l,11.5f,MUTED,false));
                }
            }
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            content.addView(c,p);
        }
    }

    public void showSearch() {
        shell("Search");
        addSimplePageTitle("Search");
        EditText q=new EditText(this);
        q.setHint("Dirijor, solist, compozitor, lucrare…");
        q.setSingleLine(true);
        q.setTextSize(14);
        q.setBackground(strokeBg(Color.WHITE,14,1,BORDER));
        pad(q,14,10,14,10);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(52));
        qp.setMargins(dp(14),0,dp(14),dp(10));
        content.addView(q,qp);
        LinearLayout results=vertical();
        content.addView(results);
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x,int a,int b,int c){}
            public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence x,int a,int b,int c){
                if(searchRunnable!=null) handler.removeCallbacks(searchRunnable);
                String query=x.toString().trim();
                searchRunnable=()->localSearch(query,results);
                handler.postDelayed(searchRunnable,180);
            }
        });
    }

    private void localSearch(String query, LinearLayout results) {
        results.removeAllViews();
        if(query.length()<2) {
            TextView x=tv("Scrie cel puțin 2 caractere.",12,MUTED,false);
            x.setGravity(Gravity.CENTER);
            results.addView(x);
            return;
        }
        if(program==null) return;
        String needle=norm(query);
        List<JSONObject> found=new ArrayList<>();
        JSONArray months=program.optJSONArray("months");
        if(months!=null) {
            for(int mi=0;mi<months.length();mi++) {
                JSONObject m=months.optJSONObject(mi);
                if(m==null) continue;
                JSONArray weeks=m.optJSONArray("weeks");
                if(weeks!=null) {
                    for(int wi=0;wi<weeks.length();wi++) {
                        JSONObject w=weeks.optJSONObject(wi);
                        JSONArray ev=w==null?null:w.optJSONArray("events");
                        if(ev!=null) {
                            for(int ei=0;ei<ev.length();ei++) {
                                JSONObject e=ev.optJSONObject(ei);
                                if(matches(e,needle)) { tagMonth(e,mi); found.add(e); }
                            }
                        }
                    }
                }
                JSONArray rec=m.optJSONArray("recitals");
                if(rec==null) rec=m.optJSONArray("tmc_recitals");
                if(rec!=null) {
                    for(int ei=0;ei<rec.length();ei++) {
                        JSONObject e=rec.optJSONObject(ei);
                        if(matches(e,needle)) { tagMonth(e,mi); found.add(e); }
                    }
                }
            }
        }
        if(found.isEmpty()) {
            TextView x=tv("Niciun rezultat.",12,MUTED,false);
            x.setGravity(Gravity.CENTER);
            results.addView(x);
            return;
        }
        for(JSONObject e:found) {
            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,14,1,BORDER));
            pad(c,14,10,14,10);
            JSONObject cond=e.optJSONObject("conductor");
            String cn=s(cond,"name");
            if(!cn.isEmpty()) c.addView(tv(cn,14,NAVY,true));
            JSONArray works=e.optJSONArray("works");
            if(works!=null&&works.length()>0) c.addView(tv(workLine(works.optJSONObject(0)),11.5f,MUTED,false));
            c.setOnClickListener(v->openSearchResult(e));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            results.addView(c,p);
        }
    }

    private boolean matches(JSONObject e,String needle) {
        String hay=s(e,"search_text");
        if(hay.isEmpty()) hay=e.toString();
        return norm(hay).contains(needle);
    }

    private void tagMonth(JSONObject e,int i) {
        try { e.put("_ui_month_index",i); } catch(Exception ignored){}
    }

    private void openSearchResult(JSONObject e) {
        selectedMonthIndex=e.optInt("_ui_month_index",selectedMonthIndex);
        monthDropdownOpen=false;
        openOccurrenceKey=null;
        showProgram("recital".equals(s(e,"type")),s(e,"id"));
    }

    private void manualRefresh(TextView button) {
        button.setEnabled(false);
        button.setAlpha(.6f);
        Toast.makeText(this,"Verific Google Docs…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try {
                JSONObject result=getJson("/api/refresh");
                JSONObject p=getJson("/api/program");
                JSONObject st=getJson("/api/status");
                program=p;
                cacheProgram(p);
                updateSyncFromStatus(st);
                int changes=result.optInt("change_count",0);
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setAlpha(1f);
                    showProgram(recitalSelected,null);
                    Toast.makeText(this,changes==0?"Programul este la zi.":"Actualizat: "+changes+" modificări.",Toast.LENGTH_LONG).show();
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    button.setEnabled(true);
                    button.setAlpha(1f);
                    Toast.makeText(this,"Actualizarea a eșuat: "+shortError(e),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadProgramAndStatus() {
        new Thread(()->{
            try {
                JSONObject p=getJson("/api/program");
                JSONObject st=getJson("/api/status");
                program=p;
                cacheProgram(p);
                updateSyncFromStatus(st);
                runOnUiThread(()->{
                    chooseInitialMonthIfNeeded();
                    showProgram(recitalSelected,targetEventId);
                });
            } catch(Exception e) {
                runOnUiThread(()->{
                    if(program==null) {
                        shell("Program");
                        addHeroHeader();
                        showMessage("Conexiune indisponibilă","Nu pot ajunge la NAS și nu există încă o copie salvată pe telefon.");
                    } else {
                        Toast.makeText(this,"Folosesc copia salvată pe telefon.",Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private JSONObject getJson(String path) throws Exception {
        URL url=new URL(API_BASE+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(6000);
        c.setReadTimeout(22000);
        c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode();
        BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=r.readLine())!=null) sb.append(line);
        r.close();
        c.disconnect();
        if(code<200||code>=300) throw new Exception("HTTP "+code);
        return new JSONObject(sb.toString());
    }

    private void updateSyncFromStatus(JSONObject st) {
        String iso=s(st,"last_scan");
        if(iso.isEmpty()) return;
        try {
            OffsetDateTime odt=OffsetDateTime.parse(iso);
            syncTime=odt.atZoneSameInstant(ZoneId.of("Europe/Bucharest")).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch(Exception e) {
            try { syncTime=iso.substring(11,16); } catch(Exception ignored) { syncTime="—"; }
        }
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_SYNC,syncTime).apply();
    }

    private void cacheProgram(JSONObject p) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_PROGRAM,p.toString()).apply();
    }

    private void chooseInitialMonthIfNeeded() {
        if(selectedMonthIndex==0&&program!=null) chooseInitialMonth();
    }

    private void chooseInitialMonth() {
        if(program==null) return;
        JSONArray months=program.optJSONArray("months");
        if(months==null||months.length()==0) return;
        LocalDate now=LocalDate.now();
        for(int i=0;i<months.length();i++) {
            JSONObject m=months.optJSONObject(i);
            if(m!=null&&m.optInt("month")==now.getMonthValue()&&m.optInt("year")==now.getYear()) {
                selectedMonthIndex=i;
                return;
            }
        }
        selectedMonthIndex=0;
    }

    private String shortError(Exception e) {
        String x=e.getMessage();
        if(x==null||x.trim().isEmpty()) x=e.getClass().getSimpleName();
        return x.length()>90?x.substring(0,90):x;
    }

    private String norm(String x) {
        String n=Normalizer.normalize(x==null?"":x,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return n.toLowerCase(Locale.ROOT);
    }
}
