package dy.lead.client;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Customer-facing queue for tasks that are run manually by the desktop operator. */
public final class RemoteTaskActivity extends Activity {
    WorkspaceUi style; EditText taskName, keyword; Spinner sort, form, published, scope, duration;
    LinearLayout list; TextView status; Button submit; Handler handler=new Handler(); boolean busy;
    final Set<String> expanded=new HashSet<>(); JSONArray tasks=new JSONArray();
    final Set<String> openedLinks=new HashSet<>();
    final Runnable refresh=new Runnable(){public void run(){load();handler.postDelayed(this,5000);}};

    @Override public void onCreate(Bundle state){super.onCreate(state);if(!MainActivity.requireLogin(this))return;openedLinks.addAll(getSharedPreferences("workspace",0).getStringSet("opened_remote_links",Collections.emptySet()));build();load();}
    @Override protected void onResume(){super.onResume();if(style!=null){handler.removeCallbacks(refresh);handler.post(refresh);}}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}

    void build(){
        style=new WorkspaceUi(this);LinearLayout page=style.page("服务器获客","提交任务后由电脑端采集，完成后结果会回到这里");
        LinearLayout[] top=style.pair(page);Button back=style.button(top[0],"返回自动化",false);back.setOnClickListener(v->finish());
        status=style.text("正在连接任务服务…",13);status.setTextColor(WorkspaceUi.MUTED);top[1].addView(status);
        LinearLayout card=style.card(page);
        taskName=style.field(card,"任务名称","",InputType.TYPE_CLASS_TEXT);taskName.setHint("例如：武汉染发客户");
        keyword=style.field(card,"搜索关键词","",InputType.TYPE_CLASS_TEXT);keyword.setHint("例如：染发武汉哪里有");
        LinearLayout[] pair=style.pair(card);
        sort=style.spinner(pair[0],"排序依据",new String[]{"综合排序","最新发布","最多点赞","最多评论","最多收藏"},"综合排序");
        form=style.spinner(pair[1],"内容形式",new String[]{"不限","视频","图文"},"不限");
        pair=style.pair(card);
        published=style.spinner(pair[0],"发布时间",new String[]{"不限","一天内","一周内","半年内"},"不限");
        scope=style.spinner(pair[1],"搜索范围",new String[]{"不限","关注的人","最近看过","还未看过"},"不限");
        duration=style.spinner(card,"视频时长",new String[]{"不限","1分钟以下","1-5分钟","5分钟以上"},"不限");
        submit=style.button(card,"提交服务器获客任务",true);submit.setOnClickListener(v->submitTask());
        list=column();page.addView(list,new LinearLayout.LayoutParams(-1,-2));
    }
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void submitTask(){
        if(busy)return;String name=taskName.getText().toString().trim(),key=keyword.getText().toString().trim();
        if(name.isEmpty()||key.isEmpty()){toast("请填写任务名称和搜索关键词");return;}
        busy=true;submit.setEnabled(false);status.setText("正在提交…");
        new Thread(()->{try{
            JSONObject body=new JSONObject().put("task_name",name).put("keyword",key).put("sort",sort.getSelectedItem().toString()).put("content_form",form.getSelectedItem().toString()).put("published",published.getSelectedItem().toString()).put("scope",scope.getSelectedItem().toString()).put("duration",duration.getSelectedItem().toString());
            MainActivity.callApi("/api/v1/remote/tasks",body,token());
            runOnUiThread(()->{taskName.setText("");keyword.setText("");status.setText("提交成功");load();});
        }catch(Exception e){runOnUiThread(()->status.setText("提交失败："+e.getMessage()));}
        finally{runOnUiThread(()->{busy=false;submit.setEnabled(true);});}}).start();
    }
    String token(){return getSharedPreferences("workspace",0).getString("token","");}
    void load(){
        if(busy)return;new Thread(()->{try{JSONObject result=MainActivity.callApiGet("/api/v1/remote/tasks",token());JSONArray a=result.has("tasks")?result.optJSONArray("tasks"):new JSONArray(result.toString());runOnUiThread(()->{tasks=a;status.setText("已同步 · "+a.length()+" 个任务");render();});}
            catch(Exception e){runOnUiThread(()->status.setText("任务服务暂时不可用"));}}).start();
    }
    void render(){
        if(list==null)return;list.removeAllViews();
        if(tasks.length()==0){TextView empty=style.text("还没有服务器获客任务",14);empty.setTextColor(WorkspaceUi.MUTED);list.addView(empty);return;}
        for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(task==null)continue;renderTask(task);}
    }
    void renderTask(JSONObject task){
        String id=task.optString("task_id"),name=task.optString("task_name","未命名任务"),state=task.optString("status");boolean open=expanded.contains(id);
        LinearLayout card=style.card(list);LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);card.addView(head);
        TextView arrow=style.text(open?"▾":"▸",18);arrow.setTextColor(WorkspaceUi.MUTED);head.addView(arrow,new LinearLayout.LayoutParams(style.dp(30),-2));
        TextView title=style.text(name+"  ·  "+stateText(state)+"  ·  "+task.optInt("result_count",0)+" 条",16);title.setTypeface(null,android.graphics.Typeface.BOLD);head.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView del=style.text("删除",13);del.setTextColor(0xffb84965);del.setGravity(Gravity.CENTER);del.setPadding(style.dp(8),0,style.dp(8),0);head.addView(del,new LinearLayout.LayoutParams(-2,style.dp(36)));del.setOnClickListener(v->deleteTask(id,name));
        head.setOnClickListener(v->{if(open)expanded.remove(id);else expanded.add(id);render();});
        if(!open)return;
        TextView detail=style.text("关键词："+task.optString("keyword")+"\n排序："+task.optString("sort")+" · 形式："+task.optString("content_form")+"\n发布时间："+task.optString("published")+" · 范围："+task.optString("scope")+" · 时长："+task.optString("duration"),12);detail.setTextColor(WorkspaceUi.MUTED);card.addView(detail);
        JSONArray rows=task.optJSONArray("results");if(rows==null||rows.length()==0){TextView empty=style.text(state.equals("completed")?"暂无命中结果":"结果将在电脑端处理完成后显示",14);empty.setTextColor(WorkspaceUi.MUTED);card.addView(empty);return;}
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;LinearLayout r=column();r.setPadding(style.dp(10),style.dp(8),style.dp(10),style.dp(8));r.setBackground(style.shape(0xfffafbfc,WorkspaceUi.LINE,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=style.dp(8);card.addView(r,lp);
            TextView comment=style.text(row.optString("comment","（无评论内容）"),15);r.addView(comment);
            String url=row.optString("profile_url");TextView link=style.text(url.isEmpty()?"未提供用户主页链接":url,12);link.setTextColor(openedLinks.contains(url)?0xff9aa0a6:0xff2878b8);link.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);link.setOnClickListener(v->{if(!url.isEmpty())try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));markLinkOpened(url);}catch(Exception ignored){}});r.addView(link);
            TextView meta=style.text("评论时间："+row.optString("comment_time","—")+"  ·  IP属地："+row.optString("ip_location","—"),12);meta.setTextColor(WorkspaceUi.MUTED);r.addView(meta);
        }
    }
    String stateText(String s){if("pending".equals(s))return "待处理";if("processing".equals(s))return "处理中";if("completed".equals(s))return "已完成";if("failed".equals(s))return "失败";return s;}
    void markLinkOpened(String url){if(url==null||url.isEmpty()||openedLinks.contains(url))return;openedLinks.add(url);getSharedPreferences("workspace",0).edit().putStringSet("opened_remote_links",new HashSet<>(openedLinks)).apply();runOnUiThread(this::render);}
    void deleteTask(String id,String name){new AlertDialog.Builder(this).setTitle("删除任务").setMessage("确定删除“"+name+"”及其结果吗？").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->new Thread(()->{try{MainActivity.callApiDelete("/api/v1/remote/tasks/"+id,token());expanded.remove(id);runOnUiThread(this::load);}catch(Exception e){runOnUiThread(()->toast("删除失败："+e.getMessage()));}}).start()).show();}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
