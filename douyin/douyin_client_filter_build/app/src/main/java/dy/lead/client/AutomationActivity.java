package dy.lead.client;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.provider.Settings;
import android.text.InputType;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AutomationActivity extends Activity {
    SharedPreferences prefs; Records db; Handler ui=new Handler();TextView progress,logs,total;
    LinearLayout results;EditText keyword,limit,perVideo,identity,target,taskName;Spinner mode,aiMode;WorkspaceUi style;MessagePanel messagePanel;
    LinkedHashMap<String,Spinner> filters=new LinkedHashMap<>();CheckBox profiles,onlyIntent;
    boolean acquisitionRegression;
    Button pause;
    Set<String> selected=new HashSet<>();String exportData="";int shownCount=-1;String shownStatus="";
    final Runnable refresh=new Runnable(){public void run(){style.refreshPermissions();AutomationService.refreshPauseButton(pause);AutomationService.refreshPauseButton(messagePanel.pause);progress.setText(prefs.getString("task_status","空闲 · 尚未运行"));logs.setText(db.logs());String log=db.logs();if(db.count()!=shownCount||!log.equals(shownStatus)){messagePanel.render();shownCount=db.count();shownStatus=log;}ui.postDelayed(this,1500);}};
    public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences("workspace",0);if(!MainActivity.requireLogin(this))return;prefs.edit().remove("ai_key").remove("ai_model").apply();db=new Records(this);build();}
    protected void onResume(){super.onResume();if(!MainActivity.requireLogin(this))return;if(style!=null){ui.removeCallbacks(refresh);ui.post(refresh);}}
    protected void onPause(){ui.removeCallbacks(refresh);super.onPause();}
    protected void onPostResume(){super.onPostResume();if(AutomationService.development(this)&&getIntent().getBooleanExtra("ai_regression",false)){getIntent().removeExtra("ai_regression");ui.postDelayed(()->{if(AutomationService.instance!=null)AutomationService.instance.startAiRegression();else toast("AI测试需要无障碍服务已连接");},1200);}if(AutomationService.development(this)&&getIntent().getBooleanExtra("overlay_regression",false)){getIntent().removeExtra("overlay_regression");ui.postDelayed(()->{if(AutomationService.instance!=null)AutomationService.instance.previewOverlay();},1200);}}
    @Override protected void onStart(){
        super.onStart();
        if(AutomationService.development(this)&&getIntent().getBooleanExtra("saved_acquisition_regression",false)){
            getIntent().removeExtra("saved_acquisition_regression");
            ui.postDelayed(()->{if(AutomationService.instance==null||AutomationService.instance.running){toast("回归需要服务已连接且空闲");return;}start(false);},1200);
        }
        if(AutomationService.development(this)&&getIntent().getBooleanExtra("note_viewport_regression",false)){
            getIntent().removeExtra("note_viewport_regression");
            ui.postDelayed(()->{if(AutomationService.instance!=null)AutomationService.instance.startCommentRegression(false,true);},1200);
        }
        if(AutomationService.development(this)&&getIntent().getBooleanExtra("acquisition_regression",false)){
            getIntent().removeExtra("acquisition_regression");
            ui.postDelayed(()->{if(AutomationService.instance==null||AutomationService.instance.running){toast("回归需要服务已连接且空闲");return;}limit.setText("100");perVideo.setText("10");profiles.setChecked(true);acquisitionRegression=true;start(false);acquisitionRegression=false;},1200);
        }
        if(AutomationService.development(this)&&getIntent().getBooleanExtra("note_regression",false)){
            boolean withAi=getIntent().getBooleanExtra("note_ai",false);getIntent().removeExtra("note_regression");
            ui.postDelayed(()->{if(AutomationService.instance!=null)AutomationService.instance.startCommentRegression(true,withAi);},1200);
        }
        if(AutomationService.development(this)&&"huainanwangd75".equals(getIntent().getStringExtra("dm_authorized_send"))){
            getIntent().removeExtra("dm_authorized_send");
            ui.postDelayed(()->{
                if(AutomationService.instance==null||AutomationService.instance.running){toast("私信实发测试需要服务已连接且空闲");return;}
                try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts WHERE account=? AND send_requested>0",new String[]{"huainanwangd75"})){
                    c.moveToFirst();if(c.getInt(0)>0){toast("测试账号已请求过发送，不重复实发");return;}
                    AutomationService.instance.startTask(new JSONObject().put("dmMode",true).put("dryRun",false).put("retry",true)
                        .put("accounts",new JSONArray().put("huainanwangd75")).put("body","你好")
                        .put("min",20).put("max",20).put("batchMin",1).put("batchMax",1).put("restMin",60).put("restMax",60).put("daily",30).put("quantity",true));
                }catch(Exception e){toast("私信实发测试启动失败");}
            },1200);
        }
        if(AutomationService.development(this)&&getIntent().hasExtra("dm_navigation_regression")){
            String accounts=getIntent().getStringExtra("dm_navigation_regression");getIntent().removeExtra("dm_navigation_regression");
            ui.postDelayed(()->{
                if(AutomationService.instance==null){toast("私信检查需要无障碍服务已连接");return;}
                try{JSONArray ids=new JSONArray();for(String id:accounts.split(","))ids.put(id.trim());
                    AutomationService.instance.startTask(new JSONObject().put("dmMode",true).put("dryRun",true).put("accounts",ids).put("body","")
                        .put("min",2).put("max",2).put("batchMin",3).put("batchMax",3).put("restMin",2).put("restMax",2).put("daily",1).put("quantity",true));
                }catch(Exception e){toast("私信检查参数无效");}
            },1200);
        }
    }
    void build(){
        style=new WorkspaceUi(this);LinearLayout page=style.page("意愿客户获取","搜索筛选、评论采集与客户意图分析");
        LinearLayout[] tabs=style.pair(page);button(tabs[0],"意愿客户获取",true);button(tabs[1],"客户互动助手",false).setOnClickListener(v->startActivity(new Intent(this,InteractionActivity.class)));
        progress=style.status(page);
        LinearLayout c=card(page);taskName=field(c,"任务名称","task_name","新任务",1);keyword=field(c,"搜索关键词（两个功能共用）","keyword","黄金回收",1);
        c=card(page);LinearLayout[] pair=style.pair(c);
        filters.put("sort",spinner(pair[0],"排序依据",new String[]{"综合排序","最新发布","最多点赞","最多评论","最多收藏"},prefs.getString("sort","最多评论")));
        filters.put("form",spinner(pair[1],"内容形式",new String[]{"不限","视频","图文"},prefs.getString("form","不限")));
        pair=style.pair(c);
        filters.put("published",spinner(pair[0],"发布时间",new String[]{"不限","一天内","一周内","半年内"},prefs.getString("published","不限")));
        filters.put("scope",spinner(pair[1],"搜索范围",new String[]{"不限","关注的人","最近看过","还未看过"},prefs.getString("scope","不限")));
        pair=style.pair(c);
        filters.put("duration",spinner(pair[0],"视频时长",new String[]{"不限","1分钟以下","1-5分钟","5分钟以上"},prefs.getString("duration","不限")));
        filters.put("distance",spinner(pair[1],"位置距离",new String[]{"不限","同城"},prefs.getString("distance","不限")));
        c=card(page);identity=field(c,"我是什么人","identity","我是回收黄金的人",1);style.multiline(identity);target=field(c,"我要找什么样的人","target","想出售黄金的人",1);style.multiline(target);
        c=card(page);aiMode=spinner(c,"AI 判断口径",new String[]{"严格","均衡","宽松"},prefs.getString("ai_mode","均衡"));
        c=card(page);
        pair=style.pair(c);limit=field(pair[0],"打开视频数量","limit","1",2);perVideo=field(pair[1],"每个视频采几条评论","perVideo","20",2);
        profiles=style.check(c,"仅对符合当前口径的意向客户获取抖音号");profiles.setChecked(prefs.getBoolean("profiles",true));
        button(page,"保存设置",false).setOnClickListener(v->{save();if(messagePanel!=null)messagePanel.saveFields();toast("已保存");});
        button(page,"创建并运行",true).setOnClickListener(v->start());
        pause=button(page,"暂停任务",false);pause.setOnClickListener(v->{if(AutomationService.instance!=null)AutomationService.instance.togglePause();AutomationService.refreshPauseButton(pause);});AutomationService.refreshPauseButton(pause);
        Button stop=button(page,"停止当前任务",false);stop.setTextColor(0xffb84965);stop.setOnClickListener(v->{if(AutomationService.instance!=null)AutomationService.instance.finish("用户停止");});
        messagePanel=new MessagePanel(this,db,prefs);messagePanel.build(page,style);total=messagePanel.counts;
        LinearLayout l=card(page);l.addView(text("运行日志",20));Diagnostics.buttons(this,l,style);logs=text(db.logs(),12);style.logStyle(logs);l.addView(logs);
    }
    void save(){SharedPreferences.Editor e=prefs.edit();e.putString("task_name",taskName.getText().toString().trim());e.putString("keyword",keyword.getText().toString());e.putString("limit",limit.getText().toString());e.putString("perVideo",perVideo.getText().toString());e.putString("identity",identity.getText().toString());e.putString("target",target.getText().toString());e.putString("ai_mode",aiMode.getSelectedItem().toString());e.putBoolean("profiles",profiles.isChecked());for(Map.Entry<String,Spinner> entry:filters.entrySet())e.putString(entry.getKey(),entry.getValue().getSelectedItem().toString());e.apply();}
    void start(){start(false);}
    void start(boolean filterOnly){
        prefs.edit().putString("task_name",taskName.getText().toString().trim()).apply();
        try{save();int n=Integer.parseInt(limit.getText().toString()),comments=Integer.parseInt(perVideo.getText().toString());if(n<1||n>10000||comments<1||comments>1000000||keyword.getText().toString().trim().isEmpty())throw new IllegalArgumentException();
            if(AutomationService.instance==null){toast("请开启 518获客先锋 的无障碍服务");startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}
            JSONObject c=new JSONObject().put("filterOnly",filterOnly).put("keyword",keyword.getText().toString().trim()).put("limit",n).put("perVideo",comments).put("quantity",true).put("identity",identity.getText().toString()).put("target",target.getText().toString()).put("aiMode",aiMode.getSelectedItem().toString()).put("profiles",profiles.isChecked());for(Map.Entry<String,Spinner> e:filters.entrySet())c.put(e.getKey(),e.getValue().getSelectedItem().toString());if(acquisitionRegression)c.put("regressionClosePanelOnce",true);AutomationService.instance.startTask(c);
        }catch(Exception e){toast("请填写关键词和有效的正整数上限");}
    }
    boolean aiEligible(JSONObject o){if(!"意向".equals(o.optString("ai")))return false;double score=o.optDouble("score",-1);String mode=prefs.getString("ai_mode","均衡");double min="严格".equals(mode)?0.8:"宽松".equals(mode)?0.4:0.6;return score>=min;}
    void render(){if(results==null)return;results.removeAllViews();JSONArray all=db.all();int shown=0;for(int i=0;i<all.length();i++){JSONObject o=all.optJSONObject(i);if(!aiEligible(o))continue;if(shown++>=100)break;CheckBox check=new CheckBox(this);check.setText(o.optString("name")+" · 意向 "+o.optString("score"));String key=o.optString("key");check.setChecked(selected.contains(key));check.setOnCheckedChangeListener((b,v)->{if(v)selected.add(key);else selected.remove(key);});results.addView(check);String account=o.optString("account");TextView info=text(o.optString("body")+"\n"+o.optString("place")+" · "+o.optString("posted")+"\n抖音号："+(account.isEmpty()?"未获取":account)+"\n"+o.optString("reason"),13);info.setPadding(12,0,12,18);results.addView(info);}if(shown==0)results.addView(text("当前口径下暂无意向客户",14));}
    static String cell(String s){if(s.matches("^[=+@\\-].*"))s="'"+s;return "\""+s.replace("\"","\"\"")+"\"";}
    void export(){StringBuilder out=new StringBuilder("\ufeff抖音号,昵称,评论,展示地区,发布时间,AI判断,置信度,判断依据,来源视频\r\n");JSONArray a=db.all();int count=0;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(!selected.isEmpty()&&!selected.contains(o.optString("key")))continue;if(selected.isEmpty()&&onlyIntent.isChecked()&&!o.optString("ai").equals("意向"))continue;String[] keys={"account","name","body","place","posted","ai","score","reason","video"};for(int k=0;k<keys.length;k++){if(k>0)out.append(',');out.append(cell(o.isNull(keys[k])?"":o.optString(keys[k])));}out.append("\r\n");count++;}if(count==0){toast("没有可导出的记录");return;}exportData=out.toString();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"518客户评论.csv");startActivityForResult(i,51);}
    protected void onActivityResult(int req,int code,Intent data){super.onActivityResult(req,code,data);if(Diagnostics.result(this,req,code,data))return;if(req==51&&code==RESULT_OK&&data!=null){try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(exportData.getBytes(StandardCharsets.UTF_8));toast("已保存 CSV");}catch(Exception e){toast("保存失败");}}}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    LinearLayout card(LinearLayout p){return style.card(p);}
    TextView text(String s,int size){return style.text(s,size);}
    Button button(LinearLayout p,String label,boolean primary){return style.button(p,label,primary);}
    EditText field(LinearLayout p,String title,String key,String def,int type){return style.field(p,title,prefs.getString(key,def),type);}
    Spinner spinner(LinearLayout p,String title,String[] values,String current){return style.spinner(p,title,values,current);}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}int dp(int n){return (int)(getResources().getDisplayMetrics().density*n);}
}
