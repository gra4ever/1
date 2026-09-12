package ro.filarmonica.transilvania.stagiune;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
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
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String API_BASE = "http://192.168.0.191:8787";
    private static final String PREFS = "stagiune_prefs";
    private static final String CACHE_PROGRAM = "program_json";

    private final int NAVY = Color.rgb(18,48,94);
    private final int BLUE = Color.rgb(29,102,209);
    private final int PALE = Color.rgb(233,243,255);
    private final int BG = Color.rgb(248,250,253);
    private final int MUTED = Color.rgb(89,108,137);
    private final int BORDER = Color.rgb(211,224,242);

    private LinearLayout content, bottom;
    private ScrollView scrollView;
    private JSONObject program;
    private int selectedMonthIndex = 0;
    private boolean tmcSelected = false;
    private String targetEventId = null;
    private View targetView = null;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        String cached = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CACHE_PROGRAM, null);
        if (cached != null) {
            try {
                program = new JSONObject(cached);
                chooseInitialMonth();
            } catch (Exception ignored) {}
        }
        showProgram(false, null);
        loadProgram(false);
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }

    private TextView tv(String s,float sp,int color,boolean bold){
        TextView t=new TextView(this);
        t.setText(s == null ? "" : s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        t.setLineSpacing(0,1.04f);
        return t;
    }

    private GradientDrawable bg(int c,int r){
        GradientDrawable g=new GradientDrawable();
        g.setColor(c); g.setCornerRadius(dp(r));
        return g;
    }

    private GradientDrawable strokeBg(int c,int r,int sw,int sc){
        GradientDrawable g=bg(c,r);
        g.setStroke(dp(sw),sc);
        return g;
    }

    private void pad(View v,int l,int t,int r,int b){ v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private void shell(String selected){
        LinearLayout root=vertical();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });

        content=vertical();
        scrollView=new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(scrollView,new LinearLayout.LayoutParams(-1,0,1));

        bottom=horizontal();
        bottom.setBackgroundColor(Color.WHITE);
        pad(bottom,10,5,10,5);
        addNav("▣\nProgram","Program".equals(selected),()->showProgram(tmcSelected,null));
        addNav("▤\nNoutăți","Noutăți".equals(selected),this::showNews);
        addNav("⌕\nCaută","Caută".equals(selected),this::showSearch);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(60)));

        setContentView(root);
        root.requestApplyInsets();
    }

    private void addNav(String label,boolean selected,Runnable action){
        TextView b=tv(label,12,selected?BLUE:MUTED,selected);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v->action.run());
        bottom.addView(b,new LinearLayout.LayoutParams(0,-1,1));
    }

    private void addHeader(String page){
        LinearLayout row=horizontal();
        pad(row,18,10,14,7);
        LinearLayout titles=vertical();

        if(page.equals("Program")){
            TextView p=tv("PROGRAM",28,NAVY,true);
            p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
            titles.addView(p);
        } else {
            titles.addView(tv("Stagiune Filarmonica Transilvania",11.5f,MUTED,false));
            TextView p=tv(page,27,NAVY,true);
            p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD));
            titles.addView(p);
        }
        row.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView sync=tv("↻ 09:00",11.5f,MUTED,false);
        pad(sync,8,8,8,8);
        row.addView(sync);
        content.addView(row);
    }

    private void showMessageCard(String title,String text){
        LinearLayout c=vertical();
        c.setBackground(strokeBg(Color.WHITE,14,1,BORDER));
        pad(c,20,24,20,24);
        TextView a=tv(title,17,NAVY,true); a.setGravity(Gravity.CENTER);
        TextView b=tv(text,13,MUTED,false); b.setGravity(Gravity.CENTER); pad(b,0,8,0,0);
        c.addView(a); c.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(14),dp(4),dp(14),0);
        content.addView(c,p);
    }

    public void showProgram(boolean tmc, String targetId){
        this.tmcSelected = tmc;
        this.targetEventId = targetId;
        this.targetView = null;

        shell("Program");
        addHeader("Program");

        if(program == null){
            showMessageCard("Se încarcă programul…","Citesc stagiunea de pe NAS.");
            return;
        }

        JSONArray months=program.optJSONArray("months");
        if(months==null || months.length()==0){
            showMessageCard("Program indisponibil","Backend-ul nu a returnat luni de stagiune.");
            return;
        }
        if(selectedMonthIndex<0 || selectedMonthIndex>=months.length()) selectedMonthIndex=0;

        addMonthRow();
        addSegments(tmcSelected);

        JSONObject month=months.optJSONObject(selectedMonthIndex);
        if(month==null) return;
        if(tmcSelected) addTmcEvents(month);
        else addOrchestraWeeks(month);

        Space s=new Space(this);
        content.addView(s,new LinearLayout.LayoutParams(1,dp(12)));

        if(targetView!=null){
            View v=targetView;
            scrollView.postDelayed(()->scrollView.smoothScrollTo(0,Math.max(0,v.getTop()-dp(10))),120);
            targetEventId=null;
        }
    }

    private void addMonthRow(){
        JSONObject month=getSelectedMonth();
        String label=month==null ? "STAGIUNE" : month.optString("label","STAGIUNE");

        LinearLayout box=horizontal();
        box.setBackground(strokeBg(Color.WHITE,12,1,BORDER));
        pad(box,14,9,9,9);

        TextView monthButton=tv(label+"  ▾",15.5f,NAVY,true);
        monthButton.setGravity(Gravity.CENTER_VERTICAL);
        monthButton.setOnClickListener(v->showMonthPicker());
        box.addView(monthButton,new LinearLayout.LayoutParams(0,dp(38),1));

        TextView refresh=tv("Actualizează acum",10.5f,Color.WHITE,true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setBackground(bg(BLUE,10));
        pad(refresh,10,8,10,8);
        refresh.setOnClickListener(v->manualRefresh(refresh));
        box.addView(refresh);

        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(dp(14),dp(2),dp(14),dp(8));
        content.addView(box,p);
    }

    private void showMonthPicker(){
        JSONArray months=program==null?null:program.optJSONArray("months");
        if(months==null) return;
        String[] labels=new String[months.length()];
        for(int i=0;i<months.length();i++){
            JSONObject m=months.optJSONObject(i);
            labels[i]=m==null?("Luna "+(i+1)):m.optString("label","");
        }
        new AlertDialog.Builder(this)
                .setTitle("Alege luna")
                .setSingleChoiceItems(labels,selectedMonthIndex,(d,which)->{
                    selectedMonthIndex=which;
                    targetEventId=null;
                    d.dismiss();
                    showProgram(tmcSelected,null);
                })
                .setNegativeButton("Închide",null)
                .show();
    }

    private void addSegments(boolean tmc){
        LinearLayout seg=horizontal();
        pad(seg,14,0,14,9);
        TextView orch=chip("Orchestră",!tmc);
        TextView rec=chip("TMC & Recitaluri",tmc);
        orch.setOnClickListener(v->showProgram(false,null));
        rec.setOnClickListener(v->showProgram(true,null));
        seg.addView(orch,new LinearLayout.LayoutParams(0,dp(37),1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(37),1);
        p.setMargins(dp(8),0,0,0);
        seg.addView(rec,p);
        content.addView(seg);
    }

    private TextView chip(String s,boolean on){
        TextView t=tv(s,12.5f,on?Color.WHITE:NAVY,on);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(on?BLUE:Color.rgb(239,244,251),20));
        return t;
    }

    private JSONObject getSelectedMonth(){
        if(program==null) return null;
        JSONArray months=program.optJSONArray("months");
        if(months==null || selectedMonthIndex<0 || selectedMonthIndex>=months.length()) return null;
        return months.optJSONObject(selectedMonthIndex);
    }

    private void addOrchestraWeeks(JSONObject month){
        JSONArray weeks=month.optJSONArray("weeks");
        if(weeks==null || weeks.length()==0){
            showMessageCard("Niciun eveniment","Nu există evenimente orchestrale pentru luna selectată.");
            return;
        }

        boolean openedAny=false;
        LocalDate today=LocalDate.now();
        for(int i=0;i<weeks.length();i++){
            JSONObject week=weeks.optJSONObject(i);
            if(week==null) continue;
            boolean containsTarget=weekContainsEvent(week,targetEventId);
            boolean current=weekContainsDate(week,today);
            boolean open=containsTarget || current;
            if(!open && targetEventId==null && !openedAny && i==0 && !isSelectedCurrentMonth()) open=true;
            if(open) openedAny=true;
            addWeek(week,open);
        }
    }

    private boolean isSelectedCurrentMonth(){
        JSONObject m=getSelectedMonth();
        if(m==null) return false;
        LocalDate n=LocalDate.now();
        return m.optInt("month",-1)==n.getMonthValue() && m.optInt("year",-1)==n.getYear();
    }

    private boolean weekContainsDate(JSONObject week,LocalDate d){
        try{
            String a=week.optString("start","");
            String b=week.optString("end","");
            if(a.isEmpty()||b.isEmpty()) return false;
            LocalDate x=LocalDate.parse(a), y=LocalDate.parse(b);
            return !d.isBefore(x) && !d.isAfter(y);
        }catch(Exception e){ return false; }
    }

    private boolean weekContainsEvent(JSONObject week,String id){
        if(id==null) return false;
        JSONArray ev=week.optJSONArray("events");
        if(ev==null) return false;
        for(int i=0;i<ev.length();i++){
            JSONObject e=ev.optJSONObject(i);
            if(e!=null && id.equals(e.optString("id"))) return true;
        }
        return false;
    }

    private void addWeek(JSONObject week,boolean open){
        LinearLayout card=vertical();
        card.setBackground(strokeBg(Color.WHITE,13,1,BORDER));

        LinearLayout head=horizontal();
        head.setBackground(bg(PALE,13));
        pad(head,14,11,12,11);
        TextView title=tv(week.optString("collapsed_label","Săptămână"),14,NAVY,true);
        TextView arrow=tv(open?"⌃":"⌄",18,NAVY,true);
        head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        head.addView(arrow);
        card.addView(head);

        LinearLayout body=vertical();
        pad(body,15,11,15,11);
        body.setVisibility(open?View.VISIBLE:View.GONE);
        body.addView(tv("Săptămâna "+week.optString("label",""),11.5f,MUTED,true));

        JSONArray events=week.optJSONArray("events");
        if(events!=null){
            for(int i=0;i<events.length();i++){
                JSONObject e=events.optJSONObject(i);
                if(e==null) continue;
                if(i>0) body.addView(bigRule());
                renderOrchestraEvent(body,e);
                if(targetEventId!=null && targetEventId.equals(e.optString("id"))) targetView=card;
            }
        }

        card.addView(body);
        head.setOnClickListener(v->{
            boolean isOpen=body.getVisibility()==View.VISIBLE;
            body.setVisibility(isOpen?View.GONE:View.VISIBLE);
            arrow.setText(isOpen?"⌄":"⌃");
        });

        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.setMargins(dp(14),0,dp(14),dp(9));
        content.addView(card,cp);
    }

    private void renderOrchestraEvent(LinearLayout b,JSONObject e){
        TextView date=tv(eventDateLabel(e),15.5f,NAVY,true);
        pad(date,0,7,0,4);
        b.addView(date);

        String venue=e.optString("venue","");
        if(!venue.isEmpty()){
            TextView v=tv(venue,11.5f,MUTED,true);
            pad(v,0,0,0,4); b.addView(v);
        }

        JSONObject conductor=e.optJSONObject("conductor");
        if(conductor!=null){
            String name=conductor.optString("name","");
            if(!name.isEmpty()){
                if(conductor.optBoolean("choir",false)) name+=" + COR";
                b.addView(tv(name,14.5f,NAVY,false));
            }
            String reserve=conductor.optString("reserve","");
            if(!reserve.isEmpty()) b.addView(tv("Rezervă: "+reserve,11.5f,MUTED,false));
        }

        JSONArray soloists=e.optJSONArray("soloists");
        if(soloists!=null){
            for(int i=0;i<soloists.length();i++){
                JSONObject s=soloists.optJSONObject(i);
                if(s==null) continue;
                String line=s.optString("name","");
                String inst=s.optString("instrument","");
                String role=s.optString("role","");
                if(!inst.isEmpty()) line+=(line.isEmpty()?"":" — ")+inst;
                if(!role.isEmpty()) line+=" "+role;
                if(!line.isEmpty()) b.addView(tv(line,13.5f,NAVY,false));
            }
        }

        b.addView(rule());

        String status=e.optString("program_status","");
        JSONArray works=e.optJSONArray("works");
        if("uncompleted".equals(status)){
            TextView x=tv("Program necompletat",14,MUTED,false);
            pad(x,0,12,0,10); b.addView(x);
        }else if(works!=null && works.length()>0){
            for(int i=0;i<works.length();i++){
                JSONObject w=works.optJSONObject(i);
                if(w!=null) renderWork(b,w);
            }
        }else if("partial".equals(status)){
            TextView x=tv("Program necompletat",14,MUTED,false);
            pad(x,0,10,0,8); b.addView(x);
        }

        JSONArray notes=e.optJSONArray("program_notes");
        if(notes!=null && notes.length()>0){
            for(int i=0;i<notes.length();i++){
                String n=notes.optString(i,"");
                if(!n.isEmpty()){
                    TextView x=tv(n,12.2f,MUTED,false);
                    pad(x,0,4,0,2); b.addView(x);
                }
            }
        }

        renderUnattachedStrings(b,e.optJSONArray("string_distributions"));
        renderRehearsals(b,e.optJSONArray("rehearsals"));
        addRaw(b,e);
    }

    private void renderWork(LinearLayout b,JSONObject w){
        String composer=w.optString("composer","");
        String title=w.optString("title","");
        String heading=composer.isEmpty()?title:(composer+" — "+title);
        TextView t=tv(heading,14.2f,NAVY,true);
        pad(t,0,8,0,3);
        b.addView(t);

        JSONArray orch=w.optJSONArray("orchestrations");
        if(orch!=null && orch.length()>0){
            for(int i=0;i<orch.length();i++){
                JSONObject o=orch.optJSONObject(i);
                if(o==null) continue;
                String label=o.optString("label","");
                if(!label.isEmpty()) b.addView(tv(label,11.3f,MUTED,true));
                String disp=o.optString("display","");
                if(!disp.isEmpty()) b.addView(tv(disp,12.2f,MUTED,false));
            }
        } else {
            TextView d=tv("Distribuție necompletată",11.8f,MUTED,false);
            b.addView(d);
        }

        JSONArray details=w.optJSONArray("details");
        if(details!=null){
            for(int i=0;i<details.length();i++){
                String x=details.optString(i,"");
                if(!x.isEmpty()) b.addView(tv(x,11.8f,MUTED,false));
            }
        }

        JSONObject sd=w.optJSONObject("string_distribution");
        if(sd!=null){
            String display=sd.optString("display","");
            if(!display.isEmpty()){
                TextView s=tv(display,10.8f,MUTED,false);
                pad(s,0,4,0,1);
                b.addView(s);
            }
        }
        b.addView(rule());
    }

    private void renderUnattachedStrings(LinearLayout b,JSONArray arr){
        if(arr==null) return;
        boolean titleAdded=false;
        for(int i=0;i<arr.length();i++){
            JSONObject s=arr.optJSONObject(i);
            if(s==null) continue;
            String applies=s.optString("applies_to","");
            if(!applies.isEmpty()) continue;
            String display=s.optString("display","");
            if(display.isEmpty()) continue;
            if(!titleAdded){
                TextView rt=tv("CORDARI",12.5f,NAVY,true);
                rt.setBackground(bg(PALE,8));
                pad(rt,10,7,10,7);
                b.addView(rt);
                titleAdded=true;
            }
            TextView x=tv(display,11.4f,MUTED,false);
            pad(x,3,5,3,2); b.addView(x);
        }
    }

    private void renderRehearsals(LinearLayout b,JSONArray reps){
        if(reps==null || reps.length()==0) return;
        TextView rt=tv("REPETIȚII",13.5f,NAVY,true);
        rt.setBackground(bg(PALE,8));
        pad(rt,10,7,10,7);
        b.addView(rt);

        for(int i=0;i<reps.length();i++){
            JSONObject r=reps.optJSONObject(i);
            if(r==null) continue;
            String day=r.optString("day","");
            String ann=r.optString("annotation","");
            if(!ann.isEmpty()) day+=(day.isEmpty()?"":" ")+ann;
            JSONArray lines=r.optJSONArray("lines");

            if(lines==null || lines.length()==0){
                if(!day.isEmpty()) rehearsalRow(b,day,"");
                continue;
            }
            for(int j=0;j<lines.length();j++){
                JSONObject line=lines.optJSONObject(j);
                String display=line==null?"":line.optString("display","");
                rehearsalRow(b,j==0?day:"",display);
            }
        }
    }

    private void rehearsalRow(LinearLayout b,String day,String text){
        LinearLayout r=horizontal();
        r.setGravity(Gravity.TOP);
        pad(r,3,5,3,5);
        TextView d=tv(day,12,NAVY,true);
        TextView x=tv(text,12,MUTED,false);
        r.addView(d,new LinearLayout.LayoutParams(dp(92),-2));
        r.addView(x,new LinearLayout.LayoutParams(0,-2,1));
        b.addView(r);
    }

    private void addRaw(LinearLayout b,JSONObject e){
        TextView raw=tv("⋮  Vezi textul original",10,MUTED,false);
        raw.setGravity(Gravity.END);
        pad(raw,0,8,0,1);
        raw.setOnClickListener(v->showRawDialog(e));
        b.addView(raw);
    }

    private void showRawDialog(JSONObject e){
        JSONObject raw=e.optJSONObject("raw");
        String text=raw==null?"":raw.optString("program","");
        if(text.isEmpty()) text="Nu există text de program în document.";
        final String finalText=text;
        new AlertDialog.Builder(this)
                .setTitle("Textul original din Google Docs")
                .setMessage(finalText)
                .setPositiveButton("Închide",null)
                .show();
    }

    private void addTmcEvents(JSONObject month){
        JSONArray arr=month.optJSONArray("tmc_recitals");
        if(arr==null || arr.length()==0){
            showMessageCard("Niciun eveniment TMC","Nu există TMC sau recitaluri în luna selectată.");
            return;
        }
        for(int i=0;i<arr.length();i++){
            JSONObject e=arr.optJSONObject(i);
            if(e==null) continue;
            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(218,229,244)));
            pad(c,14,11,14,11);

            c.addView(tv(eventDateLabel(e),12,MUTED,true));
            String venue=e.optString("venue","");
            if(!venue.isEmpty()) c.addView(tv(venue,11.5f,MUTED,false));

            JSONObject conductor=e.optJSONObject("conductor");
            if(conductor!=null){
                String name=conductor.optString("name","");
                if(!name.isEmpty()){
                    if(conductor.optBoolean("choir",false)) name+=" + COR";
                    c.addView(tv(name,14,NAVY,true));
                }
            }

            JSONArray soloists=e.optJSONArray("soloists");
            if(soloists!=null){
                for(int j=0;j<soloists.length();j++){
                    JSONObject s=soloists.optJSONObject(j);
                    if(s==null) continue;
                    String line=s.optString("raw","");
                    if(!line.isEmpty()) c.addView(tv(line,12.5f,NAVY,false));
                }
            }

            JSONArray works=e.optJSONArray("works");
            if(works!=null && works.length()>0){
                c.addView(rule());
                for(int j=0;j<works.length();j++){
                    JSONObject w=works.optJSONObject(j);
                    if(w==null) continue;
                    String comp=w.optString("composer","");
                    String title=w.optString("title","");
                    String x=comp.isEmpty()?title:(comp+" — "+title);
                    if(!x.isEmpty()){
                        TextView wt=tv(x,13.5f,NAVY,true);
                        pad(wt,0,6,0,2); c.addView(wt);
                    }
                }
            } else {
                JSONObject raw=e.optJSONObject("raw");
                String p=raw==null?"":raw.optString("program","");
                if(!p.isEmpty()){
                    TextView pt=tv(p,13.5f,NAVY,true);
                    pad(pt,0,6,0,2); c.addView(pt);
                }
            }

            TextView raw=tv("⋮  Vezi textul original",10,MUTED,false);
            raw.setGravity(Gravity.END); pad(raw,0,8,0,1);
            raw.setOnClickListener(v->showRawDialog(e));
            c.addView(raw);

            if(targetEventId!=null && targetEventId.equals(e.optString("id"))) targetView=c;

            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            content.addView(c,p);
        }
    }

    private String eventDateLabel(JSONObject e){
        JSONArray concerts=e.optJSONArray("concerts");
        StringBuilder out=new StringBuilder();
        if(concerts!=null){
            for(int i=0;i<concerts.length();i++){
                JSONObject c=concerts.optJSONObject(i);
                if(c==null) continue;
                String iso=c.optString("date","");
                if(iso.isEmpty()) continue;
                if(out.length()>0) out.append("\n");
                out.append(roDate(iso));
                String time=c.optString("time","");
                if(!time.isEmpty()) out.append(" • ").append(time);
            }
        }
        if(out.length()>0) return out.toString();

        JSONObject raw=e.optJSONObject("raw");
        String rawDate=raw==null?"":raw.optString("date","");
        String first=rawDate.replace("\n"," / ");
        String month=e.optString("month_name","");
        if(!month.isEmpty() && !first.toLowerCase(Locale.ROOT).contains(month.toLowerCase(Locale.ROOT))) first+=" "+month;
        return first;
    }

    private String roDate(String iso){
        try{
            LocalDate d=LocalDate.parse(iso);
            String[] days={"","Luni","Marți","Miercuri","Joi","Vineri","Sâmbătă","Duminică"};
            String[] months={"","ianuarie","februarie","martie","aprilie","mai","iunie","iulie","august","septembrie","octombrie","noiembrie","decembrie"};
            return days[d.getDayOfWeek().getValue()]+", "+d.getDayOfMonth()+" "+months[d.getMonthValue()];
        }catch(Exception ex){ return iso; }
    }

    private View rule(){
        View r=new View(this);
        r.setBackgroundColor(Color.rgb(226,233,243));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));
        p.setMargins(0,dp(8),0,0);
        r.setLayoutParams(p);
        return r;
    }

    private View bigRule(){
        View r=new View(this);
        r.setBackgroundColor(Color.rgb(205,219,238));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(2));
        p.setMargins(0,dp(14),0,dp(8));
        r.setLayoutParams(p);
        return r;
    }

    public void showNews(){
        shell("Noutăți");
        addHeader("Noutăți");
        showMessageCard("Se încarcă…","Citesc istoricul modificărilor.");
        new Thread(()->{
            try{
                JSONObject j=getJson("/api/news?limit=200");
                runOnUiThread(()->renderNews(j));
            }catch(Exception e){
                runOnUiThread(()->renderErrorPage("Noutăți","Nu pot citi istoricul de pe NAS.\n"+shortError(e)));
            }
        }).start();
    }

    private void renderNews(JSONObject j){
        shell("Noutăți");
        addHeader("Noutăți");
        JSONArray items=j.optJSONArray("items");
        if(items==null || items.length()==0){
            showMessageCard("Nicio modificare detectată încă",
                    "Starea actuală este baseline. Modificările viitoare vor apărea aici, cele mai noi primele.");
            return;
        }
        for(int i=0;i<items.length();i++){
            JSONObject n=items.optJSONObject(i);
            if(n==null) continue;
            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,13,1,BORDER));
            pad(c,14,11,14,11);

            c.addView(tv("Program modificat",14.5f,NAVY,true));
            c.addView(tv(n.optString("week_label","Eveniment"),12.5f,MUTED,true));

            JSONArray changes=n.optJSONArray("changes");
            if(changes!=null){
                for(int k=0;k<changes.length();k++){
                    JSONObject ch=changes.optJSONObject(k);
                    if(ch==null) continue;
                    String label=ch.optString("label","Modificare");
                    TextView x=tv("• "+label,12.2f,MUTED,false);
                    pad(x,0,5,0,0); c.addView(x);
                }
            }

            String dt=n.optString("detected_at","");
            String source=n.optString("source","");
            TextView meta=tv(formatDetectedAt(dt)+(source.equals("manual")?" • actualizare manuală":""),10.5f,MUTED,false);
            pad(meta,0,8,0,0); c.addView(meta);

            String weekKey=n.optString("week_key","");
            if(!weekKey.isEmpty()) c.setOnClickListener(v->openWeekFromNews(n));

            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            content.addView(c,p);
        }
    }

    private void openWeekFromNews(JSONObject n){
        String wk=n.optString("week_key","");
        if(wk.isEmpty() || program==null) return;
        JSONArray months=program.optJSONArray("months");
        if(months==null) return;
        for(int i=0;i<months.length();i++){
            JSONObject m=months.optJSONObject(i);
            if(m==null) continue;
            JSONArray weeks=m.optJSONArray("weeks");
            if(weeks==null) continue;
            for(int j=0;j<weeks.length();j++){
                JSONObject w=weeks.optJSONObject(j);
                if(w!=null && wk.equals(w.optString("key"))){
                    selectedMonthIndex=i;
                    JSONArray ev=w.optJSONArray("events");
                    String id=(ev!=null && ev.length()>0 && ev.optJSONObject(0)!=null)?ev.optJSONObject(0).optString("id"):null;
                    showProgram(false,id);
                    return;
                }
            }
        }
    }

    private String formatDetectedAt(String iso){
        if(iso==null || iso.isEmpty()) return "";
        return iso.replace("T"," ").replaceAll("\\.\\d+.*$","").replace("+00:00"," UTC").replace("Z"," UTC");
    }

    public void showSearch(){
        shell("Caută");
        addHeader("Caută");

        EditText q=new EditText(this);
        q.setHint("Dirijor, solist, instrument, compozitor, lucrare…");
        q.setTextSize(15);
        q.setSingleLine(true);
        q.setBackground(strokeBg(Color.WHITE,12,1,BORDER));
        pad(q,14,10,14,10);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(48));
        qp.setMargins(dp(14),dp(3),dp(14),dp(10));
        content.addView(q,qp);

        LinearLayout results=vertical();
        content.addView(results,new LinearLayout.LayoutParams(-1,-2));

        TextView hint=tv("Căutarea verifică întreaga stagiune.",12.5f,MUTED,false);
        hint.setGravity(Gravity.CENTER);
        pad(hint,14,12,14,0);
        results.addView(hint);

        q.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){
                if(searchRunnable!=null) handler.removeCallbacks(searchRunnable);
                String query=s.toString().trim();
                if(query.length()<2){
                    results.removeAllViews();
                    TextView h=tv("Scrie cel puțin 2 caractere.",12.5f,MUTED,false);
                    h.setGravity(Gravity.CENTER); pad(h,14,12,14,0); results.addView(h);
                    return;
                }
                searchRunnable=()->performSearch(query,results);
                handler.postDelayed(searchRunnable,350);
            }
            public void afterTextChanged(Editable e){}
        });
        q.requestFocus();
    }

    private void performSearch(String query,LinearLayout results){
        results.removeAllViews();
        TextView loading=tv("Caut…",12.5f,MUTED,false);
        loading.setGravity(Gravity.CENTER); pad(loading,14,12,14,0); results.addView(loading);

        new Thread(()->{
            try{
                String enc=URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
                JSONObject j=getJson("/api/search?q="+enc+"&limit=100");
                runOnUiThread(()->renderSearchResults(j,results));
            }catch(Exception e){
                runOnUiThread(()->{
                    results.removeAllViews();
                    TextView er=tv("Căutarea nu este disponibilă: "+shortError(e),12.5f,MUTED,false);
                    er.setGravity(Gravity.CENTER); pad(er,14,12,14,0); results.addView(er);
                });
            }
        }).start();
    }

    private void renderSearchResults(JSONObject j,LinearLayout results){
        results.removeAllViews();
        JSONArray items=j.optJSONArray("items");
        if(items==null || items.length()==0){
            TextView empty=tv("Niciun rezultat.",13,MUTED,false);
            empty.setGravity(Gravity.CENTER); pad(empty,14,16,14,0); results.addView(empty);
            return;
        }

        for(int i=0;i<items.length();i++){
            JSONObject item=items.optJSONObject(i);
            if(item==null) continue;
            LinearLayout c=vertical();
            c.setBackground(strokeBg(Color.WHITE,12,1,BORDER));
            pad(c,14,10,14,10);

            String date=item.optString("date_label","").replace("\n"," / ");
            c.addView(tv(date,11.5f,MUTED,true));

            StringBuilder main=new StringBuilder();
            JSONArray works=item.optJSONArray("works");
            if(works!=null){
                for(int k=0;k<works.length();k++){
                    JSONObject w=works.optJSONObject(k);
                    if(w==null) continue;
                    String comp=w.optString("composer","");
                    String title=w.optString("title","");
                    String x=comp.isEmpty()?title:(comp+" — "+title);
                    if(x.isEmpty()) continue;
                    if(main.length()>0) main.append("\n");
                    main.append(x);
                }
            }
            if(main.length()==0){
                String cond=item.optString("conductor","");
                if(!cond.isEmpty()) main.append(cond);
                else main.append("Eveniment");
            }
            c.addView(tv(main.toString(),14,NAVY,true));

            String cond=item.optString("conductor","");
            if(!cond.isEmpty()) c.addView(tv(cond,12.2f,MUTED,false));
            JSONArray sols=item.optJSONArray("soloists");
            if(sols!=null && sols.length()>0){
                StringBuilder ss=new StringBuilder();
                for(int k=0;k<sols.length();k++){
                    String x=sols.optString(k,"");
                    if(!x.isEmpty()){
                        if(ss.length()>0) ss.append(" • ");
                        ss.append(x);
                    }
                }
                if(ss.length()>0) c.addView(tv(ss.toString(),11.8f,MUTED,false));
            }

            c.setOnClickListener(v->openSearchItem(item));

            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(dp(14),0,dp(14),dp(9));
            results.addView(c,p);
        }
    }

    private void openSearchItem(JSONObject item){
        int month=item.optInt("month",-1);
        int year=item.optInt("year",-1);
        String id=item.optString("event_id","");
        String type=item.optString("type","orchestra");
        JSONArray months=program==null?null:program.optJSONArray("months");
        if(months!=null){
            for(int i=0;i<months.length();i++){
                JSONObject m=months.optJSONObject(i);
                if(m!=null && m.optInt("month")==month && m.optInt("year")==year){
                    selectedMonthIndex=i; break;
                }
            }
        }
        showProgram("tmc".equals(type),id);
    }

    private void manualRefresh(TextView button){
        button.setEnabled(false);
        button.setAlpha(.6f);
        Toast.makeText(this,"Verific modificările în Google Docs…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                JSONObject result=getJson("/api/refresh");
                JSONObject p=getJson("/api/program");
                program=p;
                cacheProgram(p);
                int changes=result.optInt("change_count",0);
                runOnUiThread(()->{
                    button.setEnabled(true); button.setAlpha(1f);
                    showProgram(tmcSelected,null);
                    Toast.makeText(this,changes==0?"Programul este la zi.":("Actualizat: "+changes+" modificări."),Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    button.setEnabled(true); button.setAlpha(1f);
                    Toast.makeText(this,"Actualizarea a eșuat: "+shortError(e),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void loadProgram(boolean forceUi){
        new Thread(()->{
            try{
                JSONObject p=getJson("/api/program");
                program=p;
                cacheProgram(p);
                runOnUiThread(()->{
                    JSONArray months=p.optJSONArray("months");
                    if(selectedMonthIndex<0 || months==null || selectedMonthIndex>=months.length()) chooseInitialMonth();
                    if(forceUi || content!=null) showProgram(tmcSelected,targetEventId);
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    if(program==null) renderErrorPage("Program","Nu mă pot conecta la NAS.\n"+shortError(e)+"\n\nTelefonul trebuie să poată ajunge la 192.168.0.191.");
                    else Toast.makeText(this,"Folosesc ultima copie salvată. NAS indisponibil.",Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void renderErrorPage(String page,String text){
        shell(page);
        addHeader(page);
        showMessageCard("Conexiune indisponibilă",text);
        TextView retry=tv("Reîncearcă",13,Color.WHITE,true);
        retry.setGravity(Gravity.CENTER);
        retry.setBackground(bg(BLUE,10));
        pad(retry,14,10,14,10);
        retry.setOnClickListener(v->{ showProgram(tmcSelected,null); loadProgram(true); });
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));
        p.setMargins(dp(50),dp(14),dp(50),0);
        content.addView(retry,p);
    }

    private JSONObject getJson(String path) throws Exception{
        URL url=new URL(API_BASE+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(6000);
        c.setReadTimeout(20000);
        c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode();
        BufferedReader r=new BufferedReader(new InputStreamReader(
                code>=200 && code<300 ? c.getInputStream() : c.getErrorStream(),
                StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=r.readLine())!=null) sb.append(line);
        r.close(); c.disconnect();
        if(code<200 || code>=300) throw new Exception("HTTP "+code);
        return new JSONObject(sb.toString());
    }

    private void cacheProgram(JSONObject p){
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(CACHE_PROGRAM,p.toString()).apply();
    }

    private void chooseInitialMonth(){
        if(program==null) return;
        JSONArray months=program.optJSONArray("months");
        if(months==null || months.length()==0) return;
        LocalDate now=LocalDate.now();
        for(int i=0;i<months.length();i++){
            JSONObject m=months.optJSONObject(i);
            if(m!=null && m.optInt("month")==now.getMonthValue() && m.optInt("year")==now.getYear()){
                selectedMonthIndex=i; return;
            }
        }
        selectedMonthIndex=0;
    }

    private String shortError(Exception e){
        String s=e.getMessage();
        if(s==null || s.trim().isEmpty()) s=e.getClass().getSimpleName();
        if(s.length()>100) s=s.substring(0,100);
        return s;
    }
}
