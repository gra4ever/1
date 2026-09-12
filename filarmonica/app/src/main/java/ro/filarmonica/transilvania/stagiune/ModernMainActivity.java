package ro.filarmonica.transilvania.stagiune;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;

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
import java.util.List;
import java.util.Locale;

public class ModernMainActivity extends Activity {
    private static final String API_BASE = "http://192.168.0.191:8787";
    private static final String PREFS = "stagiune_prefs_v4";
    private static final String CACHE_PROGRAM = "program_json";
    private static final String CACHE_NEWS = "news_json";
    private static final String CACHE_SYNC = "sync_time";

    private final int NAVY = Color.rgb(16, 43, 82);
    private final int BLUE = Color.rgb(35, 92, 164);
    private final int BLUE2 = Color.rgb(60, 125, 212);
    private final int GOLD = Color.rgb(235, 190, 86);
    private final int GOLD_PALE = Color.rgb(255, 248, 228);
    private final int PALE = Color.rgb(237, 244, 252);
    private final int BG = Color.rgb(247, 249, 252);
    private final int MUTED = Color.rgb(91, 108, 132);
    private final int BORDER = Color.rgb(218, 226, 237);

    private LinearLayout content, bottom;
    private ScrollView scrollView;
    private JSONObject program;
    private int selectedMonthIndex = 0;
    private boolean recitalSelected = false;
    private String targetEventId = null;
    private View targetView = null;
    private String syncTime = "—";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
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
    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private TextView tv(String text, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(text == null ? "" : text);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0, 1.05f);
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
        return v;
    }

    private void shell(String selected) {
        LinearLayout root = vertical();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
            return insets;
        });

        content = vertical();
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));

        bottom = horizontal();
        bottom.setBackgroundColor(Color.WHITE);
        pad(bottom, 8, 4, 8, 4);
        addNav("▣\nProgram", "Program".equals(selected), () -> showProgram(recitalSelected, null));
        addNav("▤\nNoutăți", "Noutăți".equals(selected), this::showNews);
        addNav("⌕\nCaută", "Caută".equals(selected), this::showSearch);
        root.addView(bottom, new LinearLayout.LayoutParams(-1, dp(60)));

        setContentView(root);
        root.requestApplyInsets();
    }

    private void addNav(String label, boolean selected, Runnable action) {
        TextView b = tv(label, 12, selected ? BLUE : MUTED, selected);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> action.run());
        bottom.addView(b, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addHeader(String page) {
        LinearLayout row = horizontal();
        pad(row, 18, 12, 14, 10);
        LinearLayout titles = vertical();
        if ("Program".equals(page)) {
            TextView p = tv("PROGRAM", 29, NAVY, true);
            p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            titles.addView(p);
            titles.addView(tv("Stagiune Filarmonica Transilvania", 11.5f, MUTED, false));
        } else {
            titles.addView(tv("Stagiune Filarmonica Transilvania", 11.5f, MUTED, false));
            TextView p = tv(page, 27, NAVY, true);
            p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            titles.addView(p);
        }
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout sync = vertical();
        sync.setGravity(Gravity.CENTER);
        sync.setBackground(bg(PALE, 12));
        pad(sync, 10, 7, 10, 7);
        TextView a = tv("ACTUALIZAT", 8.5f, MUTED, true); a.setGravity(Gravity.CENTER);
        TextView b = tv(syncTime, 15, NAVY, true); b.setGravity(Gravity.CENTER);
        sync.addView(a); sync.addView(b);
        row.addView(sync);
        content.addView(row);
    }

    private void showMessageCard(String title, String text) {
        LinearLayout c = vertical();
        c.setBackground(strokeBg(Color.WHITE, 16, 1, BORDER));
        pad(c, 22, 26, 22, 26);
        TextView a = tv(title, 17, NAVY, true); a.setGravity(Gravity.CENTER);
        TextView b = tv(text, 13, MUTED, false); b.setGravity(Gravity.CENTER); pad(b, 0, 9, 0, 0);
        c.addView(a); c.addView(b);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(dp(14), dp(4), dp(14), 0);
        content.addView(c, p);
    }

    public void showProgram(boolean recital, String targetId) {
        recitalSelected = recital;
        targetEventId = targetId;
        targetView = null;
        shell("Program");
        addHeader("Program");

        if (program == null) {
            showMessageCard("Se încarcă programul…", "Citesc ultima versiune disponibilă.");
            return;
        }
        JSONArray months = program.optJSONArray("months");
        if (months == null || months.length() == 0) {
            showMessageCard("Program indisponibil", "Nu există luni în program.");
            return;
        }
        if (selectedMonthIndex < 0 || selectedMonthIndex >= months.length()) selectedMonthIndex = 0;

        addMonthSelector();
        addSegments();
        JSONObject month = months.optJSONObject(selectedMonthIndex);
        if (month != null) {
            if (recitalSelected) addRecitals(month);
            else addOrchestraWeeks(month);
        }
        Space s = new Space(this);
        content.addView(s, new LinearLayout.LayoutParams(1, dp(16)));
        if (targetView != null) {
            View v = targetView;
            scrollView.postDelayed(() -> scrollView.smoothScrollTo(0, Math.max(0, v.getTop() - dp(12))), 140);
            targetEventId = null;
        }
    }

    private void addMonthSelector() {
        JSONObject month = selectedMonth();
        String label = month == null ? "ALEGE LUNA" : s(month, "label");
        LinearLayout outer = horizontal();
        pad(outer, 14, 0, 14, 10);

        LinearLayout monthButton = vertical();
        monthButton.setBackground(bg(GOLD, 14));
        pad(monthButton, 14, 7, 14, 8);
        TextView cap = tv("LUNA", 8.5f, NAVY, true);
        TextView name = tv(label + "   ▾", 16.5f, NAVY, true);
        monthButton.addView(cap); monthButton.addView(name);
        monthButton.setOnClickListener(v -> showMonthPicker());
        outer.addView(monthButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        TextView refresh = tv("↻\nActualizează", 10.5f, Color.WHITE, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(bg(BLUE, 14));
        refresh.setOnClickListener(v -> manualRefresh(refresh));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(92), dp(56));
        rp.setMargins(dp(9), 0, 0, 0);
        outer.addView(refresh, rp);
        content.addView(outer);
    }

    private void showMonthPicker() {
        JSONArray months = program == null ? null : program.optJSONArray("months");
        if (months == null) return;
        String[] labels = new String[months.length()];
        for (int i = 0; i < months.length(); i++) labels[i] = s(months.optJSONObject(i), "label");
        new AlertDialog.Builder(this)
                .setTitle("Alege luna")
                .setSingleChoiceItems(labels, selectedMonthIndex, (d, which) -> {
                    selectedMonthIndex = which;
                    targetEventId = null;
                    d.dismiss();
                    showProgram(recitalSelected, null);
                })
                .setNegativeButton("Închide", null)
                .show();
    }

    private void addSegments() {
        LinearLayout seg = horizontal();
        pad(seg, 14, 0, 14, 11);
        TextView orch = chip("Orchestră", !recitalSelected);
        TextView rec = chip("Recitaluri", recitalSelected);
        orch.setOnClickListener(v -> showProgram(false, null));
        rec.setOnClickListener(v -> showProgram(true, null));
        seg.addView(orch, new LinearLayout.LayoutParams(0, dp(39), 1));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(39), 1);
        p.setMargins(dp(8), 0, 0, 0);
        seg.addView(rec, p);
        content.addView(seg);
    }

    private TextView chip(String text, boolean on) {
        TextView t = tv(text, 12.5f, on ? Color.WHITE : NAVY, on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(on ? BLUE : PALE, 20));
        return t;
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
            showMessageCard("Niciun eveniment", "Nu există evenimente orchestrale în luna selectată.");
            return;
        }
        boolean openedAny = false;
        LocalDate today = LocalDate.now();
        for (int i = 0; i < weeks.length(); i++) {
            JSONObject week = weeks.optJSONObject(i);
            if (week == null) continue;
            boolean target = weekContainsEvent(week, targetEventId);
            boolean current = weekContainsDate(week, today);
            boolean open = target || current;
            if (!open && targetEventId == null && !openedAny && i == 0 && !isCurrentMonthSelected()) open = true;
            if (open) openedAny = true;
            addWeekCard(week, open);
        }
    }

    private boolean isCurrentMonthSelected() {
        JSONObject m = selectedMonth();
        if (m == null) return false;
        LocalDate n = LocalDate.now();
        return m.optInt("month", -1) == n.getMonthValue() && m.optInt("year", -1) == n.getYear();
    }

    private boolean weekContainsDate(JSONObject week, LocalDate d) {
        try {
            String a = s(week, "start"), b = s(week, "end");
            if (a.isEmpty() || b.isEmpty()) return false;
            LocalDate x = LocalDate.parse(a), y = LocalDate.parse(b);
            return !d.isBefore(x) && !d.isAfter(y);
        } catch (Exception e) { return false; }
    }

    private boolean weekContainsEvent(JSONObject week, String id) {
        if (id == null || id.isEmpty()) return false;
        JSONArray a = week.optJSONArray("events");
        if (a == null) return false;
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e != null && id.equals(s(e, "id"))) return true;
        }
        return false;
    }

    private void addWeekCard(JSONObject week, boolean open) {
        LinearLayout card = vertical();
        card.setBackground(strokeBg(Color.WHITE, 16, 1, BORDER));

        LinearLayout head = horizontal();
        head.setBackground(bg(NAVY, 16));
        pad(head, 15, 12, 12, 12);
        LinearLayout labels = vertical();
        labels.addView(tv("SĂPTĂMÂNA", 8.5f, GOLD, true));
        labels.addView(tv(s(week, "label"), 16, Color.WHITE, true));
        head.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = tv(open ? "⌃" : "⌄", 19, Color.WHITE, true);
        head.addView(arrow);
        card.addView(head);

        LinearLayout body = vertical();
        pad(body, 13, 8, 13, 13);
        body.setVisibility(open ? View.VISIBLE : View.GONE);
        JSONArray events = week.optJSONArray("events");
        if (events != null) {
            for (int i = 0; i < events.length(); i++) {
                JSONObject e = events.optJSONObject(i);
                if (e == null) continue;
                if (i > 0) body.addView(bigRule());
                renderEvent(body, e);
                if (targetEventId != null && targetEventId.equals(s(e, "id"))) targetView = card;
            }
        }
        card.addView(body);
        head.setOnClickListener(v -> {
            boolean isOpen = body.getVisibility() == View.VISIBLE;
            body.setVisibility(isOpen ? View.GONE : View.VISIBLE);
            arrow.setText(isOpen ? "⌄" : "⌃");
        });

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(14), 0, dp(14), dp(10));
        content.addView(card, cp);
    }

    private void renderEvent(LinearLayout b, JSONObject e) {
        TextView date = tv(eventDateLabel(e), 12.5f, BLUE, true);
        date.setBackground(bg(PALE, 10));
        pad(date, 10, 7, 10, 7);
        LinearLayout.LayoutParams dateP = new LinearLayout.LayoutParams(-2, -2);
        dateP.setMargins(0, dp(4), 0, dp(9));
        b.addView(date, dateP);

        String venue = s(e, "venue");
        if (!venue.isEmpty()) {
            TextView v = tv("⌖  " + venue, 11.5f, MUTED, false);
            pad(v, 1, 0, 0, 8); b.addView(v);
        }

        JSONObject conductor = e.optJSONObject("conductor");
        String conductorName = s(conductor, "name");
        if (!conductorName.isEmpty()) {
            b.addView(tv("DIRIJOR", 8.5f, MUTED, true));
            String name = conductorName;
            if (conductor != null && conductor.optBoolean("choir", false)) name += " + COR";
            TextView n = tv(name, 18, NAVY, true);
            n.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
            pad(n, 0, 2, 0, 8); b.addView(n);
        }

        JSONArray soloists = e.optJSONArray("soloists");
        if (soloists != null && soloists.length() > 0) {
            LinearLayout soloBox = vertical();
            soloBox.setBackground(bg(GOLD_PALE, 12));
            pad(soloBox, 11, 8, 11, 8);
            soloBox.addView(tv("SOLIST", 8.5f, Color.rgb(137, 101, 18), true));
            for (int i = 0; i < soloists.length(); i++) {
                JSONObject so = soloists.optJSONObject(i);
                if (so == null) continue;
                String line = s(so, "name");
                String inst = s(so, "instrument");
                String role = s(so, "role");
                if (!inst.isEmpty()) line += (line.isEmpty() ? "" : " — ") + inst;
                if (!role.isEmpty()) line += " " + role;
                if (!line.isEmpty()) soloBox.addView(tv(line, 14.5f, NAVY, true));
            }
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(0, 0, 0, dp(8));
            b.addView(soloBox, sp);
        }

        String status = s(e, "program_status");
        JSONArray works = e.optJSONArray("works");
        if ("uncompleted".equals(status)) {
            TextView x = tv("Program necompletat", 14, MUTED, false);
            pad(x, 0, 10, 0, 10); b.addView(x);
        } else if (works != null && works.length() > 0) {
            TextView cap = tv("PROGRAM", 9, MUTED, true); pad(cap, 0, 5, 0, 1); b.addView(cap);
            for (int i = 0; i < works.length(); i++) {
                JSONObject w = works.optJSONObject(i);
                if (w != null) renderWork(b, w);
            }
        } else if ("partial".equals(status)) {
            b.addView(tv("Program necompletat", 14, MUTED, false));
        }

        JSONArray notes = e.optJSONArray("program_notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                String n = notes.optString(i, "");
                if (!n.isEmpty() && !"null".equalsIgnoreCase(n)) b.addView(tv(n, 12, MUTED, false));
            }
        }

        renderUnattachedStrings(b, e.optJSONArray("string_distributions"));
        renderRehearsals(b, e.optJSONArray("rehearsals"));
        addRaw(b, e);
    }

    private void renderWork(LinearLayout b, JSONObject w) {
        String composer = s(w, "composer"), title = s(w, "title");
        String heading = composer.isEmpty() ? title : composer + " — " + title;
        if (!heading.isEmpty()) {
            TextView t = tv(heading, 14.2f, NAVY, true);
            pad(t, 0, 7, 0, 3); b.addView(t);
        }
        JSONArray orch = w.optJSONArray("orchestrations");
        if (orch != null && orch.length() > 0) {
            for (int i = 0; i < orch.length(); i++) {
                JSONObject o = orch.optJSONObject(i);
                if (o == null) continue;
                String label = s(o, "label"), disp = s(o, "display");
                if (!label.isEmpty()) b.addView(tv(label, 11, MUTED, true));
                if (!disp.isEmpty()) b.addView(tv(disp, 12, MUTED, false));
            }
        }
        JSONArray details = w.optJSONArray("details");
        if (details != null) {
            for (int i = 0; i < details.length(); i++) {
                String x = details.optString(i, "");
                if (!x.isEmpty() && !"null".equalsIgnoreCase(x)) b.addView(tv(x, 11.8f, MUTED, false));
            }
        }
        JSONObject sd = w.optJSONObject("string_distribution");
        String display = s(sd, "display");
        if (!display.isEmpty()) {
            TextView st = tv(display, 10.8f, MUTED, false);
            pad(st, 0, 4, 0, 1); b.addView(st);
        }
        b.addView(rule());
    }

    private void renderUnattachedStrings(LinearLayout b, JSONArray arr) {
        if (arr == null) return;
        boolean cap = false;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject x = arr.optJSONObject(i);
            if (x == null || !s(x, "applies_to").isEmpty()) continue;
            String display = s(x, "display");
            if (display.isEmpty()) continue;
            if (!cap) {
                TextView h = tv("CORDARI", 11, NAVY, true); h.setBackground(bg(PALE, 8)); pad(h, 9, 6, 9, 6); b.addView(h); cap = true;
            }
            TextView d = tv(display, 11.3f, MUTED, false); pad(d, 2, 5, 2, 2); b.addView(d);
        }
    }

    private void renderRehearsals(LinearLayout b, JSONArray reps) {
        if (reps == null || reps.length() == 0) return;
        TextView h = tv("PROGRAMUL ZILEI", 11, Color.WHITE, true);
        h.setBackground(bg(BLUE, 9)); pad(h, 10, 7, 10, 7);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2); hp.setMargins(0, dp(9), 0, dp(3));
        b.addView(h, hp);

        for (int i = 0; i < reps.length(); i++) {
            JSONObject r = reps.optJSONObject(i);
            if (r == null) continue;
            String day = s(r, "day"), ann = s(r, "annotation");
            if (!ann.isEmpty()) day += (day.isEmpty() ? "" : " ") + ann;
            JSONArray lines = r.optJSONArray("lines");
            if (lines == null || lines.length() == 0) {
                if (!day.isEmpty()) rehearsalRow(b, day, "");
                continue;
            }
            for (int j = 0; j < lines.length(); j++) {
                JSONObject line = lines.optJSONObject(j);
                String display = s(line, "display");
                rehearsalRow(b, j == 0 ? day : "", display);
            }
        }
    }

    private void rehearsalRow(LinearLayout b, String day, String text) {
        if (day.isEmpty() && text.isEmpty()) return;
        LinearLayout r = horizontal(); r.setGravity(Gravity.TOP); pad(r, 2, 5, 2, 5);
        TextView d = tv(day, 12, NAVY, true);
        TextView x = tv(text, 12, MUTED, false);
        r.addView(d, new LinearLayout.LayoutParams(dp(94), -2));
        r.addView(x, new LinearLayout.LayoutParams(0, -2, 1));
        b.addView(r);
    }

    private void addRaw(LinearLayout b, JSONObject e) {
        TextView raw = tv("Vezi textul original  ›", 10.5f, MUTED, true);
        raw.setGravity(Gravity.END); pad(raw, 0, 9, 0, 1);
        raw.setOnClickListener(v -> showRawDialog(e)); b.addView(raw);
    }

    private void showRawDialog(JSONObject e) {
        JSONObject raw = e.optJSONObject("raw");
        String text = s(raw, "program");
        if (text.isEmpty()) text = "Nu există text de program în document.";
        new AlertDialog.Builder(this).setTitle("Textul original din Google Docs").setMessage(text)
                .setPositiveButton("Închide", null).show();
    }

    private void addRecitals(JSONObject month) {
        JSONArray arr = month.optJSONArray("recitals");
        if (arr == null) arr = month.optJSONArray("tmc_recitals");
        if (arr == null || arr.length() == 0) {
            showMessageCard("Niciun recital", "Nu există recitaluri în luna selectată.");
            return;
        }
        for (int i = 0; i < arr.length(); i++) {
            JSONObject e = arr.optJSONObject(i); if (e == null) continue;
            LinearLayout c = vertical(); c.setBackground(strokeBg(Color.WHITE, 15, 1, BORDER)); pad(c, 14, 12, 14, 12);
            TextView d = tv(eventDateLabel(e), 11.8f, BLUE, true); pad(d, 0, 0, 0, 6); c.addView(d);
            String venue = s(e, "venue"); if (!venue.isEmpty()) c.addView(tv("⌖  " + venue, 11.3f, MUTED, false));
            JSONObject conductor = e.optJSONObject("conductor");
            String name = s(conductor, "name"); if (!name.isEmpty()) c.addView(tv(name, 15.5f, NAVY, true));
            JSONArray sols = e.optJSONArray("soloists");
            if (sols != null) for (int j = 0; j < sols.length(); j++) {
                JSONObject so = sols.optJSONObject(j); String line = s(so, "raw"); if (!line.isEmpty()) c.addView(tv(line, 13, NAVY, true));
            }
            JSONArray works = e.optJSONArray("works");
            if (works != null && works.length() > 0) {
                c.addView(rule());
                for (int j = 0; j < works.length(); j++) {
                    JSONObject w = works.optJSONObject(j); if (w == null) continue;
                    String comp = s(w, "composer"), title = s(w, "title");
                    String x = comp.isEmpty() ? title : comp + " — " + title;
                    if (!x.isEmpty()) { TextView z = tv(x, 13.5f, NAVY, true); pad(z, 0, 6, 0, 1); c.addView(z); }
                }
            }
            addRaw(c, e);
            if (targetEventId != null && targetEventId.equals(s(e, "id"))) targetView = c;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(14), 0, dp(14), dp(10)); content.addView(c, p);
        }
    }

    private String eventDateLabel(JSONObject e) {
        JSONArray concerts = e.optJSONArray("concerts");
        StringBuilder out = new StringBuilder();
        if (concerts != null) for (int i = 0; i < concerts.length(); i++) {
            JSONObject c = concerts.optJSONObject(i); if (c == null) continue;
            String iso = s(c, "date"); if (iso.isEmpty()) continue;
            if (out.length() > 0) out.append("\n");
            out.append(roDate(iso));
            String time = s(c, "time"); if (!time.isEmpty()) out.append(" • ").append(time);
        }
        if (out.length() > 0) return out.toString();
        JSONObject raw = e.optJSONObject("raw");
        String x = s(raw, "date").replace("\n", " / ");
        return x;
    }

    private String roDate(String iso) {
        try {
            LocalDate d = LocalDate.parse(iso);
            String[] days = {"", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă", "Duminică"};
            String[] months = {"", "ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie", "august", "septembrie", "octombrie", "noiembrie", "decembrie"};
            return days[d.getDayOfWeek().getValue()] + ", " + d.getDayOfMonth() + " " + months[d.getMonthValue()];
        } catch (Exception e) { return iso; }
    }

    private View rule() {
        View r = new View(this); r.setBackgroundColor(Color.rgb(229, 234, 242));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.setMargins(0, dp(8), 0, 0); r.setLayoutParams(p); return r;
    }

    private View bigRule() {
        View r = new View(this); r.setBackgroundColor(Color.rgb(204, 214, 228));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(2)); p.setMargins(0, dp(14), 0, dp(10)); r.setLayoutParams(p); return r;
    }

    public void showNews() {
        shell("Noutăți"); addHeader("Noutăți");
        String cached = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_NEWS, null);
        if (cached != null) {
            try { renderNews(new JSONObject(cached), false); } catch (Exception ignored) { showMessageCard("Se încarcă…", "Citesc istoricul modificărilor."); }
        } else showMessageCard("Se încarcă…", "Citesc istoricul modificărilor.");
        new Thread(() -> {
            try {
                JSONObject j = getJson("/api/news?limit=200");
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CACHE_NEWS, j.toString()).apply();
                runOnUiThread(() -> renderNews(j, true));
            } catch (Exception ignored) {}
        }).start();
    }

    private void renderNews(JSONObject j, boolean rebuild) {
        if (rebuild) { shell("Noutăți"); addHeader("Noutăți"); }
        else { content.removeAllViews(); addHeader("Noutăți"); }
        JSONArray items = j.optJSONArray("items");
        if (items == null || items.length() == 0) {
            showMessageCard("Nicio modificare detectată încă", "Modificările viitoare vor apărea aici, cele mai noi primele."); return;
        }
        for (int i = 0; i < items.length(); i++) {
            JSONObject n = items.optJSONObject(i); if (n == null) continue;
            LinearLayout c = vertical(); c.setBackground(strokeBg(Color.WHITE, 14, 1, BORDER)); pad(c, 14, 11, 14, 11);
            c.addView(tv("Program modificat", 14.5f, NAVY, true));
            c.addView(tv(s(n, "week_label"), 12.5f, MUTED, true));
            JSONArray changes = n.optJSONArray("changes");
            if (changes != null) for (int k = 0; k < changes.length(); k++) {
                JSONObject ch = changes.optJSONObject(k); String label = s(ch, "label"); if (!label.isEmpty()) c.addView(tv("• " + label, 12, MUTED, false));
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(14), 0, dp(14), dp(9)); content.addView(c, p);
        }
    }

    public void showSearch() {
        shell("Caută"); addHeader("Caută");
        EditText q = new EditText(this);
        q.setHint("Dirijor, solist, instrument, compozitor, lucrare…"); q.setTextSize(15); q.setSingleLine(true);
        q.setBackground(strokeBg(Color.WHITE, 13, 1, BORDER)); pad(q, 14, 10, 14, 10);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, dp(50)); qp.setMargins(dp(14), dp(3), dp(14), dp(10)); content.addView(q, qp);
        LinearLayout results = vertical(); content.addView(results);
        TextView hint = tv("Căutarea funcționează și din copia salvată pe telefon.", 12, MUTED, false); hint.setGravity(Gravity.CENTER); results.addView(hint);
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {}
            public void onTextChanged(CharSequence x, int a, int b, int c) {
                if (searchRunnable != null) handler.removeCallbacks(searchRunnable);
                String query = x.toString().trim();
                searchRunnable = () -> localSearch(query, results);
                handler.postDelayed(searchRunnable, 180);
            }
        });
    }

    private void localSearch(String query, LinearLayout results) {
        results.removeAllViews();
        if (query.length() < 2) { TextView x = tv("Scrie cel puțin 2 caractere.", 12, MUTED, false); x.setGravity(Gravity.CENTER); results.addView(x); return; }
        if (program == null) { results.addView(tv("Programul nu este încă disponibil.", 12, MUTED, false)); return; }
        String needle = norm(query);
        List<JSONObject> found = new ArrayList<>();
        JSONArray months = program.optJSONArray("months");
        if (months != null) for (int mi = 0; mi < months.length(); mi++) {
            JSONObject m = months.optJSONObject(mi); if (m == null) continue;
            JSONArray weeks = m.optJSONArray("weeks");
            if (weeks != null) for (int wi = 0; wi < weeks.length(); wi++) {
                JSONObject w = weeks.optJSONObject(wi); if (w == null) continue;
                JSONArray ev = w.optJSONArray("events");
                if (ev != null) for (int ei = 0; ei < ev.length(); ei++) {
                    JSONObject e = ev.optJSONObject(ei); if (matches(e, needle)) { tagMonth(e, mi); found.add(e); }
                }
            }
            JSONArray rec = m.optJSONArray("recitals"); if (rec == null) rec = m.optJSONArray("tmc_recitals");
            if (rec != null) for (int ei = 0; ei < rec.length(); ei++) {
                JSONObject e = rec.optJSONObject(ei); if (matches(e, needle)) { tagMonth(e, mi); found.add(e); }
            }
        }
        if (found.isEmpty()) { TextView x = tv("Niciun rezultat.", 13, MUTED, false); x.setGravity(Gravity.CENTER); results.addView(x); return; }
        for (JSONObject e : found) {
            LinearLayout c = vertical(); c.setBackground(strokeBg(Color.WHITE, 13, 1, BORDER)); pad(c, 14, 10, 14, 10);
            c.addView(tv(eventDateLabel(e), 11.5f, BLUE, true));
            JSONObject cond = e.optJSONObject("conductor"); String cn = s(cond, "name"); if (!cn.isEmpty()) c.addView(tv(cn, 14, NAVY, true));
            JSONArray sols = e.optJSONArray("soloists"); if (sols != null && sols.length() > 0) {
                JSONObject so = sols.optJSONObject(0); String line = s(so, "raw"); if (!line.isEmpty()) c.addView(tv(line, 12.5f, MUTED, false));
            }
            JSONArray works = e.optJSONArray("works"); if (works != null && works.length() > 0) {
                JSONObject w = works.optJSONObject(0); String comp = s(w, "composer"), title = s(w, "title");
                String line = comp.isEmpty() ? title : comp + " — " + title; if (!line.isEmpty()) c.addView(tv(line, 12, MUTED, false));
            }
            c.setOnClickListener(v -> openLocalSearch(e));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(dp(14), 0, dp(14), dp(9)); results.addView(c, p);
        }
    }

    private void tagMonth(JSONObject e, int index) { try { e.put("_ui_month_index", index); } catch (Exception ignored) {} }
    private boolean matches(JSONObject e, String needle) {
        String hay = s(e, "search_text");
        if (hay.isEmpty()) hay = e.toString();
        return norm(hay).contains(needle);
    }
    private String norm(String x) {
        String n = Normalizer.normalize(x == null ? "" : x, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.toLowerCase(Locale.ROOT);
    }
    private void openLocalSearch(JSONObject e) {
        selectedMonthIndex = e.optInt("_ui_month_index", selectedMonthIndex);
        showProgram("recital".equals(s(e, "type")), s(e, "id"));
    }

    private void manualRefresh(TextView button) {
        button.setEnabled(false); button.setAlpha(.65f);
        Toast.makeText(this, "Verific Google Docs…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                JSONObject result = getJson("/api/refresh");
                JSONObject p = getJson("/api/program");
                JSONObject st = getJson("/api/status");
                program = p; cacheProgram(p); updateSyncFromStatus(st);
                int changes = result.optInt("change_count", 0);
                runOnUiThread(() -> {
                    button.setEnabled(true); button.setAlpha(1f); showProgram(recitalSelected, null);
                    Toast.makeText(this, changes == 0 ? "Programul este la zi." : "Actualizat: " + changes + " modificări.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> { button.setEnabled(true); button.setAlpha(1f); Toast.makeText(this, "Actualizarea a eșuat: " + shortError(e), Toast.LENGTH_LONG).show(); });
            }
        }).start();
    }

    private void loadProgramAndStatus() {
        new Thread(() -> {
            try {
                JSONObject p = getJson("/api/program");
                JSONObject st = getJson("/api/status");
                program = p; cacheProgram(p); updateSyncFromStatus(st);
                runOnUiThread(() -> { chooseInitialMonthIfNeeded(); showProgram(recitalSelected, targetEventId); });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (program == null) {
                        shell("Program"); addHeader("Program"); showMessageCard("Conexiune indisponibilă", "Nu pot ajunge la NAS și nu există încă o copie salvată pe telefon.");
                    } else Toast.makeText(this, "Folosesc copia salvată pe telefon.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateSyncFromStatus(JSONObject st) {
        String iso = s(st, "last_scan");
        if (iso.isEmpty()) return;
        try {
            OffsetDateTime odt = OffsetDateTime.parse(iso);
            syncTime = odt.atZoneSameInstant(ZoneId.of("Europe/Bucharest")).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            try { syncTime = iso.substring(11, 16); } catch (Exception ignored) { syncTime = "—"; }
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CACHE_SYNC, syncTime).apply();
    }

    private JSONObject getJson(String path) throws Exception {
        URL url = new URL(API_BASE + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(6000); c.setReadTimeout(22000); c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        BufferedReader r = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(); String line; while ((line = r.readLine()) != null) sb.append(line); r.close(); c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return new JSONObject(sb.toString());
    }

    private void cacheProgram(JSONObject p) { getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CACHE_PROGRAM, p.toString()).apply(); }

    private void chooseInitialMonthIfNeeded() {
        if (selectedMonthIndex == 0 && program != null) chooseInitialMonth();
    }

    private void chooseInitialMonth() {
        if (program == null) return;
        JSONArray months = program.optJSONArray("months"); if (months == null || months.length() == 0) return;
        LocalDate now = LocalDate.now();
        for (int i = 0; i < months.length(); i++) {
            JSONObject m = months.optJSONObject(i);
            if (m != null && m.optInt("month") == now.getMonthValue() && m.optInt("year") == now.getYear()) { selectedMonthIndex = i; return; }
        }
        selectedMonthIndex = 0;
    }

    private String shortError(Exception e) {
        String x = e.getMessage(); if (x == null || x.trim().isEmpty()) x = e.getClass().getSimpleName();
        return x.length() > 90 ? x.substring(0, 90) : x;
    }
}
