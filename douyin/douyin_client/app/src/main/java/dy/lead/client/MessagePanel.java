package dy.lead.client;

import android.app.*;
import android.content.*;
import android.os.*;
import android.graphics.Color;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Same APK: task-scoped leads and device-wide contact history. */
final class MessagePanel extends ContextWrapper {
    Records db;SharedPreferences prefs;Handler handler=new Handler();WorkspaceUi style;
    LinearLayout list;TextView counts;Button send,pause;
    EditText body,min,max,batchMin,batchMax,restMin,restMax,daily,region,date,confidence;
    CheckBox retry,sort;final Set<String> selected=new LinkedHashSet<>();
    final List<JSONObject> displayed=new ArrayList<>();
    final Set<String> expanded=new HashSet<>();
    final Map<String,Integer> visibleLimits=new HashMap<>();
    final Map<String,LinearLayout> taskBodies=new LinkedHashMap<>();
    final Map<String,List<JSONObject>> taskRows=new LinkedHashMap<>();
    final AutomationActivity host;
    MessagePanel(AutomationActivity host,Records db,SharedPreferences prefs){super(host);this.host=host;this.db=db;this.prefs=prefs;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
    TextView text(LinearLayout parent,String value,int size){TextView t=style.text(value,size);parent.addView(t);return t;}
    Button button(LinearLayout p,String label){return style.button(p,label,false);}
    EditText field(LinearLayout p,String label,String key,String def,boolean number){return style.field(p,label,prefs.getString(key,def),number?2:1);}
    CheckBox check(LinearLayout p,String label){return style.check(p,label);}
    void build(LinearLayout page,WorkspaceUi style){
        this.style=style;LinearLayout[] pair;
        LinearLayout c=style.card(page);counts=text(c,"",13);counts.setTextColor(WorkspaceUi.MUTED);
        pair=style.pair(c);region=field(pair[0],"地区","dm_region","",false);region.setHint("如 安徽");date=field(pair[1],"评论时间","dm_date","",false);date.setHint("如 03-23");
        pair=style.pair(c);confidence=field(pair[0],"最低置信度","dm_score","",false);confidence.setInputType(8194);confidence.setHint("如 0.8");sort=check(pair[1],"置信度从高到低");
        retry=check(c,"允许重选失败的人（成功、待核实除外）");
        pair=style.pair(c);button(pair[0],"应用筛选").setOnClickListener(v->{selected.clear();visibleLimits.clear();render();});button(pair[1],"导出 CSV").setOnClickListener(v->export());
        c=style.card(page);body=field(c,"私信文案","dm_body","",false);style.multiline(body);
        pair=style.pair(c);min=field(pair[0],"间隔（秒）最少","dm_min","20",true);max=field(pair[1],"最多","dm_max","60",true);
        pair=style.pair(c);batchMin=field(pair[0],"处理几人休息 · 最少","dm_batch_min","3",true);batchMax=field(pair[1],"最多","dm_batch_max","5",true);
        pair=style.pair(c);restMin=field(pair[0],"休息（秒）最少","dm_rest_min","60",true);restMax=field(pair[1],"最多","dm_rest_max","180",true);daily=field(c,"每天最多发送尝试次数","dm_daily","30",true);
        pair=style.pair(c);button(pair[0],"全选可发送").setOnClickListener(v->{for(JSONObject row:displayed)if(selectable(row))selected.add(row.optString("group_key"));render();});
        button(pair[1],"取消全选").setOnClickListener(v->{selected.clear();render();});
        send=style.button(c,"给选中的 0 人发私信",true);send.setOnClickListener(v->launch(false));
        pause=button(c,"暂停任务");pause.setOnClickListener(v->{if(AutomationService.instance!=null)AutomationService.instance.togglePause();AutomationService.refreshPauseButton(pause);});AutomationService.refreshPauseButton(pause);
        text(c,"缺少抖音号的客户会跳过；成功或待核实的记录不会重复发送。",12).setTextColor(WorkspaceUi.MUTED);
        list=column();page.addView(list);
        style.scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->loadVisibleRows());
        retry.setOnCheckedChangeListener((b,value)->{selected.clear();render();});render();
    }
    boolean selectable(JSONObject o){return MessagePolicy.canSelect(o.optString("account"),o.optString("dm_state"),retry.isChecked());}
    void render(){
        if(list==null)return;double score=0;try{if(!confidence.getText().toString().trim().isEmpty())score=Double.parseDouble(confidence.getText().toString().trim());if(!Double.isFinite(score)||score<0||score>1)throw new IllegalArgumentException();}catch(Exception e){Toast.makeText(this,"最低置信度请填写 0～1",Toast.LENGTH_SHORT).show();return;}
        JSONArray rows=db.messageRows();LinkedHashMap<String,JSONObject> groups=new LinkedHashMap<>();int raw=0;Set<String> sentIds=new HashSet<>();LinkedHashSet<String> allTasks=new LinkedHashSet<>();JSONArray taskList=db.tasks();for(int i=0;i<taskList.length();i++)allTasks.add(taskList.optJSONObject(i).optString("task"));
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.optJSONObject(i);allTasks.add(row.optString("task"));
            if(!host.aiEligible(row))continue;
            if(!row.optString("place").contains(region.getText().toString().trim())||!row.optString("posted").contains(date.getText().toString().trim()))continue;
            if(score>0&&(row.isNull("score")||row.optDouble("score",-1)<score))continue;
            raw++;String account=MessagePolicy.account(row.optString("account"));String key=account.isEmpty()?"missing:"+row.optString("key"):"account:"+account;
            String taskKey=row.optString("task")+"\n"+key;
            if(row.optString("dm_state").equals("成功"))sentIds.add(account);
            try{if(groups.containsKey(taskKey)){JSONObject previous=groups.get(taskKey);previous.put("merged",previous.optInt("merged",1)+1);}
                else{row.put("account",account);row.put("group_key",key);row.put("merged",1);groups.put(taskKey,row);}}
            catch(JSONException e){throw new IllegalStateException(e);}
        }
        displayed.clear();displayed.addAll(groups.values());if(sort.isChecked())displayed.sort((a,b)->Double.compare(b.optDouble("score",-1),a.optDouble("score",-1)));
        Set<String> allowed=new HashSet<>();int missing=0;for(JSONObject row:displayed){if(selectable(row))allowed.add(row.optString("group_key"));if(!MessagePolicy.validAccount(row.optString("account")))missing++;}
        selected.retainAll(allowed);counts.setText("总数 "+raw+" · 已发送 "+sentIds.size()+" · 合并后 "+displayed.size()+"\n合并掉 "+(raw-displayed.size())+" 条 · 缺少/无效抖音号 "+missing+" 条");
        send.setText("给选中的 "+selected.size()+" 人发私信");list.removeAllViews();taskBodies.clear();taskRows.clear();
        LinkedHashMap<String,List<JSONObject>> byTask=new LinkedHashMap<>();for(String task:allTasks)byTask.put(task,new ArrayList<>());for(JSONObject row:displayed)byTask.get(row.optString("task")).add(row);
        for(Map.Entry<String,List<JSONObject>> group:byTask.entrySet()){
            String task=group.getKey();boolean open=expanded.contains(task);int missingIds=0,merged=0,sent=0;
            for(JSONObject row:group.getValue()){if(!MessagePolicy.validAccount(row.optString("account")))missingIds++;if("成功".equals(row.optString("dm_state")))sent++;merged+=row.optInt("merged",1)-1;}
            LinearLayout section=new LinearLayout(this);section.setGravity(android.view.Gravity.CENTER_VERTICAL);section.setPadding(style.dp(14),style.dp(10),style.dp(14),style.dp(10));section.setMinimumHeight(style.dp(58));section.setBackground(style.shape(0xfff9fafc,WorkspaceUi.LINE,17));
            LinearLayout.LayoutParams sectionParams=new LinearLayout.LayoutParams(-1,-2);sectionParams.topMargin=style.dp(12);list.addView(section,sectionParams);
            TextView arrow=text(section,open?"▾":"▸",16);arrow.setTextColor(WorkspaceUi.MUTED);arrow.setPadding(0,0,style.dp(12),0);
            TextView name=style.text(prefs.getString("task_name_"+task,"任务 "+task),16);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);section.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView stats=text(section,group.getValue().size()+" 人 · 已发送 "+sent+"\n缺号 "+missingIds+" · 合并 "+merged,12);stats.setTextColor(WorkspaceUi.MUTED);stats.setGravity(android.view.Gravity.END);stats.setPadding(style.dp(8),0,0,0);
            TextView delete=style.text("删除",12);delete.setTextColor(0xffb34d5e);delete.setGravity(android.view.Gravity.CENTER);delete.setPadding(style.dp(10),0,style.dp(10),0);delete.setMinHeight(style.dp(32));delete.setBackground(style.shape(0xfffff5f6,0xfff1dfe2,10));delete.setContentDescription("删除历史任务");LinearLayout.LayoutParams deleteParams=new LinearLayout.LayoutParams(-2,style.dp(32));deleteParams.leftMargin=style.dp(8);section.addView(delete,deleteParams);delete.setOnClickListener(v->confirmDeleteTask(task));
            section.setContentDescription(name.getText()+"，"+stats.getText()+"，"+(open?"已展开":"已收起"));section.setFocusable(true);section.setOnClickListener(v->{if(expanded.contains(task))expanded.remove(task);else expanded.add(task);render();});
            if(!open)continue;
            LinearLayout body=column();list.addView(body);taskBodies.put(task,body);taskRows.put(task,group.getValue());
            if(group.getValue().isEmpty())text(body,"当前筛选下暂无客户",14).setTextColor(WorkspaceUi.MUTED);
            else appendRows(task,visibleLimits.getOrDefault(task,40));
        }
        if(byTask.isEmpty())text(list,"暂无任务",15).setTextColor(WorkspaceUi.MUTED);
        list.post(this::loadVisibleRows);
    }
    void confirmDeleteTask(String task){
        if(AutomationService.instance!=null&&AutomationService.instance.running&&task.equals(prefs.getString("lastTask",""))){Toast.makeText(this,"当前任务正在运行，结束后才能删除",Toast.LENGTH_LONG).show();return;}
        new AlertDialog.Builder(this).setTitle("删除历史任务").setMessage("删除“"+prefs.getString("task_name_"+task,"任务 "+task)+"”的本机采集记录和运行日志，并请求删除服务器日志？此操作不可恢复。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->{
            int removed=db.deleteTask(task);Diagnostics.deleteUploadedTask(host,task);prefs.edit().remove("task_name_"+task).apply();expanded.remove(task);visibleLimits.remove(task);selected.clear();Toast.makeText(this,"已删除任务记录（"+removed+" 条）",Toast.LENGTH_SHORT).show();render();
        }).show();
    }
    void appendRows(String task,int limit){
        LinearLayout parent=taskBodies.get(task);List<JSONObject> rows=taskRows.get(task);int end=Math.min(limit,rows.size());
        for(int i=parent.getChildCount();i<end;i++){
            JSONObject row=rows.get(i);LinearLayout card=column();String state=row.optString("dm_state"),key=row.optString("group_key");
            card.setBackground(style.shape(MessagePolicy.color(state),WorkspaceUi.LINE,17));card.setPadding(style.dp(12),style.dp(10),style.dp(12),style.dp(14));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=style.dp(8);parent.addView(card,lp);
            LinearLayout title=new LinearLayout(this);title.setGravity(android.view.Gravity.CENTER_VERTICAL);card.addView(title);
            CheckBox select=style.check(title,row.optString("name"));select.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));select.setChecked(selected.contains(key));select.setEnabled(selectable(row));select.setTag(key);
            select.setOnCheckedChangeListener((v,yes)->{if(yes)selected.add(key);else selected.remove(key);syncChecks(key,yes);send.setText("给选中的 "+selected.size()+" 人发私信");});
            TextView score=text(title,String.format(java.util.Locale.US,"%.2f",row.optDouble("score",0)),13);score.setTextColor(WorkspaceUi.PINK);
            text(card,row.optString("body"),15);text(card,(row.optString("account").isEmpty()?"未获取抖音号":row.optString("account"))+" · "+row.optString("place")+" · "+row.optString("posted"),12).setTextColor(WorkspaceUi.MUTED);
            String reason=row.optString("dm_reason");if(!MessagePolicy.validAccount(row.optString("account")))reason="缺少有效抖音号，无法准确搜索";
            if(!state.equals("未尝试")||!reason.isEmpty())text(card,(state.equals("成功")?"发送成功":state)+(reason.isEmpty()?"":" · "+reason),12).setTextColor(0xff996577);
        }
        visibleLimits.put(task,end);
    }
    void syncChecks(String key,boolean checked){
        for(LinearLayout body:taskBodies.values())for(int i=0;i<body.getChildCount();i++){
            android.view.View row=body.getChildAt(i);if(!(row instanceof LinearLayout))continue;
            CheckBox check=(CheckBox)((LinearLayout)((LinearLayout)row).getChildAt(0)).getChildAt(0);
            if(key.equals(check.getTag())&&check.isChecked()!=checked)check.setChecked(checked);
        }
    }
    void loadVisibleRows(){
        android.graphics.Rect viewport=new android.graphics.Rect();if(!style.scroll.getGlobalVisibleRect(viewport))return;
        for(Map.Entry<String,LinearLayout> entry:taskBodies.entrySet()){
            LinearLayout body=entry.getValue();List<JSONObject> rows=taskRows.get(entry.getKey());if(rows.isEmpty()||body.getChildCount()>=rows.size())continue;
            int[] position=new int[2];body.getLocationOnScreen(position);
            if(position[1]<viewport.bottom&&position[1]+body.getHeight()<viewport.bottom+style.dp(320))appendRows(entry.getKey(),body.getChildCount()+40);
        }
    }
    int number(EditText e){return Integer.parseInt(e.getText().toString().trim());}
    void saveFields(){SharedPreferences.Editor e=prefs.edit();String[] keys={"body","min","max","batch_min","batch_max","rest_min","rest_max","daily","region","date","score"};EditText[] inputs={body,min,max,batchMin,batchMax,restMin,restMax,daily,region,date,confidence};for(int i=0;i<keys.length;i++)e.putString("dm_"+keys[i],inputs[i].getText().toString());e.apply();}
    void export(){
        render();if(displayed.isEmpty()){Toast.makeText(this,"没有可导出的记录",Toast.LENGTH_SHORT).show();return;}
        StringBuilder csv=new StringBuilder("\ufeff抖音号,昵称,评论,地区,评论时间,AI判断,置信度,合并条数,发送状态,原因\r\n");
        String[] keys={"account","name","body","place","posted","ai","score","merged","dm_state","dm_reason"};
        for(JSONObject row:displayed){if(!selected.isEmpty()&&!selected.contains(row.optString("group_key")))continue;for(int i=0;i<keys.length;i++){if(i>0)csv.append(',');csv.append(AutomationActivity.cell(row.isNull(keys[i])?"":row.optString(keys[i])));}csv.append("\r\n");}
        host.exportData=csv.toString();host.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE,"518意向客户.csv"),51);
    }
    void launch(boolean dry){
        try{
            render();if(selected.isEmpty())throw new IllegalArgumentException("请先勾选有抖音号、可发送的人");
            if(AutomationService.instance==null)throw new IllegalArgumentException("请先开启无障碍服务");if(AutomationService.instance.running)throw new IllegalArgumentException("请等待当前任务结束或先停止");
            MessagePolicy.validate(number(min),number(max),number(batchMin),number(batchMax),number(restMin),number(restMax),number(daily));
            String message=body.getText().toString().trim();if(!dry&&(message.isEmpty()||message.length()>1000))throw new IllegalArgumentException("请填写 1～1000 字的私信文案");
            JSONArray accounts=new JSONArray();Set<String> queued=new HashSet<>();for(JSONObject row:displayed)if(selected.contains(row.optString("group_key"))&&selectable(row)&&queued.add(row.optString("account")))accounts.put(row.optString("account"));
            JSONObject config=new JSONObject().put("dmMode",true).put("dryRun",dry).put("accounts",accounts).put("body",message).put("retry",retry.isChecked()).put("min",number(min)).put("max",number(max)).put("batchMin",number(batchMin)).put("batchMax",number(batchMax)).put("restMin",number(restMin)).put("restMax",number(restMax)).put("daily",number(daily)).put("quantity",true);
            SharedPreferences.Editor editor=prefs.edit();String[] keys={"body","min","max","batch_min","batch_max","rest_min","rest_max","daily","region","date","score"};EditText[] fields={body,min,max,batchMin,batchMax,restMin,restMax,daily,region,date,confidence};for(int i=0;i<keys.length;i++)editor.putString("dm_"+keys[i],fields[i].getText().toString());editor.apply();
            String summary=(dry?"只检查，不填写或发送消息":"将逐个搜索并核对抖音号后发送")+"\n人数："+accounts.length()+"\n"+(dry?"": "文案："+message+"\n")+"间隔："+number(min)+"～"+number(max)+" 秒\n已成功发送和待核实的人会跳过。";
            new AlertDialog.Builder(this).setTitle(dry?"检查用户路径":"确认发送任务").setMessage(summary).setNegativeButton("取消",null).setPositiveButton(dry?"开始检查":"开始发送",(d,w)->AutomationService.instance.startTask(config)).show();
        }catch(Exception e){Toast.makeText(this,e.getMessage()==null?"配置无效":e.getMessage(),Toast.LENGTH_LONG).show();}
    }
}
