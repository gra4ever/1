package ro.filarmonica.transilvania.stagiune;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.*;
import java.text.Normalizer;
import java.util.*;

public class MainActivity extends Activity {
    private final int NAVY = Color.rgb(18,48,94);
    private final int BLUE = Color.rgb(29,102,209);
    private final int PALE = Color.rgb(233,243,255);
    private final int BG = Color.rgb(248,250,253);
    private final int MUTED = Color.rgb(89,108,137);
    private LinearLayout content, bottom;

    private final List<SearchItem> searchItems = Arrays.asList(
        new SearchItem("6 nov 2026","Bartók — Concertul pentru orchestră","Lawrence Foster • Jenő Koppándi — vioară","bartok concertul pentru orchestra lawrence foster jeno koppandi vioara"),
        new SearchItem("13 nov 2026","Mozart — Requiem","Toby Thatcher + COR • Nardus Williams — soprană","mozart requiem toby thatcher cor nardus williams soprana"),
        new SearchItem("27 nov 2026","Valentin Gheorghiu — Concertul pentru pian și orchestră","Gabriel Bebeșelea • Oliver Triendl — pian","valentin gheorghiu concert pian gabriel bebeslea oliver triendl")
    );

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        showProgram(false);
    }

    private int dp(int v){ return (int)(v*getResources().getDisplayMetrics().density+.5f); }
    private TextView tv(String s,float sp,int color,boolean bold){
        TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL)); t.setIncludeFontPadding(false); t.setLineSpacing(0,1.04f); return t;
    }
    private GradientDrawable bg(int c,int r){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp(r)); return g; }
    private GradientDrawable strokeBg(int c,int r,int sw,int sc){ GradientDrawable g=bg(c,r); g.setStroke(dp(sw),sc); return g; }
    private void pad(View v,int l,int t,int r,int b){ v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private void shell(String selected){
        LinearLayout root=vertical(); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            v.setPadding(0,insets.getSystemWindowInsetTop(),0,insets.getSystemWindowInsetBottom());
            return insets;
        });
        content=vertical();
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        bottom=horizontal(); bottom.setBackgroundColor(Color.WHITE); pad(bottom,10,5,10,5);
        addNav("▣\nProgram","Program".equals(selected),()->showProgram(false));
        addNav("▤\nNoutăți","Noutăți".equals(selected),this::showNews);
        addNav("⌕\nCaută","Caută".equals(selected),this::showSearch);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(60)));
        setContentView(root); root.requestApplyInsets();
    }
    private void addNav(String label,boolean selected,Runnable action){
        TextView b=tv(label,12,selected?BLUE:MUTED,selected); b.setGravity(Gravity.CENTER); b.setOnClickListener(v->action.run()); bottom.addView(b,new LinearLayout.LayoutParams(0,-1,1));
    }
    private void addHeader(String page){
        LinearLayout row=horizontal(); pad(row,18,10,14,7); LinearLayout titles=vertical();
        if(page.equals("Program")){ TextView p=tv("PROGRAM",28,NAVY,true); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); titles.addView(p); }
        else { titles.addView(tv("Stagiune Filarmonica Transilvania",11.5f,MUTED,false)); TextView p=tv(page,27,NAVY,true); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); titles.addView(p); }
        row.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); TextView sync=tv("↻ 09:00",11.5f,MUTED,false); pad(sync,8,8,8,8); row.addView(sync); content.addView(row);
    }

    public void showProgram(boolean tmcSelected){
        shell("Program"); addHeader("Program"); addMonthRow(); addSegments(tmcSelected);
        if(tmcSelected) addTmcEvents(); else addOrchestraWeeks();
        Space s=new Space(this); content.addView(s,new LinearLayout.LayoutParams(1,dp(12)));
    }
    private void addMonthRow(){
        LinearLayout month=horizontal(); month.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(211,224,242))); pad(month,14,9,9,9);
        month.addView(tv("NOIEMBRIE 2026  ▾",15.5f,NAVY,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=tv("Actualizează acum",10.5f,Color.WHITE,true); refresh.setBackground(bg(BLUE,10)); pad(refresh,10,8,10,8);
        refresh.setOnClickListener(v->Toast.makeText(this,"Actualizare manuală — backend-ul mini-PC urmează să fie conectat.",Toast.LENGTH_SHORT).show()); month.addView(refresh);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),dp(2),dp(14),dp(8)); content.addView(month,p);
    }
    private void addSegments(boolean tmcSelected){
        LinearLayout seg=horizontal(); pad(seg,14,0,14,9);
        TextView orch=chip("Orchestră",!tmcSelected); TextView tmc=chip("TMC & Recitaluri",tmcSelected);
        orch.setOnClickListener(v->showProgram(false)); tmc.setOnClickListener(v->showProgram(true));
        seg.addView(orch,new LinearLayout.LayoutParams(0,dp(37),1)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(37),1); p.setMargins(dp(8),0,0,0); seg.addView(tmc,p); content.addView(seg);
    }
    private TextView chip(String s,boolean on){ TextView t=tv(s,12.5f,on?Color.WHITE:NAVY,on); t.setGravity(Gravity.CENTER); t.setBackground(bg(on?BLUE:Color.rgb(239,244,251),20)); return t; }

    private void addOrchestraWeeks(){
        addWeek("8 noiembrie","Săptămâna 2–8 noiembrie",true,0);
        addWeek("15 noiembrie","Săptămâna 9–15 noiembrie",false,1);
        addWeek("22 noiembrie","Săptămâna 16–22 noiembrie",false,2);
        addWeek("29 noiembrie","Săptămâna 23–29 noiembrie",false,3);
    }
    private void addWeek(String headerDate,String weekLabel,boolean open,int kind){
        LinearLayout card=vertical(); card.setBackground(strokeBg(Color.WHITE,13,1,Color.rgb(211,224,242)));
        LinearLayout head=horizontal(); head.setBackground(bg(PALE,13)); pad(head,14,11,12,11);
        TextView title=tv(headerDate,14,NAVY,true); TextView arrow=tv(open?"⌃":"⌄",18,NAVY,true); head.addView(title,new LinearLayout.LayoutParams(0,-2,1)); head.addView(arrow); card.addView(head);
        LinearLayout body=vertical(); pad(body,15,11,15,11); body.setVisibility(open?View.VISIBLE:View.GONE); body.addView(tv(weekLabel,11.5f,MUTED,true));
        if(kind==0) fillWeek6(body); else if(kind==1) fillWeek13(body); else if(kind==2) fillWeek20(body); else fillWeek27(body);
        card.addView(body);
        head.setOnClickListener(v->{ boolean isOpen=body.getVisibility()==View.VISIBLE; body.setVisibility(isOpen?View.GONE:View.VISIBLE); arrow.setText(isOpen?"⌄":"⌃"); });
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(dp(14),0,dp(14),dp(9)); content.addView(card,cp);
    }
    private void fillWeek6(LinearLayout b){
        b.addView(tv("Vineri, 6 noiembrie • 19:00",15.5f,NAVY,true)); b.addView(tv("Lawrence Foster",14.5f,NAVY,false)); b.addView(tv("Jenő Koppándi — vioară",13.5f,NAVY,false)); b.addView(rule());
        work(b,"Kodály — Dansurile din Galánta","2* 2 2 2; 4 2 0 0; T; P; Archi;","Vioara I - 14 • Vioara II - 12 • Viola - 10 • Violoncel - 8 • Contrabas - 6");
        work(b,"Dohnányi — Concertul pentru vioară nr. 2","3 2 2 2; 4 2 3 1; timp.perc.; Hp; Vla; Vc; Cb","Vioara I - 10 • Vioara II - 8 • Viola - 6");
        work(b,"Bartók — Concertul pentru orchestră","3* 3* 3* 3*; 4 3 3 1; T; P; 2Hp; Archi;",null);
        repetitionsTitle(b); rehearsal(b,"Luni","10:00–14:00"); rehearsal(b,"Marți","10:00–14:00"); rehearsal(b,"Miercuri","10:00–14:00 + solist"); rehearsal(b,"Joi","10:00–14:00 + solist"); rehearsal(b,"Vineri","10:00–13:00 • Concert 19:00"); addRaw(b);
    }
    private void fillWeek13(LinearLayout b){
        b.addView(tv("Vineri, 13 noiembrie • 19:00",15.5f,NAVY,true)); b.addView(tv("Toby Thatcher + COR",14.5f,NAVY,false)); b.addView(tv("Nardus Williams — soprană",13.5f,NAVY,false)); b.addView(rule());
        work(b,"Mozart — Requiem (Breitkopf)","0 0 2 2; 0 2 3 0; T; Archi;",null);
        repetitionsTitle(b); rehearsal(b,"Luni","10:00–14:00 • 17:00–20:00 cabina COR"); rehearsal(b,"Marți","10:00–14:00 + COR"); rehearsal(b,"Miercuri","ANULAT UBB • (cabina soliști 17:00–20:00?)"); rehearsal(b,"Joi","10:00–14:00 + soliști + COR"); rehearsal(b,"Vineri","10:00–13:00 + soliști + COR • Concert 19:00–21:00"); addRaw(b);
    }
    private void fillWeek20(LinearLayout b){ b.addView(tv("Vineri, 20 noiembrie • 19:00",15.5f,NAVY,true)); TextView x=tv("Program necompletat",14,MUTED,false); pad(x,0,12,0,10); b.addView(x); addRaw(b); }
    private void fillWeek27(LinearLayout b){ b.addView(tv("Vineri, 27 noiembrie • 19:00",15.5f,NAVY,true)); b.addView(tv("Gabriel Bebeșelea",14.5f,NAVY,false)); b.addView(tv("Oliver Triendl — pian",13.5f,NAVY,false)); b.addView(rule()); work(b,"Valentin Gheorghiu — Concertul pentru pian și orchestră","Distribuție necompletată",null); addRaw(b); }

    private void work(LinearLayout b,String title,String dist,String strings){
        TextView t=tv(title,14.2f,NAVY,true); pad(t,0,8,0,3); b.addView(t); b.addView(tv(dist,12.2f,MUTED,false));
        if(strings!=null){ TextView s=tv(strings,10.3f,MUTED,false); s.setSingleLine(true); s.setTextScaleX(.86f); pad(s,0,4,0,1); b.addView(s); }
        b.addView(rule());
    }
    private View rule(){ View r=new View(this); r.setBackgroundColor(Color.rgb(226,233,243)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1)); p.setMargins(0,dp(8),0,0); r.setLayoutParams(p); return r; }
    private void repetitionsTitle(LinearLayout b){ TextView rt=tv("REPETIȚII",13.5f,NAVY,true); rt.setBackground(bg(PALE,8)); pad(rt,10,7,10,7); b.addView(rt); }
    private void rehearsal(LinearLayout b,String day,String text){ LinearLayout r=horizontal(); pad(r,3,5,3,5); TextView d=tv(day,12,NAVY,true); TextView x=tv(text,12,MUTED,false); r.addView(d,new LinearLayout.LayoutParams(dp(88),-2)); r.addView(x,new LinearLayout.LayoutParams(0,-2,1)); b.addView(r); }
    private void addRaw(LinearLayout b){ TextView raw=tv("⋮  Vezi textul original",10,MUTED,false); raw.setGravity(Gravity.END); pad(raw,0,8,0,1); raw.setOnClickListener(v->Toast.makeText(this,"Textul original va veni din snapshot-ul mini-PC.",Toast.LENGTH_SHORT).show()); b.addView(raw); }

    private void addTmcEvents(){
        event("11 octombrie • 19:00","Recital de muzică românească","");
        event("12 octombrie • 19:00","Panoramic componistic clujean I","");
        event("18 octombrie • 17:00 • MOTOLAND","Classic unlimited","Bogdan Vaida — pian");
        event("18 octombrie • 19:00","Teodora Brody","solo voce");
        event("19 octombrie • 17:00","Panoramic componistic clujean II","");
    }
    private void event(String date,String name,String sub){ LinearLayout c=vertical(); c.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(218,229,244))); pad(c,14,11,14,11); c.addView(tv(date,11.5f,MUTED,false)); c.addView(tv(name,14.5f,NAVY,true)); if(!sub.isEmpty()) c.addView(tv(sub,12.5f,MUTED,false)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),0,dp(14),dp(9)); content.addView(c,p); }

    public void showNews(){
        shell("Noutăți"); addHeader("Noutăți"); LinearLayout empty=vertical(); empty.setGravity(Gravity.CENTER); empty.setBackground(strokeBg(Color.WHITE,14,1,Color.rgb(218,229,244))); pad(empty,22,28,22,28); empty.addView(tv("Nicio modificare detectată încă",17,NAVY,true)); TextView sub=tv("Istoricul începe după prima sincronizare cu documentul. Cele mai noi modificări vor apărea primele.",13,MUTED,false); sub.setGravity(Gravity.CENTER); pad(sub,0,9,0,0); empty.addView(sub); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),dp(4),dp(14),0); content.addView(empty,p);
    }

    public void showSearch(){
        shell("Caută"); addHeader("Caută"); EditText q=new EditText(this); q.setHint("Dirijor, solist, piesă, compozitor..."); q.setTextSize(15); q.setSingleLine(true); q.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(202,216,237))); pad(q,14,8,14,8); LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(50)); qp.setMargins(dp(14),0,dp(14),dp(10)); content.addView(q,qp); LinearLayout results=vertical(); content.addView(results,new LinearLayout.LayoutParams(-1,-2)); renderResults(results,""); q.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ renderResults(results,s.toString()); } public void afterTextChanged(Editable e){} });
    }
    private void renderResults(LinearLayout results,String query){ results.removeAllViews(); String nq=norm(query); int count=0; for(SearchItem item:searchItems){ if(nq.isEmpty()||norm(item.search).contains(nq)){ addResult(results,item); count++; } } TextView ct=tv(count+" rezultate",11.5f,MUTED,false); pad(ct,18,2,18,8); results.addView(ct,0); }
    private void addResult(LinearLayout parent,SearchItem item){ LinearLayout r=horizontal(); r.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(218,229,244))); pad(r,12,11,12,11); TextView d=tv(item.date,11.5f,NAVY,true); d.setGravity(Gravity.CENTER); r.addView(d,new LinearLayout.LayoutParams(dp(72),-1)); LinearLayout x=vertical(); x.addView(tv(item.name,13.5f,NAVY,true)); x.addView(tv(item.sub,11.5f,MUTED,false)); r.addView(x,new LinearLayout.LayoutParams(0,-2,1)); r.addView(tv("›",20,NAVY,false)); r.setOnClickListener(v->{ showProgram(false); Toast.makeText(this,"Săptămâna concertului a fost deschisă.",Toast.LENGTH_SHORT).show(); }); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),0,dp(14),dp(8)); parent.addView(r,p); }
    private String norm(String s){ String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+",""); return n.toLowerCase(Locale.ROOT); }
    private static class SearchItem{ final String date,name,sub,search; SearchItem(String d,String n,String s,String q){date=d;name=n;sub=s;search=q;} }
}
