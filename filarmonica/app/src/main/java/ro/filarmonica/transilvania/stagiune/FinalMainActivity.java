package ro.filarmonica.transilvania.stagiune;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.WindowInsets;
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
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class FinalMainActivity extends Activity {
    private static final String API_BASE = "http://192.168.0.191:8787";
    private static final String PREFS = "stagiune_final_v5";
    private static final String CACHE_PROGRAM = "program_json";
    private static final String CACHE_NEWS = "news_json";
    private static final String CACHE_SYNC = "sync_time";

    private final int NAVY = Color.rgb(17, 42, 78);
    private final int NAVY_2 = Color.rgb(23, 64, 116);
    private final int GOLD = Color.rgb(181, 133, 49);
    private final int GOLD_PALE = Color.rgb(250, 245, 233);
    private final int CREAM = Color.rgb(249, 248, 244);
    private final int WHITE = Color.rgb(255, 255, 255);
    private final int MUTED = Color.rgb(112, 116, 127);
    private final int BORDER = Color.rgb(225, 225, 222);
    private final int PALE_BLUE = Color.rgb(244, 247, 251);

    private LinearLayout content, bottom;
    private ScrollView scrollView;
    private JSONObject program;
    private int selectedMonthIndex = 0;
    private boolean recitalSelected = false;
    private String targetEventId;
    private String syncTime = "—";
    private boolean initialMonthChosen = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(CREAM);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        String cached = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_PROGRAM, null);
        syncTime = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_SYNC, "—");
        if (cached != null) {
            try { program = new JSONObject(cached); chooseInitialMonth(); } catch (Exception ignored) {}
        }
        showProgram(false, null);
        loadProgramAndStatus();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }
    private void pad(View v, int l, int t, int r, int b) { v.setPadding(dp(l), dp(t), dp(r), dp(b)); }
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(clean(text));
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.04f);
        return t;
    }

    private TextView serif(String text, float sp, int color, boolean bold) {
        TextView t = tv(text, sp, color, bold);
        t.setTypeface(Typeface.create(Typeface.SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }

    private String clean(String v) {
        if (v == null) return "";
        v = v.trim();
        if (v.equalsIgnoreCase("null")) return "";
        v = v.replace("Contrabas", "C-bas").replace("contrabas", "C-bas");
        v = v.replaceAll("\\bCb\\b", "C-bas");
        return v;
    }

    private String s(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        return clean(o.optString(key, ""));
    }

    private GradientDrawable bg(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private GradientDrawable strokeBg(int color, int radius, int sw, int strokeColor) {
        GradientDrawable g = bg(color, radius);
        g.setStroke(dp(sw), strokeColor);
        return g;
    }

    private void shell(String selected) {
        LinearLayout root = vertical();
        root.setBackgroundColor(CREAM);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        content = vertical();
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = horizontal();
        bottom.setBackgroundColor(Color.WHITE);
        pad(bottom, 8, 4, 8, 4);
        addNav("▣\nProgram", "Program".equals(selected), () -> showProgram(recitalSelected, null));
        addNav("▤\nNoutăți", "Noutăți".equals(selected), this::showNews);
        addNav("⌕\nCaută", "Caută".equals(selected), this::showSearch);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(62)));
        setContentView(root);
        root.requestApplyInsets();
    }

    private void addNav(String label, boolean selected, Runnable action) {
        TextView b = tv(label, 12, selected ? NAVY_2 : MUTED, selected);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> action.run());
        bottom.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    public void showProgram(boolean recital, String targetId) {
        recitalSelected = recital;
        targetEventId = targetId;
        shell("Program");
        addHeroHeader();
        addSegments();

        if (program == null) {
            messageCard("Se încarcă programul…", "Citesc ultima versiune disponibilă.");
            return;
        }
        JSONObject month = selectedMonth();
        if (month == null) {
            messageCard("Program indisponibil", "Nu există date pentru luna selectată.");
            return;
        }
        if (recitalSelected) addRecitals(month); else addOrchestraWeeks(month);
        Space s = new Space(this); content.addView(s, new LinearLayout.LayoutParams(1, dp(18)));
    }

    private void addHeroHeader() {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackgroundColor(CREAM);
        hero.setClipChildren(false);
        hero.addView(new HallSketchView(this), new FrameLayout.LayoutParams(-1, dp(210)));

        LinearLayout title = vertical();
        TextView stag = tv("S  T  A  G  I  U  N  E", 11.5f, GOLD, true);
        title.addView(stag);
        TextView name = serif("Filarmonica\nTransilvania", 34, NAVY, true);
        name.setLineSpacing(-dp(4), .94f);
        title.addView(name);

        TextView sync = tv("Actualizat " + syncTime + "   ↻", 12.5f, MUTED, false);
        pad(sync, 0, 7, 0, 0);
        sync.setOnClickListener(v -> manualRefresh(sync));
        title.addView(sync);

        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(300), -2);
        tp.leftMargin = dp(18); tp.topMargin = dp(17);
        hero.addView(title, tp);

        JSONObject m = selectedMonth();
        String monthLabel = m == null ? "ALEGE LUNA" : s(m, "label");
        TextView month = tv(monthLabel + " ⌄", 15, Color.WHITE, true);
        month.setGravity(Gravity.CENTER);
        month.setBackground(bg(GOLD, 28));
        month.setOnClickListener(v -> showMonthPicker());
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(dp(210), dp(50));
        mp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
        mp.rightMargin = dp(18); mp.bottomMargin = dp(8);
        hero.addView(month, mp);

        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(210)));
    }

    private void addSegments() {
        LinearLayout wrap = horizontal();
        wrap.setBackground(strokeBg(Color.WHITE, 28, 1, BORDER));
        TextView a = tv("Orchestră", 14.5f, recitalSelected ? MUTED : Color.WHITE, !recitalSelected);
        TextView b = tv("Recitaluri", 14.5f, recitalSelected ? Color.WHITE : MUTED, recitalSelected);
        a.setGravity(Gravity.CENTER); b.setGravity(Gravity.CENTER);
        a.setBackground(bg(recitalSelected ? Color.TRANSPARENT : NAVY_2, 28));
        b.setBackground(bg(recitalSelected ? NAVY_2 : Color.TRANSPARENT, 28));
        a.setOnClickListener(v -> showProgram(false, null));
        b.setOnClickListener(v -> showProgram(true, null));
        wrap.addView(a, new LinearLayout.LayoutParams(0, dp(50), 1));
        wrap.addView(b, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(50));
        p.setMargins(dp(14), 0, dp(14), dp(14));
        content.addView(wrap, p);
    }

    private void showMonthPicker() {
        JSONArray months = program == null ? null : program.optJSONArray("months");
        if (months == null) return;
        String[] labels = new String[months.length()];
        for (int i = 0; i < months.length(); i++) labels[i] = s(months.optJSONObject(i), "label");
        new AlertDialog.Builder(this).setTitle("Alege luna")
                .setSingleChoiceItems(labels, selectedMonthIndex, (d, which) -> {
                    selectedMonthIndex = which; d.dismiss(); showProgram(recitalSelected, null);
                }).setNegativeButton("Închide", null).show();
    }

    private JSONObject selectedMonth() {
        if (program == null) return null;
        JSONArray months = program.optJSONArray("months");
        if (months == null || selectedMonthIndex < 0 || selectedMonthIndex >= months.length()) return null;
        return months.optJSONObject(selectedMonthIndex);
    }

    private void addOrchestraWeeks(JSONObject month) {
        JSONArray weeks = month.optJSONArray("weeks");
        if (weeks == null || weeks.length() == 0) {
            messageCard("Niciun concert", "Nu există concerte orchestrale în luna selectată."); return;
        }
        LocalDate today = LocalDate.now();
        boolean opened = false;
        for (int i = 0; i < weeks.length(); i++) {
            JSONObject week = weeks.optJSONObject(i); if (week == null) continue;
            boolean target = weekContainsEvent(week, targetEventId);
            boolean current = weekContainsDate(week, today);
            boolean open = target || current;
            if (!open && !opened && targetEventId == null && (i == 0 || isCurrentMonthSelected())) open = true;
            if (open) opened = true;
            addWeekCard(week, open);
        }
    }

    private void addWeekCard(JSONObject week, boolean open) {
        LinearLayout card = vertical();
        card.setBackground(strokeBg(Color.WHITE, 15, 1, BORDER));

        LinearLayout head = vertical();
        pad(head, 14, 10, 12, 10);
        LinearLayout top = horizontal();
        TextView label = tv(s(week, "label"), 17, NAVY, true);
        top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        int count = concertCount(week);
        TextView countV = tv(count == 0 ? "" : count + (count == 1 ? " concert" : " concerte"), 10.8f, MUTED, false);
        top.addView(countV);
        TextView arrow = tv(open ? "⌃" : "›", 22, NAVY, true); pad(arrow, 12, 0, 1, 0); top.addView(arrow);
        head.addView(top);

        TextView preview = tv(collapsedPreview(week), 11.3f, MUTED, false);
        pad(preview, 0, 4, 0, 0);
        preview.setVisibility(open ? View.GONE : View.VISIBLE);
        head.addView(preview);
        card.addView(head);

        LinearLayout body = vertical();
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        JSONArray events = week.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i); if (e == null) continue;
                boolean eventOpen = open && (targetEventId == null ? i == 0 : targetEventId.equals(s(e, "id")));
                addEventExpandable(body, e, eventOpen);
                if (i < events.length() - 1) body.addView(thinRule());
            }
        }
        card.addView(body);

        head.setOnClickListener(v -> {
            boolean isOpen = body.getVisibility() == View.VISIBLE;
            body.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            arrow.setText(isOpen ? "›" : "⌃");
            preview.setVisibility(isOpen ? View.VISIBLE : View.GONE);
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(10), 0, dp(10), dp(10));
        content.addView(card, cp);
    }

    private void addEventExpandable(LinearLayout parent, JSONObject e, boolean expanded) {
        LinearLayout wrap = vertical();
        LinearLayout summary = horizontal();
        summary.setGravity(Gravity.TOP);
        summary.setBackgroundColor(Color.WHITE);
        pad(summary, 8, 4, 8, 8);

        TextView badge = dateBadge(e);
        summary.addView(badge, new LinearLayout.LayoutParams(dp(84), dp(112)));

        LinearLayout mid = vertical();
        pad(mid, 13, 4, 4, 0);
        JSONObject conductor = e.optJSONObject("conductor");
        String cn = s(conductor, "name");
        if (cn.isEmpty() && "uncompleted".equals(s(e, "program_status"))) cn = "Program în pregătire";
        TextView cond = serif(cn, 17.5f, cn.startsWith("Program") ? Color.rgb(160,160,160) : NAVY, true);
        mid.addView(cond);

        String works = worksCompact(e);
        if (!works.isEmpty()) {
            TextView w = serif(works, 14.5f, NAVY, false); pad(w, 0, 2, 0, 2); mid.addView(w);
        }

        String meta = eventMeta(e);
        if (!meta.isEmpty()) mid.addView(tv(meta, 11.5f, MUTED, false));

        String rep = rehearsalCompact(e);
        if (!rep.isEmpty()) { TextView r = tv(rep, 10.8f, MUTED, false); pad(r, 0, 3, 0, 0); mid.addView(r); }
        String cord = stringsCompact(e);
        if (!cord.isEmpty()) mid.addView(tv("Cordari: " + cord, 10.8f, MUTED, false));
        summary.addView(mid, new LinearLayout.LayoutParams(0, -2, 1));

        TextView eArrow = tv(expanded ? "⌄" : "›", 22, NAVY, true);
        pad(eArrow, 2, 10, 0, 0); summary.addView(eArrow);
        wrap.addView(summary);

        LinearLayout detail = vertical();
        detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
        detail.setBackgroundColor(Color.rgb(254, 253, 250));
        pad(detail, 12, 2, 12, 12);
        renderEventDetails(detail, e);
        wrap.addView(detail);

        summary.setOnClickListener(v -> {
            boolean isOpen = detail.getVisibility() == View.VISIBLE;
            detail.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            eArrow.setText(isOpen ? "›" : "⌄");
        });
        parent.addView(wrap);
    }

    private TextView dateBadge(JSONObject e) {
        String iso = firstConcertDate(e);
        String day = "--", mon = s(e, "month_name").toUpperCase(Locale.ROOT), dow = "";
        if (mon.length() > 3) mon = mon.substring(0, 3);
        try {
            LocalDate d = LocalDate.parse(iso);
            day = String.format(Locale.ROOT, "%02d", d.getDayOfMonth());
            dow = roWeekdayShort(d);
            mon = roMonthShort(d.getMonthValue());
        } catch (Exception ignored) {}
        TextView b = tv(mon + "\n" + day + "\n" + dow, 12, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setTextSize(12);
        b.setLineSpacing(dp(2), 1.0f);
        b.setBackground(bg(NAVY_2, 14));
        b.setText(mon + "\n" + day + "\n" + dow);
        return b;
    }

    private void renderEventDetails(LinearLayout b, JSONObject e) {
        JSONArray works = e.optJSONArray("works");
        String status = s(e, "program_status");
        if ("uncompleted".equals(status)) {
            TextView x = serif("Program în pregătire", 16, Color.rgb(155,155,155), false); pad(x, 6, 12, 6, 12); b.addView(x);
        } else if (works != null && works.length() > 0) {
            TextView cap = serif("Programul lucrărilor", 15.5f, GOLD, true); pad(cap, 6, 8, 6, 8); b.addView(cap);
            LinearLayout worksBox = vertical(); worksBox.setBackground(strokeBg(WHITE, 12, 1, BORDER)); pad(worksBox, 10, 4, 10, 4);
            for (int i = 0; i < works.length(); i++) {
                JSONObject w = works.optJSONObject(i); if (w == null) continue;
                LinearLayout row = horizontal(); row.setGravity(Gravity.TOP); pad(row, 0, 7, 0, 7);
                TextView no = serif((i + 1) + ".", 13, GOLD, true); row.addView(no, new LinearLayout.LayoutParams(dp(34), -2));
                LinearLayout wd = vertical();
                String comp = s(w, "composer"), title = s(w, "title");
                String heading = comp.isEmpty() ? title : comp + " — " + title;
                wd.addView(tv(heading, 13.2f, NAVY, true));
                JSONArray orch = w.optJSONArray("orchestrations");
                if (orch != null) for (int j = 0; j < orch.length(); j++) {
                    JSONObject o = orch.optJSONObject(j); String label = s(o, "label"), disp = s(o, "display");
                    if (!label.isEmpty()) wd.addView(tv(label, 10.3f, MUTED, true));
                    if (!disp.isEmpty()) wd.addView(tv(disp, 10.8f, MUTED, false));
                }
                JSONObject sd = w.optJSONObject("string_distribution");
                String str = s(sd, "display"); if (!str.isEmpty()) wd.addView(tv("Cordari: " + str, 10.5f, MUTED, false));
                row.addView(wd, new LinearLayout.LayoutParams(0, -2, 1)); worksBox.addView(row);
                if (i < works.length() - 1) worksBox.addView(thinRule());
            }
            b.addView(worksBox);
        }

        renderProgramDay(b, e);
        TextView raw = tv("Vezi textul original  ›", 10.3f, MUTED, true); raw.setGravity(Gravity.END); pad(raw, 0, 9, 2, 0);
        raw.setOnClickListener(v -> showRawDialog(e)); b.addView(raw);
    }

    private void renderProgramDay(LinearLayout b, JSONObject e) {
        JSONArray reps = e.optJSONArray("rehearsals");
        if (reps == null || reps.length() == 0) return;
        TextView cap = serif("Programul zilei", 15, GOLD, true); pad(cap, 6, 12, 6, 8); b.addView(cap);
        LinearLayout box = vertical(); box.setBackground(strokeBg(WHITE, 12, 1, BORDER)); pad(box, 10, 4, 10, 4);
        for (int i = 0; i < reps.length(); i++) {
            JSONObject r = reps.optJSONObject(i); if (r == null) continue;
            String day = s(r, "day"), ann = s(r, "annotation"); if (!ann.isEmpty()) day += (day.isEmpty() ? "" : " ") + ann;
            JSONArray lines = r.optJSONArray("lines");
            if (lines == null || lines.length() == 0) continue;
            for (int j = 0; j < lines.length(); j++) {
                JSONObject ln = lines.optJSONObject(j); String txt = s(ln, "display");
                txt = ensureConcertLabel(day, txt, e);
                if (txt.isEmpty() && day.isEmpty()) continue;
                LinearLayout row = horizontal(); row.setGravity(Gravity.TOP); pad(row, 0, 5, 0, 5);
                row.addView(tv(j == 0 ? day : "", 11.2f, MUTED, false), new LinearLayout.LayoutParams(dp(92), -2));
                row.addView(tv(txt, 11.2f, NAVY, false), new LinearLayout.LayoutParams(0, -2, 1));
                box.addView(row);
                if (!(i == reps.length() - 1 && j == lines.length() - 1)) box.addView(thinRule());
            }
        }
        b.addView(box);
    }

    private String ensureConcertLabel(String day, String text, JSONObject e) {
        if (text == null) return "";
        String t = clean(text);
        if (norm(t).contains("concert")) return t;
        String concertDay = concertWeekday(e);
        String ct = concertTime(e);
        if (!concertDay.isEmpty() && norm(day).startsWith(norm(concertDay)) && !ct.isEmpty()) {
            if (t.matches(".*\\b" + java.util.regex.Pattern.quote(ct) + "\\s*[–-]\\s*\\d{1,2}:?\\d{0,2}.*") || t.startsWith(ct + "–") || t.startsWith(ct + "-")) {
                return "Concert " + t;
            }
        }
        return t;
    }

    private String collapsedPreview(JSONObject week) {
        JSONArray a = week.optJSONArray("events");
        if (a == null || a.length() == 0) return "";
        JSONObject e = a.optJSONObject(0); if (e == null) return "";
        if ("uncompleted".equals(s(e, "program_status"))) return "Program în pregătire";
        String d = firstConcertDate(e);
        String shortDate = "";
        try { LocalDate ld = LocalDate.parse(d); shortDate = ld.getDayOfMonth() + " " + roMonthShort(ld.getMonthValue()).toLowerCase(Locale.ROOT) + "."; } catch (Exception ignored) {}
        String cond = s(e.optJSONObject("conductor"), "name");
        String works = worksCompact(e);
        String time = concertTime(e);
        StringBuilder out = new StringBuilder(shortDate);
        if (!cond.isEmpty()) out.append("   ").append(cond);
        if (!works.isEmpty()) out.append(" — ").append(works);
        if (!time.isEmpty()) out.append(" — ").append(time);
        return out.toString();
    }

    private String worksCompact(JSONObject e) {
        JSONArray works = e.optJSONArray("works"); if (works == null) return "";
        Set<String> parts = new LinkedHashSet<>();
        for (int i = 0; i < works.length(); i++) {
            JSONObject w = works.optJSONObject(i); if (w == null) continue;
            String c = s(w, "composer"); if (!c.isEmpty()) parts.add(c);
            else { String t = s(w, "title"); if (!t.isEmpty()) parts.add(t); }
        }
        return String.join(" · ", parts);
    }

    private String eventMeta(JSONObject e) {
        List<String> x = new ArrayList<>();
        String time = concertTime(e); if (!time.isEmpty()) x.add(time);
        String venue = s(e, "venue"); if (!venue.isEmpty()) x.add(venue);
        JSONArray sols = e.optJSONArray("soloists");
        if (sols != null && sols.length() > 0) {
            JSONObject so = sols.optJSONObject(0); String line = s(so, "name"), inst = s(so, "instrument");
            if (!inst.isEmpty()) line += (line.isEmpty() ? "" : " — ") + inst;
            if (!line.isEmpty()) x.add(line);
        }
        JSONObject c = e.optJSONObject("conductor"); if (c != null && c.optBoolean("choir", false)) x.add("cu cor");
        return String.join(" · ", x);
    }

    private String rehearsalCompact(JSONObject e) {
        JSONArray reps = e.optJSONArray("rehearsals"); if (reps == null || reps.length() == 0) return "";
        String firstDay = "", lastDay = "", firstTime = "";
        for (int i = 0; i < reps.length(); i++) {
            JSONObject r = reps.optJSONObject(i); if (r == null) continue;
            String d = s(r, "day"); if (!d.isEmpty()) { if (firstDay.isEmpty()) firstDay = d; lastDay = d; }
            JSONArray lines = r.optJSONArray("lines");
            if (firstTime.isEmpty() && lines != null && lines.length() > 0) firstTime = s(lines.optJSONObject(0), "display");
        }
        String days = shortDay(firstDay);
        if (!lastDay.isEmpty() && !norm(lastDay).equals(norm(firstDay))) days += "–" + shortDay(lastDay);
        String ct = concertTime(e); String cd = concertWeekday(e);
        String out = "Programul zilei: " + days;
        if (!firstTime.isEmpty()) out += " " + firstTime;
        if (!ct.isEmpty()) out += " · Concert " + shortDay(cd) + " " + ct;
        return out;
    }

    private String stringsCompact(JSONObject e) {
        JSONArray arr = e.optJSONArray("string_distributions");
        if (arr != null) for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i); String d = s(x, "display"); if (!d.isEmpty()) return d;
        }
        JSONArray works = e.optJSONArray("works");
        if (works != null) for (int i = 0; i < works.length(); i++) {
            JSONObject w = works.optJSONObject(i); String d = s(w == null ? null : w.optJSONObject("string_distribution"), "display"); if (!d.isEmpty()) return d;
        }
        return "";
    }

    private int concertCount(JSONObject week) {
        JSONArray events = week.optJSONArray("events"); if (events == null) return 0;
        int n = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i); JSONArray c = e == null ? null : e.optJSONArray("concerts");
            if (c != null && c.length() > 0) n += c.length(); else if (e != null) n++;
        }
        return n;
    }

    private String firstConcertDate(JSONObject e) {
        JSONArray c = e.optJSONArray("concerts");
        if (c != null && c.length() > 0) {
            JSONObject x = c.optJSONObject(0); String d = s(x, "date"); if (!d.isEmpty()) return d;
            JSONArray cand = x == null ? null : x.optJSONArray("candidate_dates"); if (cand != null && cand.length() > 0) return clean(cand.optString(0, ""));
        }
        JSONObject di = e.optJSONObject("date"); JSONArray dates = di == null ? null : di.optJSONArray("dates");
        if (dates != null && dates.length() > 0) return clean(dates.optString(0, ""));
        return "";
    }

    private String concertTime(JSONObject e) {
        JSONArray c = e.optJSONArray("concerts");
        if (c != null && c.length() > 0) return s(c.optJSONObject(0), "time");
        return "";
    }

    private String concertWeekday(JSONObject e) {
        try { return roWeekday(LocalDate.parse(firstConcertDate(e))); } catch (Exception ex) { return ""; }
    }

    private String eventDateLabel(JSONObject e) {
        String d = firstConcertDate(e), t = concertTime(e);
        if (!d.isEmpty()) {
            try { LocalDate x = LocalDate.parse(d); return roWeekday(x) + ", " + x.getDayOfMonth() + " " + roMonthName(x.getMonthValue()) + (t.isEmpty() ? "" : " · " + t); } catch (Exception ignored) {}
        }
        return s(e.optJSONObject("date"), "raw");
    }

    private String shortDay(String d) {
        String n = norm(d);
        if (n.startsWith("luni")) return "lun";
        if (n.startsWith("mart")) return "mar";
        if (n.startsWith("mier")) return "mie";
        if (n.startsWith("joi")) return "joi";
        if (n.startsWith("vin")) return "vin";
        if (n.startsWith("samb")) return "sâm";
        if (n.startsWith("dum")) return "dum";
        return clean(d);
    }

    private String roWeekday(LocalDate d) {
        String[] a = {"", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"}; return a[d.getDayOfWeek().getValue()];
    }
    private String roWeekdayShort(LocalDate d) { return shortDay(roWeekday(d)).toUpperCase(Locale.ROOT); }
    private String roMonthName(int m) { String[] a={"","ianuarie","februarie","martie","aprilie","mai","iunie","iulie","august","septembrie","octombrie","noiembrie","decembrie"}; return a[m]; }
    private String roMonthShort(int m) { String x = roMonthName(m); if (x.length() <= 3) return x.toUpperCase(Locale.ROOT); return x.substring(0, 3).toUpperCase(Locale.ROOT); }

    private boolean isCurrentMonthSelected() {
        JSONObject m = selectedMonth(); if (m == null) return false; LocalDate n = LocalDate.now();
        return m.optInt("month", -1) == n.getMonthValue() && m.optInt("year", -1) == n.getYear();
    }

    private boolean weekContainsDate(JSONObject week, LocalDate d) {
        try { LocalDate a = LocalDate.parse(s(week, "start")), b = LocalDate.parse(s(week, "end")); return !d.isBefore(a) && !d.isAfter(b); } catch (Exception e) { return false; }
    }

    private boolean weekContainsEvent(JSONObject week, String id) {
        if (id == null || id.isEmpty()) return false; JSONArray a = week.optJSONArray("events"); if (a == null) return false;
        for (int i = 0; i < a.length(); i++) { JSONObject e = a.optJSONObject(i); if (e != null && id.equals(s(e, "id"))) return true; }
        return false;
    }

    private View thinRule() {
        View v = new View(this); v.setBackgroundColor(Color.rgb(234,234,232)); v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1))); return v;
    }

    private void addRecitals(JSONObject month) {
        JSONArray arr = month.optJSONArray("recitals"); if (arr == null) arr = month.optJSONArray("tmc_recitals");
        if (arr == null || arr.length() == 0) { messageCard("Niciun recital", "Nu există recitaluri în luna selectată."); return; }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i); if (e == null) continue;
            LinearLayout c = vertical(); c.setBackground(strokeBg(WHITE, 15, 1, BORDER)); pad(c, 14, 12, 14, 12);
            c.addView(tv(eventDateLabel(e), 11.5f, GOLD, true));
            String venue=s(e,"venue"); if(!venue.isEmpty()) c.addView(tv(venue,11,MUTED,false));
            String cn=s(e.optJSONObject("conductor"),"name"); if(!cn.isEmpty()) c.addView(serif(cn,16,NAVY,true));
            JSONArray sols=e.optJSONArray("soloists"); if(sols!=null) for(int j=0;j<sols.length();j++){JSONObject so=sols.optJSONObject(j);String line=s(so,"raw");if(!line.isEmpty())c.addView(tv(line,12.5f,NAVY,false));}
            JSONArray works=e.optJSONArray("works"); if(works!=null) for(int j=0;j<works.length();j++){JSONObject w=works.optJSONObject(j);String line=s(w,"composer")+(s(w,"composer").isEmpty()?"":" — ")+s(w,"title");if(!line.isEmpty())c.addView(tv(line,12.5f,NAVY,true));}
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(10),0,dp(10),dp(10));content.addView(c,p);
        }
    }

    private void messageCard(String title, String body) {
        LinearLayout c=vertical();c.setBackground(strokeBg(WHITE,15,1,BORDER));pad(c,20,24,20,24);
        TextView t=serif(title,17,NAVY,true);t.setGravity(Gravity.CENTER);c.addView(t);TextView b=tv(body,12.5f,MUTED,false);b.setGravity(Gravity.CENTER);pad(b,0,8,0,0);c.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(14),0,dp(14),dp(10));content.addView(c,p);
    }

    private void showRawDialog(JSONObject e) {
        String text=s(e.optJSONObject("raw"),"program");if(text.isEmpty())text="Nu există text de program în document.";
        new AlertDialog.Builder(this).setTitle("Textul original din Google Docs").setMessage(text).setPositiveButton("Închide",null).show();
    }

    public void showNews() {
        shell("Noutăți"); addSimpleHeader("Noutăți");
        String cached=getSharedPreferences(PREFS,MODE_PRIVATE).getString(CACHE_NEWS,null);
        if(cached!=null){try{renderNews(new JSONObject(cached));}catch(Exception ignored){messageCard("Se încarcă…","Citesc istoricul.");}}else messageCard("Se încarcă…","Citesc istoricul.");
        new Thread(()->{try{JSONObject j=getJson("/api/news?limit=200");getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_NEWS,j.toString()).apply();runOnUiThread(()->{shell("Noutăți");addSimpleHeader("Noutăți");renderNews(j);});}catch(Exception ignored){}}).start();
    }

    private void renderNews(JSONObject j){
        JSONArray a=j.optJSONArray("items");if(a==null||a.length()==0){messageCard("Nicio modificare detectată încă","Modificările viitoare vor apărea aici.");return;}
        for(int i=0;i<a.length();i++){JSONObject n=a.optJSONObject(i);if(n==null)continue;LinearLayout c=vertical();c.setBackground(strokeBg(WHITE,14,1,BORDER));pad(c,14,11,14,11);c.addView(serif("Program modificat",15,NAVY,true));c.addView(tv(s(n,"week_label"),12,MUTED,true));JSONArray ch=n.optJSONArray("changes");if(ch!=null)for(int k=0;k<ch.length();k++){String l=s(ch.optJSONObject(k),"label");if(!l.isEmpty())c.addView(tv("• "+l,11.5f,MUTED,false));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(10),0,dp(10),dp(9));content.addView(c,p);}
    }

    public void showSearch(){
        shell("Caută");addSimpleHeader("Caută");EditText q=new EditText(this);q.setHint("Dirijor, solist, compozitor, lucrare…");q.setTextSize(15);q.setSingleLine(true);q.setBackground(strokeBg(WHITE,13,1,BORDER));pad(q,14,10,14,10);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(50));qp.setMargins(dp(12),0,dp(12),dp(10));content.addView(q,qp);LinearLayout results=vertical();content.addView(results);q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int c){if(searchRunnable!=null)handler.removeCallbacks(searchRunnable);String z=s.toString();searchRunnable=()->localSearch(z,results);handler.postDelayed(searchRunnable,180);}});
    }

    private void localSearch(String q,LinearLayout out){
        out.removeAllViews();if(q.trim().length()<2){TextView x=tv("Scrie cel puțin 2 caractere.",12,MUTED,false);x.setGravity(Gravity.CENTER);out.addView(x);return;}if(program==null)return;String needle=norm(q);JSONArray months=program.optJSONArray("months");int hits=0;if(months!=null)for(int mi=0;mi<months.length();mi++){JSONObject m=months.optJSONObject(mi);if(m==null)continue;JSONArray weeks=m.optJSONArray("weeks");if(weeks!=null)for(int wi=0;wi<weeks.length();wi++){JSONObject w=weeks.optJSONObject(wi);JSONArray ev=w==null?null:w.optJSONArray("events");if(ev!=null)for(int ei=0;ei<ev.length();ei++){JSONObject e=ev.optJSONObject(ei);if(e!=null&&norm(s(e,"search_text")+" "+e.toString()).contains(needle)){addSearchResult(out,e,mi,false);hits++;}}}JSONArray rec=m.optJSONArray("recitals");if(rec==null)rec=m.optJSONArray("tmc_recitals");if(rec!=null)for(int ei=0;ei<rec.length();ei++){JSONObject e=rec.optJSONObject(ei);if(e!=null&&norm(s(e,"search_text")+" "+e.toString()).contains(needle)){addSearchResult(out,e,mi,true);hits++;}}}if(hits==0){TextView x=tv("Niciun rezultat.",13,MUTED,false);x.setGravity(Gravity.CENTER);out.addView(x);}
    }

    private void addSearchResult(LinearLayout out,JSONObject e,int mi,boolean recital){
        LinearLayout c=vertical();c.setBackground(strokeBg(WHITE,13,1,BORDER));pad(c,14,10,14,10);c.addView(tv(eventDateLabel(e),11,GOLD,true));String cn=s(e.optJSONObject("conductor"),"name");if(!cn.isEmpty())c.addView(serif(cn,15,NAVY,true));String w=worksCompact(e);if(!w.isEmpty())c.addView(tv(w,12,MUTED,false));c.setOnClickListener(v->{selectedMonthIndex=mi;showProgram(recital,s(e,"id"));});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(10),0,dp(10),dp(8));out.addView(c,p);
    }

    private void addSimpleHeader(String page){
        LinearLayout r=horizontal();pad(r,18,14,16,12);LinearLayout t=vertical();t.addView(tv("Stagiune Filarmonica Transilvania",11,MUTED,false));t.addView(serif(page,27,NAVY,true));r.addView(t,new LinearLayout.LayoutParams(0,-2,1));TextView s=tv("Actualizat\n"+syncTime,10,MUTED,false);s.setGravity(Gravity.CENTER);r.addView(s);content.addView(r);
    }

    private void manualRefresh(TextView button){
        button.setEnabled(false);button.setAlpha(.6f);Toast.makeText(this,"Verific Google Docs…",Toast.LENGTH_SHORT).show();new Thread(()->{try{JSONObject result=getJson("/api/refresh");JSONObject p=getJson("/api/program");JSONObject st=getJson("/api/status");program=p;cacheProgram(p);updateSync(st);int ch=result.optInt("change_count",0);runOnUiThread(()->{button.setEnabled(true);button.setAlpha(1f);showProgram(recitalSelected,null);Toast.makeText(this,ch==0?"Programul este la zi.":"Actualizat: "+ch+" modificări.",Toast.LENGTH_LONG).show();});}catch(Exception e){runOnUiThread(()->{button.setEnabled(true);button.setAlpha(1f);Toast.makeText(this,"Actualizarea a eșuat: "+shortError(e),Toast.LENGTH_LONG).show();});}}).start();
    }

    private void loadProgramAndStatus(){
        new Thread(()->{try{JSONObject p=getJson("/api/program");JSONObject st=getJson("/api/status");program=p;cacheProgram(p);updateSync(st);runOnUiThread(()->{if(!initialMonthChosen)chooseInitialMonth();showProgram(recitalSelected,targetEventId);});}catch(Exception e){runOnUiThread(()->{if(program==null){shell("Program");addHeroHeader();addSegments();messageCard("Conexiune indisponibilă","Nu pot ajunge la NAS și nu există încă o copie salvată pe telefon.");}else Toast.makeText(this,"Folosesc copia salvată pe telefon.",Toast.LENGTH_SHORT).show();});}}).start();
    }

    private JSONObject getJson(String path)throws Exception{URL u=new URL(API_BASE+path);HttpURLConnection c=(HttpURLConnection)u.openConnection();c.setRequestMethod("GET");c.setConnectTimeout(6000);c.setReadTimeout(22000);c.setRequestProperty("Accept","application/json");int code=c.getResponseCode();BufferedReader r=new BufferedReader(new InputStreamReader(code>=200&&code<300?c.getInputStream():c.getErrorStream(),StandardCharsets.UTF_8));StringBuilder sb=new StringBuilder();String line;while((line=r.readLine())!=null)sb.append(line);r.close();c.disconnect();if(code<200||code>=300)throw new Exception("HTTP "+code);return new JSONObject(sb.toString());}
    private void cacheProgram(JSONObject p){getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_PROGRAM,p.toString()).apply();}
    private void updateSync(JSONObject st){String iso=s(st,"last_scan");if(iso.isEmpty())return;try{syncTime=OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Europe/Bucharest")).format(DateTimeFormatter.ofPattern("HH:mm"));}catch(Exception e){try{syncTime=iso.substring(11,16);}catch(Exception ignored){}}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_SYNC,syncTime).apply();}
    private void chooseInitialMonth(){if(program==null)return;JSONArray months=program.optJSONArray("months");if(months==null||months.length()==0)return;LocalDate now=LocalDate.now();for(int i=0;i<months.length();i++){JSONObject m=months.optJSONObject(i);if(m!=null&&m.optInt("month")==now.getMonthValue()&&m.optInt("year")==now.getYear()){selectedMonthIndex=i;initialMonthChosen=true;return;}}selectedMonthIndex=0;initialMonthChosen=true;}
    private String norm(String x){String n=Normalizer.normalize(x==null?"":x,Normalizer.Form.NFD).replaceAll("\\p{M}+","");return n.toLowerCase(Locale.ROOT);}
    private String shortError(Exception e){String x=e.getMessage();if(x==null||x.trim().isEmpty())x=e.getClass().getSimpleName();return x.length()>90?x.substring(0,90):x;}

    private class HallSketchView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        HallSketchView(Activity c){super(c);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(Color.argb(28,118,92,48));}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(), h=getHeight();float x=w*.52f,y=h*.12f;path.reset();path.moveTo(x,h*.42f);path.lineTo(w*.94f,h*.42f);path.moveTo(w*.58f,h*.42f);path.lineTo(w*.62f,h*.25f);path.lineTo(w*.68f,h*.18f);path.lineTo(w*.74f,h*.25f);path.lineTo(w*.78f,h*.42f);path.moveTo(w*.84f,h*.42f);path.lineTo(w*.84f,h*.12f);path.lineTo(w*.88f,h*.07f);path.lineTo(w*.92f,h*.12f);path.lineTo(w*.92f,h*.42f);for(int i=0;i<5;i++){float ax=w*(.59f+i*.07f);path.moveTo(ax,h*.42f);path.lineTo(ax,h*.70f);path.quadTo(ax+w*.025f,h*.57f,ax+w*.05f,h*.70f);path.lineTo(ax+w*.05f,h*.42f);}path.moveTo(w*.54f,h*.70f);path.lineTo(w*.96f,h*.70f);path.moveTo(w*.56f,h*.75f);path.lineTo(w*.95f,h*.75f);c.drawPath(path,p);}
    }
}
