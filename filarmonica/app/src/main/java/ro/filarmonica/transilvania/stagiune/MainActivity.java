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
import android.widget.*;
import java.text.Normalizer;
import java.util.*;

public class MainActivity extends Activity {
    private final int NAVY = Color.rgb(18, 48, 94);
    private final int BLUE = Color.rgb(29, 102, 209);
    private final int PALE = Color.rgb(233, 243, 255);
    private final int BG = Color.rgb(248, 250, 253);
    private final int MUTED = Color.rgb(89, 108, 137);
    private LinearLayout content;
    private LinearLayout bottom;
    private final List<SearchItem> searchItems = Arrays.asList(
        new SearchItem("6 nov 2026", "Bartók — Concertul pentru orchestră", "Lawrence Foster • Jenő Koppándi — vioară", "bartok concertul pentru orchestra lawrence foster jeno koppandi vioara"),
        new SearchItem("11 dec 2026", "Mahler — Simfonia nr. 5", "Gregory Vajda + COR • Tatiana Lisnic — soprană", "mahler simfonia 5 gregory vajda cor tatiana lisnic soprana"),
        new SearchItem("19 mar 2027", "Mahler — Simfonia nr. 2", "Gergely Madaras + COR", "mahler simfonia 2 gergely madaras cor"),
        new SearchItem("30 oct 2026", "Richard Strauss — Don Juan", "Lawrence Foster • Plamena Mangova — pian", "strauss don juan lawrence foster plamena mangova pian"),
        new SearchItem("23 apr 2027", "Brahms — Concertul pentru vioară", "Gabor Kali • Mariam Abouzahra — vioară", "brahms concertul pentru vioara gabor kali mariam abouzahra"),
        new SearchItem("16 apr 2027", "Beethoven — Concertul pentru vioară", "Gregory Vajda", "beethoven concertul pentru vioara gregory vajda"),
        new SearchItem("22 ian 2027", "Brahms — Dublul concert", "Peter Stark • Petre Abraham Smeu — vioară • Jan Sekaci — violoncel", "brahms dublul concert peter stark petre abraham smeu jan sekaci violoncel")
    );

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.WHITE);
        showProgram();
    }

    private int dp(int v){ return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private TextView tv(String s, float sp, int color, boolean bold){
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color);
        t.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        t.setLineSpacing(0,1.05f); t.setIncludeFontPadding(false); return t;
    }
    private GradientDrawable bg(int color, float radius){ GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp((int)radius)); return g; }
    private GradientDrawable strokeBg(int color, float radius, int stroke, int strokeColor){ GradientDrawable g=bg(color,radius); g.setStroke(dp(stroke),strokeColor); return g; }
    private void pad(View v,int l,int t,int r,int b){ v.setPadding(dp(l),dp(t),dp(r),dp(b)); }
    private LinearLayout vertical(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout horizontal(){ LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }

    private void shell(String selected){
        LinearLayout root=vertical(); root.setBackgroundColor(BG); content=vertical();
        ScrollView sc=new ScrollView(this); sc.setFillViewport(true); sc.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        bottom=horizontal(); bottom.setBackgroundColor(Color.WHITE); pad(bottom,12,7,12,8);
        addNav("▣\nProgram","Program".equals(selected),this::showProgram);
        addNav("▤\nNoutăți","Noutăți".equals(selected),this::showNews);
        addNav("⌕\nCaută","Caută".equals(selected),this::showSearch);
        root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(67))); setContentView(root);
    }
    private void addNav(String label, boolean selected, Runnable action){ TextView b=tv(label,12,selected?BLUE:MUTED,selected); b.setGravity(Gravity.CENTER); b.setOnClickListener(v->action.run()); bottom.addView(b,new LinearLayout.LayoutParams(0,-1,1)); }
    private void addHeader(String page){
        LinearLayout row=horizontal(); pad(row,18,16,14,7); LinearLayout titles=vertical();
        if(page.equals("Program")) { TextView p=tv("PROGRAM",29,NAVY,true); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); titles.addView(p); }
        else { TextView small=tv("Stagiune Filarmonica Transilvania",12,MUTED,false); titles.addView(small); TextView p=tv(page,29,NAVY,true); p.setTypeface(Typeface.create(Typeface.SERIF,Typeface.BOLD)); titles.addView(p); }
        row.addView(titles,new LinearLayout.LayoutParams(0,-2,1)); TextView sync=tv("↻ 09:00",12,MUTED,false); pad(sync,8,8,8,8); row.addView(sync); content.addView(row);
    }

    public void showProgram(){
        shell("Program"); addHeader("Program");
        LinearLayout month=horizontal(); month.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(211,224,242))); pad(month,14,10,10,10);
        TextView m=tv("NOIEMBRIE 2026  ▾",16,NAVY,true); month.addView(m,new LinearLayout.LayoutParams(0,-2,1));
        TextView refresh=tv("Actualizează acum",11,Color.WHITE,true); refresh.setBackground(bg(BLUE,10)); pad(refresh,11,8,11,8);
        refresh.setOnClickListener(v->Toast.makeText(this,"Versiunea APK este pregătită pentru sincronizarea prin mini-PC.",Toast.LENGTH_SHORT).show()); month.addView(refresh);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2); mp.setMargins(dp(14),dp(2),dp(14),dp(8)); content.addView(month,mp);

        LinearLayout seg=horizontal(); pad(seg,14,0,14,9); TextView orch=chip("Orchestră",true); TextView tmc=chip("TMC & Recitaluri",false);
        seg.addView(orch,new LinearLayout.LayoutParams(0,dp(37),1)); LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,dp(37),1); sp.setMargins(dp(8),0,0,0); seg.addView(tmc,sp); content.addView(seg);
        tmc.setOnClickListener(v->showTmc());
        addWeekOpen(); addWeekClosed("9–15 NOIEMBRIE","15 noiembrie"); addWeekClosed("16–22 NOIEMBRIE","22 noiembrie"); addWeekClosed("23–29 NOIEMBRIE","29 noiembrie");
        Space s=new Space(this); content.addView(s,new LinearLayout.LayoutParams(1,dp(16)));
    }
    private TextView chip(String s, boolean on){ TextView t=tv(s,13,on?Color.WHITE:NAVY,on); t.setGravity(Gravity.CENTER); t.setBackground(bg(on?BLUE:Color.rgb(239,244,251),20)); return t; }

    private void addWeekOpen(){
        LinearLayout card=vertical(); card.setBackground(strokeBg(Color.WHITE,14,1,Color.rgb(211,224,242)));
        LinearLayout head=horizontal(); head.setBackground(bg(PALE,14)); pad(head,14,11,14,11); TextView h=tv("SĂPTĂMÂNA 2–8 NOIEMBRIE",15,NAVY,true); head.addView(h,new LinearLayout.LayoutParams(0,-2,1)); head.addView(tv("⌃",18,NAVY,true)); card.addView(head);
        LinearLayout body=vertical(); pad(body,15,13,15,11); body.addView(tv("Vineri, 6 noiembrie • 19:00",16,NAVY,true)); body.addView(tv("Lawrence Foster",15,NAVY,false)); body.addView(tv("Jenő Koppándi — vioară",14,NAVY,false)); body.addView(rule());
        work(body,"Kodály — Dansurile din Galánta","2* 2 2 2; 4 2 0 0; T; P; Archi;","Vl I 14 • Vl II 12 • Vla 10 • Vc 8 • Cb 6");
        work(body,"Dohnányi — Concertul pentru vioară nr. 2","3 2 2 2; 4 2 3 1; timp.perc.; Hp; Vla; Vc; Cb","Vl I 10 • Vl II 8 • Vla 6");
        work(body,"Bartók — Concertul pentru orchestră","3* 3* 3* 3*; 4 3 3 1; T; P; 2Hp; Archi;",null);
        TextView rt=tv("REPETIȚII",14,NAVY,true); rt.setBackground(bg(PALE,8)); pad(rt,10,8,10,8); body.addView(rt);
        rehearsal(body,"Luni","10:00–14:00"); rehearsal(body,"Marți","10:00–14:00"); rehearsal(body,"Miercuri","10:00–14:00 + solist"); rehearsal(body,"Joi","10:00–14:00 + solist"); rehearsal(body,"Vineri","10:00–13:00 • Concert 19:00");
        TextView raw=tv("⋮  Vezi textul original",10.5f,MUTED,false); raw.setGravity(Gravity.END); pad(raw,0,9,0,1); raw.setOnClickListener(v->Toast.makeText(this,"Textul original va veni din snapshot-ul mini-PC.",Toast.LENGTH_SHORT).show()); body.addView(raw); card.addView(body);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2); cp.setMargins(dp(14),0,dp(14),dp(10)); content.addView(card,cp);
    }
    private void work(LinearLayout b,String title,String dist,String strings){ TextView t=tv(title,14.5f,NAVY,true); pad(t,0,9,0,3); b.addView(t); b.addView(tv(dist,12.5f,MUTED,false)); if(strings!=null){ TextView s=tv(strings,11.8f,MUTED,false); s.setSingleLine(true); s.setTextScaleX(0.94f); pad(s,0,4,0,1); b.addView(s); } b.addView(rule()); }
    private View rule(){ View r=new View(this); r.setBackgroundColor(Color.rgb(226,233,243)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1)); p.setMargins(0,dp(9),0,0); r.setLayoutParams(p); return r; }
    private void rehearsal(LinearLayout b,String day,String text){ LinearLayout r=horizontal(); pad(r,3,6,3,6); TextView d=tv(day,12.5f,NAVY,true); TextView x=tv(text,12.5f,MUTED,false); r.addView(d,new LinearLayout.LayoutParams(dp(93),-2)); r.addView(x,new LinearLayout.LayoutParams(0,-2,1)); b.addView(r); }
    private void addWeekClosed(String week,String date){ LinearLayout r=horizontal(); r.setBackground(strokeBg(PALE,12,1,Color.rgb(218,229,244))); pad(r,14,12,12,12); TextView w=tv(date,14,NAVY,true); r.addView(w,new LinearLayout.LayoutParams(0,-2,1)); r.addView(tv("›",20,NAVY,false)); r.setOnClickListener(v->Toast.makeText(this,"Săptămâna "+week,Toast.LENGTH_SHORT).show()); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),0,dp(14),dp(9)); content.addView(r,p); }

    private void showTmc(){
        shell("Program"); addHeader("Program"); TextView back=tv("‹ Orchestră",13,BLUE,true); pad(back,18,4,18,8); back.setOnClickListener(v->showProgram()); content.addView(back);
        TextView title=tv("TMC & Recitaluri",22,NAVY,true); pad(title,18,4,18,12); content.addView(title);
        event("11 octombrie • 19:00","Recital de muzică românească",""); event("12 octombrie • 19:00","Panoramic componistic clujean I",""); event("18 octombrie • 17:00 • MOTOLAND","Classic unlimited","Bogdan Vaida — pian"); event("18 octombrie • 19:00","Teodora Brody","solo voce"); event("19 octombrie • 17:00","Panoramic componistic clujean II","");
    }
    private void event(String date,String name,String sub){ LinearLayout c=vertical(); c.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(218,229,244))); pad(c,14,11,14,11); c.addView(tv(date,11.5f,MUTED,false)); c.addView(tv(name,14.5f,NAVY,true)); if(!sub.isEmpty()) c.addView(tv(sub,12.5f,MUTED,false)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(14),0,dp(14),dp(9));content.addView(c,p); }

    public void showNews(){
        shell("Noutăți"); addHeader("Noutăți");
        LinearLayout empty=vertical(); empty.setGravity(Gravity.CENTER); empty.setBackground(strokeBg(Color.WHITE,14,1,Color.rgb(218,229,244))); pad(empty,22,28,22,28);
        empty.addView(tv("Nicio modificare detectată încă",17,NAVY,true)); TextView sub=tv("Istoricul începe după prima sincronizare cu documentul. Cele mai noi modificări vor apărea primele.",13,MUTED,false); sub.setGravity(Gravity.CENTER); pad(sub,0,9,0,0); empty.addView(sub);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),dp(4),dp(14),0); content.addView(empty,p);
    }

    public void showSearch(){
        shell("Caută"); addHeader("Caută"); EditText q=new EditText(this); q.setHint("Dirijor, solist, piesă, compozitor..."); q.setTextSize(15); q.setSingleLine(true); q.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(202,216,237))); pad(q,14,8,14,8);
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(-1,dp(50));qp.setMargins(dp(14),0,dp(14),dp(10));content.addView(q,qp);
        LinearLayout results=vertical(); content.addView(results,new LinearLayout.LayoutParams(-1,-2)); renderResults(results,"");
        q.addTextChangedListener(new TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ renderResults(results,s.toString()); } public void afterTextChanged(Editable e){} });
    }
    private void renderResults(LinearLayout results,String query){ results.removeAllViews(); String nq=norm(query); int count=0; for(SearchItem item:searchItems){ if(nq.isEmpty() || norm(item.search).contains(nq)){ addResult(results,item); count++; } } TextView countTv=tv(count+" rezultate",11.5f,MUTED,false); pad(countTv,18,2,18,8); results.addView(countTv,0); }
    private void addResult(LinearLayout parent, SearchItem item){ LinearLayout r=horizontal(); r.setBackground(strokeBg(Color.WHITE,12,1,Color.rgb(218,229,244))); pad(r,12,11,12,11); TextView d=tv(item.date,11.5f,NAVY,true); d.setGravity(Gravity.CENTER); r.addView(d,new LinearLayout.LayoutParams(dp(72),-1)); LinearLayout x=vertical(); x.addView(tv(item.name,13.5f,NAVY,true)); x.addView(tv(item.sub,11.5f,MUTED,false)); r.addView(x,new LinearLayout.LayoutParams(0,-2,1)); r.addView(tv("›",20,NAVY,false)); r.setOnClickListener(v->{ showProgram(); Toast.makeText(this,"Deschis din Caută: "+item.date,Toast.LENGTH_SHORT).show(); }); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.setMargins(dp(14),0,dp(14),dp(8)); parent.addView(r,p); }
    private String norm(String s){ String n=Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+",""); return n.toLowerCase(Locale.ROOT); }
    private static class SearchItem { final String date,name,sub,search; SearchItem(String d,String n,String s,String q){date=d;name=n;sub=s;search=q;} }
}
