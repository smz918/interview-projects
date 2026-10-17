package dy.lead.client;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.SharedPreferences;
import org.json.JSONArray;

/** Persistent network retry for task diagnostics, including recovery after the app process is killed. */
public final class DiagnosticUploadJob extends JobService {
    private volatile boolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        stopped=false;
        new Thread(()->runUpload(params),"diagnostic-upload-retry").start();
        return true;
    }

    private void runUpload(JobParameters params) {
        boolean retry=false;
        Records db=new Records(this);
        try {
            SharedPreferences prefs=getSharedPreferences("workspace",0);
            if(prefs.getBoolean("running",false)) {
                AutomationService service=AutomationService.instance;
                if(service==null||!service.running) {
                    String task=prefs.getString("lastTask","");
                    if(task.matches("[0-9]{10,20}")) {
                        db.setTask(task);
                        db.log("[中断恢复] 系统未收到任务正常结束；后台补传诊断包");
                        db.setTask("");
                    }
                    prefs.edit().putBoolean("running",false).putString("task_status","任务进程异常结束；诊断包由后台任务自动补传").apply();
                } else {
                    retry=true;
                }
            }
            if(!retry) {
                JSONArray tasks=db.tasks();
                String last=prefs.getString("lastTask","");
                if(last.matches("[0-9]{10,20}")&&!prefs.getBoolean("task_diagnostic_uploaded_"+last,false)) {
                    boolean found=false;for(int i=0;i<tasks.length();i++)if(last.equals(tasks.optJSONObject(i).optString("task"))){found=true;break;}
                    if(!found)tasks.put(new org.json.JSONObject().put("task",last));
                }
                for(int i=0;i<tasks.length()&&!stopped;i++) {
                    String task=tasks.optJSONObject(i).optString("task");
                    if(!prefs.getBoolean("task_diagnostic_uploaded_"+task,false)&&!Diagnostics.uploadTaskNow(this,db,task))retry=true;
                }
            }
        } catch(Exception e) {
            retry=true;
            android.util.Log.w("LeadAutomation","Diagnostic retry failed: "+e.getClass().getSimpleName());
        } finally {
            db.close();
            if(!retry&&!stopped)Diagnostics.cancelRetry(this);
            jobFinished(params,retry&&!stopped);
        }
    }

    @Override public boolean onStopJob(JobParameters params) { stopped=true;return true; }
}
