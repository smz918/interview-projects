package dy.lead.client;

import android.app.*;import android.os.*;import android.content.*;import android.provider.Settings;import android.widget.*;import java.util.*;import org.json.*;

/** Same-app interaction assistant configuration. Uses the existing accessibility worker. */
public final class InteractionActivity extends Activity {
    WorkspaceUi ui; SharedPreferences prefs; EditText keyword,start,end,candidates; CheckBox like,comment,aiText; TextView status;
    LinkedHashMap<String,Spinner> filters=new LinkedHashMap<>();
    final Handler refreshHandler=new Handler();TextView logs;Records records;
    Button pause;
    final Runnable refresh=new Runnable(){public void run(){ui.refreshPermissions();AutomationService.refreshPauseButton(pause);status.setText(prefs.getString("task_status","空闲"));logs.setText(records.logs());refreshHandler.postDelayed(this,1500);}};
    @Override protected void onResume(){super.onResume();if(MainActivity.requireLogin(this)&&ui!=null)refreshHandler.post(refresh);}
    @Override protected void onPause(){refreshHandler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onDestroy(){if(records!=null)records.close();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);Diagnostics.result(this,request,result,data);}
    @Override public void onCreate(Bundle b){super.onCreate(b);if(!MainActivity.requireLogin(this))return;prefs=getSharedPreferences("workspace",0);build();}
    EditText field(LinearLayout p,String title,String key,String def,int type){return ui.field(p,title,prefs.getString(key,def),type);}
    CheckBox action(LinearLayout p,String title,boolean checked){CheckBox c=ui.check(p,title);c.setChecked(prefs.getBoolean(title,checked));return c;}
    void build(){
        ui=new WorkspaceUi(this);LinearLayout page=ui.page("客户互动助手","识别意向客户，点赞和评论一个视频或笔记");
        LinearLayout[] tabs=ui.pair(page);ui.button(tabs[0],"意愿客户获取",false).setOnClickListener(v->{finish();});ui.button(tabs[1],"客户互动助手",true);
        status=ui.status(page);
        LinearLayout c=ui.card(page);keyword=field(c,"搜索关键词（两个功能共用）","ia_keyword","黄金回收",1);LinearLayout[] pair=ui.pair(c);start=field(pair[0],"开始时间","ia_start","09:00",1);end=field(pair[1],"结束时间","ia_end","22:00",1);TextView hint=ui.text("两个时间填一样表示不限时段；支持跨午夜，例如 22:00 到 02:00。",12);c.addView(hint);hint.setTextColor(WorkspaceUi.MUTED);
        c=ui.card(page);ui.label(c,"搜索筛选");
        pair=ui.pair(c);
        filters.put("sort",spinner(pair[0],"排序依据",new String[]{"综合排序","最新发布","最多点赞","最多评论","最多收藏"},prefs.getString("ia_sort","综合排序")));
        filters.put("form",spinner(pair[1],"内容形式",new String[]{"不限","视频","图文"},prefs.getString("ia_form","不限")));
        pair=ui.pair(c);
        filters.put("published",spinner(pair[0],"发布时间",new String[]{"不限","一天内","一周内","半年内"},prefs.getString("ia_published","不限")));
        filters.put("scope",spinner(pair[1],"搜索范围",new String[]{"不限","关注的人","最近看过","还未看过"},prefs.getString("ia_scope","不限")));
        pair=ui.pair(c);
        filters.put("duration",spinner(pair[0],"视频时长",new String[]{"不限","1分钟以下","1-5分钟","5分钟以上"},prefs.getString("ia_duration","不限")));
        filters.put("distance",spinner(pair[1],"位置距离",new String[]{"不限","同城"},prefs.getString("ia_distance","不限")));
        c=ui.card(page);ui.label(c,"执行动作");like=action(c,"进入客户主页，点赞作品",true);comment=action(c,"进入客户主页，评论作品",true);aiText=action(c,"评论文案让 AI 针对作品生成",true);
        c.addView(ui.text("每位客户仅处理一个视频或笔记，没有可见作品则跳过。",12));
        c=ui.card(page);candidates=field(c,"评论候选池（不用 AI 生成时随机选择）","ia_candidates","一行一句，多写几条，随机挑一句",1);ui.multiline(candidates);EditText mode=field(c,"意向判断口径","ai_mode",prefs.getString("ai_mode","均衡"),1);mode.setEnabled(false);
        LinearLayout[] buttons=ui.pair(page);ui.button(buttons[0],"保存设置",false).setOnClickListener(v->{save();Toast.makeText(this,"已保存",Toast.LENGTH_SHORT).show();});ui.button(buttons[1],"创建并开始任务",true).setOnClickListener(v->start());
        pause=ui.button(page,"暂停任务",false);pause.setOnClickListener(v->{if(AutomationService.instance!=null)AutomationService.instance.togglePause();AutomationService.refreshPauseButton(pause);});AutomationService.refreshPauseButton(pause);
        Button stop=ui.button(page,"停止当前任务",false);stop.setTextColor(0xffb84965);stop.setOnClickListener(v->{if(AutomationService.instance!=null)AutomationService.instance.finish("用户停止互动任务");});
        records=new Records(this);c=ui.card(page);c.addView(ui.text("运行日志",20));Diagnostics.buttons(this,c,ui);logs=ui.text(records.logs(),12);ui.logStyle(logs);c.addView(logs);
    }
    void save(){SharedPreferences.Editor e=prefs.edit().putString("ia_keyword",keyword.getText().toString()).putString("ia_start",start.getText().toString()).putString("ia_end",end.getText().toString()).putString("ia_candidates",candidates.getText().toString()).remove("ia_works").putBoolean("进入客户主页，关注",false).putBoolean("进入客户主页，点赞作品",like.isChecked()).putBoolean("进入客户主页，评论作品",comment.isChecked()).putBoolean("评论文案让 AI 针对作品生成",aiText.isChecked());for(Map.Entry<String,Spinner> entry:filters.entrySet())e.putString("ia_"+entry.getKey(),entry.getValue().getSelectedItem().toString());e.apply();}
    void start(){save();if(AutomationService.instance==null){Toast.makeText(this,"请先开启无障碍服务",Toast.LENGTH_LONG).show();startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));return;}try{JSONObject c=new JSONObject().put("interactionMode",true).put("quantity",false).put("keyword",keyword.getText().toString().trim()).put("startTime",start.getText().toString().trim()).put("endTime",end.getText().toString().trim()).put("follow",false).put("likeWorks",like.isChecked()).put("commentWorks",comment.isChecked()).put("aiComment",aiText.isChecked()).put("commentCandidates",candidates.getText().toString()).put("aiMode",prefs.getString("ai_mode","均衡"));for(Map.Entry<String,Spinner> entry:filters.entrySet())c.put(entry.getKey(),entry.getValue().getSelectedItem().toString());AutomationService.instance.startTask(c);finish();}catch(Exception e){Toast.makeText(this,"请检查任务设置",Toast.LENGTH_LONG).show();}}
    Spinner spinner(LinearLayout p,String title,String[] values,String current){return ui.spinner(p,title,values,current);}
}
