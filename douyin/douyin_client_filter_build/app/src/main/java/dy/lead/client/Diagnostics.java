package dy.lead.client;

import android.app.Activity;
import android.content.*;
import android.os.Build;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.JSONObject;

/** User-exported support bundle. Never includes account tokens, API keys or the database. */
final class Diagnostics {
    static final int EXPORT=518;
    static String environment(Context context){
        String app="unknown",douyin="unknown";
        try{app=context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(Exception ignored){}
        try{douyin=context.getPackageManager().getPackageInfo(AutomationService.DY,0).versionName;}catch(Exception ignored){}
        android.util.DisplayMetrics d=context.getResources().getDisplayMetrics();
        return "软件="+app+"；设备="+Build.MANUFACTURER+"/"+Build.MODEL+"；Android="+Build.VERSION.RELEASE+"/API"+Build.VERSION.SDK_INT+"；抖音="+douyin+"；屏幕="+d.widthPixels+"x"+d.heightPixels+"；density="+d.density+"；fontScale="+context.getResources().getConfiguration().fontScale;
    }
    static String taskSettings(JSONObject config){
        JSONObject safe=new JSONObject();
        for(String key:new String[]{"keyword","interactionMode","quantity","limit","perVideo","profiles","sort","published","duration","scope","form","distance","aiMode","identity","target","startTime","endTime","likeWorks","commentWorks","aiComment","filterOnly","scanOnly"})
            if(config.has(key))try{safe.put(key,config.opt(key));}catch(Exception ignored){}
        return safe.toString();
    }
    static void buttons(Activity activity,LinearLayout parent,WorkspaceUi ui){
        ui.button(parent,"导出诊断包（日志和页面结构）",false).setOnClickListener(v->{
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,"518诊断_"+new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.ROOT).format(new Date())+".zip");
            activity.startActivityForResult(intent,EXPORT);
        });
        ui.button(parent,"复制最近日志",false).setOnClickListener(v->{
            Records db=new Records(activity);String log=db.logs();db.close();
            ((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("518诊断日志",environment(activity)+"\n"+log));
            Toast.makeText(activity,"日志已复制",Toast.LENGTH_SHORT).show();
        });
    }
    static boolean result(Activity activity,int request,int result,Intent data){
        if(request!=EXPORT)return false;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;
        new Thread(()->{
            try(OutputStream stream=activity.getContentResolver().openOutputStream(data.getData());ZipOutputStream zip=new ZipOutputStream(stream)){
                Context context=activity.getApplicationContext();SharedPreferences prefs=context.getSharedPreferences("workspace",0);
                put(zip,"environment.txt",environment(context)+"\n任务="+prefs.getString("lastTask","")+"\n状态="+prefs.getString("task_status","")+"\n无障碍已连接="+(AutomationService.instance!=null)+"\n诊断包包括评论页面文字和结构，请只发送给排查人员。不包含账号令牌、AI Key 或评论数据库。\n");
                Records db=new Records(context);try{zip.putNextEntry(new ZipEntry("logs.txt"));db.writeLogs(zip,20000);zip.closeEntry();}finally{db.close();}
                File folder=context.getExternalFilesDir("xml");File[] files=folder==null?null:folder.listFiles((dir,name)->name.endsWith(".xml"));
                StringBuilder included=new StringBuilder();
                if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());long bytes=0;int count=0;
                    for(File f:files){if(count>=80||bytes+f.length()>20*1024*1024)continue;zip.putNextEntry(new ZipEntry("xml/"+f.getName()));try(InputStream in=new FileInputStream(f)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)zip.write(buffer,0,n);}zip.closeEntry();bytes+=f.length();count++;included.append(f.getName()).append('\n');}
                }
                put(zip,"included-files.txt",included.toString());
                activity.runOnUiThread(()->Toast.makeText(activity,"诊断包已保存，可发送此 ZIP 文件排查",Toast.LENGTH_LONG).show());
            }catch(Exception e){activity.runOnUiThread(()->Toast.makeText(activity,"诊断包保存失败："+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show());}
        },"diagnostics-export").start();return true;
    }
    static void put(ZipOutputStream zip,String name,String text)throws IOException{zip.putNextEntry(new ZipEntry(name));zip.write(text.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
}
