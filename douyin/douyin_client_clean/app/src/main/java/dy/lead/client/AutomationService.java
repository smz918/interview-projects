package dy.lead.client;

import android.accessibilityservice.*;
import android.app.KeyguardManager;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit, bounded UI states. No fallback to arbitrary screen coordinates or social actions. */
public class AutomationService extends AccessibilityService {
    public static AutomationService instance;
    static final String DY="com.ss.android.ugc.aweme";
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService network=Executors.newSingleThreadExecutor();
    final ExecutorService ai=Executors.newSingleThreadExecutor();
    SharedPreferences prefs; Records db; JSONObject config;
    boolean running,authBusy; volatile int generation; int pendingAi;
    boolean paused,resumeRequested;long pausedAt,resumeRequestedAt;int captureEpoch;
    int taskScanned,taskNew,taskExisting;
    long lastProgressAt;
    String state="IDLE",taskId="",video="",pendingKey="",snapshotState=""; boolean interactionProfileWork=false,interactionFollowDone=false;
    String interactionDraft=""; boolean interactionLikeRequested=false;
    int interactionWorksDone=0; boolean interactionNoMoreWorks=false;
    final Set<String> interactionCustomers=new HashSet<>();
    String interactionAccount=""; boolean interactionSendRequested=false;
    long began,stateAt,lease,nextAuth; int done,videoComments,idle,filterIndex,interactionScrolls; boolean resetFilters;
    final Set<String> seenVideos=new HashSet<>(),seenComments=new HashSet<>();
    List<String> filters; String filterPending=""; int filterScroll;
    String filterPhase="WAIT_PANEL"; long filterAt,notBefore,missingSince; int recoveries;
    boolean visionBusy,visualFilters; FilterPage visualPage; long visualAt,visionStarted;
    boolean nativeFilterObserved;long filterWaitLogAt;int filterWaitSnapshots;
    boolean capturePermissionPending; long capturePermissionAt;
    CommentPage commentPageCache;
    static final int AI_MAX_RETRIES=5;
    static final long AI_FIRST_RETRY_DELAY_MS=1000L;
    long commentUnresolvedAt;int commentStalls,commentFrames,commentExpandAttempts;String commentBeforeSwipe="";boolean commentSwipePending,commentSwipeCompleted,commentBottomObserved;
    long aiOffscreenLoggedAt;
    final WindowReadRecovery windowRecovery=new WindowReadRecovery();
    final CommentPanelRecovery panelRecovery=new CommentPanelRecovery();
    final NoteCommentRecovery noteRecovery=new NoteCommentRecovery();
    int commentLayoutAttempts;boolean commentLayoutPending;
    long commentEmptyAt;String commentEmptyReason="";
    boolean regressionPanelClosed;
    String foregroundPackage="",windowDiagnostic="";long foregroundAt;
    Rect lastCommentRegion;
    ScreenText screenText; int captureNumber,snapshotNumber,returnAttempts; final List<AccessibilityNodeInfo> roots=new ArrayList<>();
    WindowManager wm; LinearLayout overlay; TextView overlayText; Button overlayStop,overlayPause;
    List<AccessibilityNodeInfo> nodes=new ArrayList<>();
    final Runnable loop=()->tick();
    DirectMessageRunner messages;
    static boolean development(Context c){return BuildConfig.LOCAL_REGRESSION&&(c.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;}

    @Override protected void onServiceConnected(){instance=this;prefs=getSharedPreferences("workspace",0);db=new Records(this);wm=(WindowManager)getSystemService(WINDOW_SERVICE);if(!running){db.recoverMessages();if(prefs.getBoolean("running",false)){String reason="上次任务运行中断，未收到正常结束记录；上次阶段="+prefs.getString("task_stage","未知")+"；请导出诊断包排查系统关闭/进程退出";db.log("[中断恢复] task="+prefs.getString("lastTask","")+"；"+reason);prefs.edit().putBoolean("running",false).putString("task_status",reason).apply();}}db.log("自动化服务已连接");}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){
        if(e.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){String pkg=str(e.getPackageName());
            if(!pkg.isEmpty()&&!pkg.equals(getPackageName())){if(running&&!pkg.equals(foregroundPackage)&&db!=null)db.log("[前台切换] "+foregroundPackage+" → "+pkg+"；事件页面="+str(e.getClassName())+"；阶段="+state+"；此事件不能单独证明是谁触发退出");foregroundPackage=pkg;foregroundAt=SystemClock.elapsedRealtime();}}
    }
    @Override public void onInterrupt(){finish("无障碍服务被中断");}
    @Override public void onDestroy(){finish("无障碍服务已关闭");dismissOverlay();instance=null;network.shutdownNow();ai.shutdownNow();if(screenText!=null)screenText.close();super.onDestroy();}

    public void startTask(JSONObject request){
        if(running){db.log("任务已经在运行");return;}
        paused=false;resumeRequested=false;
        if(!development(this)&&prefs.getString("token","").isEmpty()){db.log("请先登录");return;}
        try{messages=request.optBoolean("dmMode")?new DirectMessageRunner(this,request):null;}catch(Exception e){db.log("私信参数无效："+e.getMessage());return;}
        try{for(String key:new String[]{"identity","target","aiMode"})if(!request.has(key))request.put(key,prefs.getString(key.equals("aiMode")?"ai_mode":key,key.equals("aiMode")?"均衡":""));}catch(JSONException e){db.log("[ai] 任务判断条件读取失败，未启动任务");return;}
        config=request;interactionProfileWork=false;interactionFollowDone=false;interactionWorksDone=0;interactionNoMoreWorks=false;interactionCustomers.clear();interactionAccount="";interactionSendRequested=false;generation++;running=true;began=SystemClock.elapsedRealtime();lease=0;nextAuth=0;done=0;idle=0;
        taskId=String.valueOf(System.currentTimeMillis());seenVideos.clear();seenComments.clear();pendingAi=0;taskScanned=0;taskNew=0;taskExisting=0;videoComments=0;video="";snapshotState="";recoveries=0;missingSince=0;notBefore=0;visualPage=null;visualFilters=false;visionBusy=false;captureNumber=0;snapshotNumber=0;returnAttempts=0;
        if(messages==null)prefs.edit().putString("task_name_"+taskId,prefs.getString("task_name","新任务")).apply();
        filters=new ArrayList<>();for(String key:new String[]{"sort","published","duration","scope","form","distance"})filters.add(config.optString(key,"不限").replace(" ",""));
        prefs.edit().putBoolean("running",true).putString("lastTask",taskId).apply();showOverlay();setState("AUTH");
        db.log("[worker] 任务开始："+config.optString("keyword")+(config.optBoolean("interactionMode",false)?"；模式=互动助手，关注="+config.optBoolean("follow")+"，点赞="+config.optBoolean("likeWorks")+"，评论="+config.optBoolean("commentWorks")+"，AI文案="+config.optBoolean("aiComment"):""));
        db.log("[环境] task="+taskId+"；"+Diagnostics.environment(this));
        db.log("[配置] "+Diagnostics.taskSettings(config));
        windowRecovery.reset();resetCommentScan();regressionPanelClosed=false;
        if(development(this)){lease=Long.MAX_VALUE;nextAuth=Long.MAX_VALUE;if(messages!=null)messages.start();else{setState("SEARCH");relaunchDouyin();}db.log("[worker] 本地开发测试，登录与远程授权暂不启用");}else checkAuth();main.postDelayed(loop,1000);
    }
    public void finish(String reason){
        paused=false;resumeRequested=false;
        if(!running)return;if(messages!=null)messages.stop(reason);running=false;generation++;main.removeCallbacks(loop);String finalReason=messages==null?reason+"；完成视频 "+done:reason;prefs.edit().putBoolean("running",false).putString("task_status",finalReason).apply();db.log(finalReason);
        db.log("[停止] task="+taskId+"；阶段="+state+"；原因="+reason+"；耗时="+((SystemClock.elapsedRealtime()-began)/1000)+"秒；完成视频="+done+"；本视频评论="+videoComments+"；待处理AI="+pendingAi);
        db.log("[计数汇总] 本次扫描="+taskScanned+"；新增保存="+taskNew+"；已有记录="+taskExisting+"；历史保存总数="+db.count()+"；已保存记录不随任务停止删除");
        if(overlay!=null){overlayText.setText("518 · 已停止\n"+finalReason);overlayText.setMaxLines(7);overlayStop.setText("关闭提示");overlayStop.setOnClickListener(v->dismissOverlay());}
        if(overlayPause!=null)overlayPause.setVisibility(View.GONE);
        prefs.edit().putBoolean("paused",false).apply();
        capturePermissionPending=false;stopService(new Intent(this,LegacyCaptureService.class));
    }
    public void togglePause(){if(!running)return;if(paused)requestResume();else pauseTask("用户主动暂停");}
    static void refreshPauseButton(Button button){
        if(button==null)return;AutomationService s=instance;
        button.setEnabled(s!=null&&s.running&&!s.resumeRequested);
        button.setText(s!=null&&s.resumeRequested?"正在恢复抖音…":s!=null&&s.paused?"继续任务":"暂停任务");
    }
    public void pauseTask(String reason){
        if(!running||paused)return;
        paused=true;pausedAt=SystemClock.elapsedRealtime();resumeRequested=false;captureEpoch++;
        visualPage=null;visionBusy=false;commentPageCache=null;
        if(messages!=null)messages.evidenceAt=0;
        if(overlay!=null)overlay.setVisibility(View.VISIBLE);
        prefs.edit().putBoolean("paused",true).apply();
        db.log("[暂停] task="+taskId+"；阶段="+state+"；原因="+reason+"；已扫="+taskScanned+"；待判="+pendingAi+"；私信已请求发送="+(messages!=null&&messages.dispatched)+"；保留当前任务，暂停期间不操作屏幕");
        status("已暂停 · 点击继续任务");if(overlayPause!=null)overlayPause.setText("继续任务");
    }
    public void requestResume(){
        if(!running||!paused||resumeRequested)return;
        if(((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()){status("已暂停 · 请解锁后继续");return;}
        resumeRequested=true;resumeRequestedAt=SystemClock.elapsedRealtime();
        refreshPauseButton(overlayPause);
        status("已暂停 · 正在恢复抖音原页面");
        if(!resumeDouyinTask()){resumeRequested=false;refreshPauseButton(overlayPause);status("已暂停 · 请手动返回抖音后继续");}
        main.removeCallbacks(loop);main.postDelayed(loop,700);
    }
    void resumeIfReady(long now){
        if(!resumeRequested)return;
        collectWindows();
        if(roots.isEmpty()){
            if(now-resumeRequestedAt>15000){resumeRequested=false;refreshPauseButton(overlayPause);status("已暂停 · 未恢复抖音，请返回原页面后继续");db.log("[继续等待] 抖音窗口未恢复，保持暂停，不结束任务；"+windowDiagnostic);}
            return;
        }
        long duration=now-pausedAt;
        began=PauseTime.shift(began,pausedAt,now);stateAt=PauseTime.shift(stateAt,pausedAt,now);
        filterAt=PauseTime.shift(filterAt,pausedAt,now);notBefore=PauseTime.deadline(notBefore,duration);
        commentUnresolvedAt=PauseTime.shift(commentUnresolvedAt,pausedAt,now);
        commentEmptyAt=PauseTime.shift(commentEmptyAt,pausedAt,now);
        capturePermissionAt=PauseTime.shift(capturePermissionAt,pausedAt,now);
        // Preserve recovery age without counting the same pause twice on window recovery.
        windowRecovery.began=PauseTime.shift(windowRecovery.began,pausedAt,now);
        windowRecovery.roundAt=PauseTime.shift(windowRecovery.roundAt,pausedAt,now);
        windowRecovery.loggedAt=PauseTime.shift(windowRecovery.loggedAt,pausedAt,now);
        panelRecovery.nextAttempt=PauseTime.deadline(panelRecovery.nextAttempt,duration);
        if(messages!=null){messages.waitUntil=PauseTime.deadline(messages.waitUntil,duration);messages.lastSubmitAt=PauseTime.shift(messages.lastSubmitAt,pausedAt,now);messages.evidenceAt=0;}
        if(messages!=null&&messages.dispatched&&state.equals("DM_VERIFY"))stateAt=now;
        paused=false;resumeRequested=false;visualPage=null;snapshotState="";notBefore=Math.max(notBefore,now+1200);
        prefs.edit().putBoolean("paused",false).apply();refreshPauseButton(overlayPause);
        db.log("[继续] task="+taskId+"；原阶段="+state+"；暂停="+duration/1000+"秒；保留已扫="+taskScanned+"；未重新创建任务或重搜；已发送请求只核验、不重发");
        status(messages==null?state:messages.summary()+" · "+state);
        if(!development(this)&&now>=nextAuth)checkAuth();
    }
    String otherForegroundApp(){
        for(AccessibilityWindowInfo w:getWindows())if(w.isActive()&&w.getType()==AccessibilityWindowInfo.TYPE_APPLICATION){
            AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;String pkg=str(root.getPackageName());root.recycle();
            if(!pkg.isEmpty()&&!DY.equals(pkg)&&!pkg.equals("com.android.systemui"))return pkg;
        }
        return "";
    }
    void dismissOverlay(){if(overlay!=null){try{wm.removeView(overlay);}catch(Exception ignored){}overlay=null;}}
    void setState(String s){String old=state;state=s;stateAt=SystemClock.elapsedRealtime();idle=0;status(s);prefs.edit().putString("task_stage",s).apply();if(db!=null&&!old.equals(s))db.log("[状态] task="+taskId+"；"+old+" → "+s);}
    String progressText(String s){
        if(s.equals("AI_WAIT"))s="等待意向判断 "+((SystemClock.elapsedRealtime()-stateAt)/1000)+"秒";
        else if(s.equals("COMMENTS"))s="扫描评论";
        else if(s.equals("PROFILE"))s="获取意向客户抖音号";
        else if(s.equals("RETURN_COMMENTS"))s="返回原评论区";
        return s+"\n视频已完成 "+done+" · 本页已扫 "+videoComments+"\n本次扫描 "+taskScanned+" · 新增 "+taskNew+"\n已有记录 "+taskExisting+" · 待判 "+pendingAi;
    }
    void status(String s){if(paused&&!s.startsWith("已暂停"))s="已暂停 · 点击继续任务";String progress=messages==null?progressText(s):s;lastProgressAt=SystemClock.elapsedRealtime();prefs.edit().putString("task_status",progress).apply();if(overlayText!=null)overlayText.setText(progress);}
    void showOverlay(){
        dismissOverlay();
        overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(10,8,10,8);overlay.setBackgroundColor(0xeef5f7fa);
        TextView handle=new TextView(this);handle.setText("518 · 按住此处拖动");handle.setTextColor(0xff526b83);handle.setTextSize(11);handle.setPadding(4,6,4,12);overlay.addView(handle);
        overlayText=new TextView(this);overlayText.setTextColor(Color.BLACK);overlayText.setTextSize(11);overlay.addView(overlayText);
        overlayPause=new Button(this);overlayPause.setText(paused?"继续任务":"暂停任务");overlayPause.setTextSize(12);overlayPause.setOnClickListener(v->togglePause());overlay.addView(overlayPause);
        overlayStop=new Button(this);overlayStop.setText("停止任务");overlayStop.setTextSize(12);overlayStop.setOnClickListener(v->finish("用户停止"));overlay.addView(overlayStop);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(270,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.LEFT|Gravity.TOP;p.x=0;p.y=0;p.width=(int)(getResources().getDisplayMetrics().density*170);wm.addView(overlay,p);
        OverlayDrag drag=new OverlayDrag(wm,overlay,p,prefs);handle.setOnTouchListener(drag);overlayText.setOnTouchListener(drag);overlay.post(drag::restore);
    }
    void previewOverlay(){if(!development(this)||running)return;showOverlay();overlayText.setText("悬浮窗拖动测试\n按住标题或文字移动\n未启动扫描任务");overlayStop.setText("关闭提示");overlayStop.setOnClickListener(v->dismissOverlay());db.log("[界面回归] 仅显示可拖动悬浮窗，不启动任务");}
    void checkAuth(){
        if(authBusy)return;
        long now=SystemClock.elapsedRealtime();
        if(lease>now){nextAuth=lease;return;}
        final String token=prefs.getString("token","");long remaining=lease==0?AuthorizationCache.remaining(prefs,token):0;
        if(remaining>0){lease=nextAuth=SystemClock.elapsedRealtime()+remaining;return;}
        authBusy=true;final int g=generation;
        db.log("[授权] 首次或距上次成功检查已满24小时，开始验证后台权限");
        network.execute(()->{boolean active=false;String error="授权检查失败";
            try {String id=android.os.Build.MANUFACTURER+"-"+android.os.Build.MODEL+"-"+android.provider.Settings.Secure.getString(getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);
                JSONObject body=new JSONObject().put("app_version",BuildConfig.VERSION_NAME).put("running_task",taskId);
                JSONObject r=MainActivity.callApi("/api/v1/device/"+java.net.URLEncoder.encode(id,"UTF-8")+"/heartbeat",body,token);active="active".equals(r.optString("status"));error=r.optString("message","设备授权已停用");
            }catch(Exception e){error="授权服务器检查失败："+e.getMessage();}
            final boolean ok=active;final String msg=error;main.post(()->{
                authBusy=false;
                if(!token.equals(prefs.getString("token",""))){if(running)checkAuth();return;}
                if(ok)AuthorizationCache.save(prefs,token);
                if(g!=generation||!running){if(running)checkAuth();return;}
                if(!ok){SharedPreferences.Editor edit=prefs.edit().putString("auth_status",msg);AuthorizationCache.clear(edit);edit.apply();finish(msg);return;}
                prefs.edit().putString("auth_status","已授权").apply();lease=nextAuth=SystemClock.elapsedRealtime()+AuthorizationPolicy.INTERVAL_MS;
                db.log("[授权] 检查通过；24小时内复用本次结果，新建任务和暂停继续不重复请求");
            });
        });
    }
    void tick(){
        if(!running)return;
        try {
            long now=SystemClock.elapsedRealtime();
            if(paused){if(!development(this)&&now>=nextAuth)checkAuth();resumeIfReady(now);return;}
            if(state.equals("FILTER")&&filterPhase.equals("WAIT_RESULTS")&&now-filterWaitLogAt>=5000){
                filterWaitLogAt=now;db.log("[筛选等待] WAIT_RESULTS；已等="+((now-filterAt)/1000)+"秒；OCR忙="+visionBusy+"；授权检查忙="+authBusy+"；前台="+foregroundPackage+"；待稳定="+Math.max(0,notBefore-now)+"ms");
            }
            if(!state.equals("AUTH")&&!capturePermissionPending&&now>=notBefore){String other=otherForegroundApp();if(!other.isEmpty()){pauseTask("切换到其他应用："+other);return;}}
            if(state.equals("AI_WAIT")&&now-lastProgressAt>=2000)status(state);
            // Interaction assistant has no artificial runtime limit; acquisition tasks
            // still use their explicit quantity/video limit as before.
            if(!config.optBoolean("quantity",true)&&config.has("limit")&&now-began>=config.optInt("limit",1)*60000L){finish("达到运行时长");return;}
            if(state.equals("AUTH")){if(lease>now&&!authBusy){if(messages!=null)messages.start();else{setState("SEARCH");relaunchDouyin();}}else if(now-stateAt>20000)finish("授权连接超时");return;}
            if(now>=nextAuth)checkAuth();
            // At the daily boundary wait for a fresh result before any screen
            // action. The old 35-second lease no longer applies.
            if(now>=lease)return;
            if(state.equals("DRAIN")){if(pendingAi==0){finish("达到视频数量上限，AI 判断完成");}else if(now-stateAt>120000){finish("AI 等待超时，未完成记录保留待判断");}return;}
            if(((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked()){pauseTask("手机已锁屏，解锁后可继续");return;}
            if(messages!=null&&messages.waiting())return;
            if(capturePermissionPending){if(now-capturePermissionAt>120000)finish("[capture] 屏幕捕获授权等待超时；请重新运行，并在系统提示中允许屏幕捕获");return;}
            if(visionBusy){if(now-visionStarted>15000)finish("本机文字识别等待超时，已停止");return;}
            if(now<notBefore)return;
            collectWindows();if(roots.isEmpty()){
                // Network retries and AI backoff do not require Douyin to remain foreground.
                // Keep the task alive while the current comment is being judged; otherwise a
                // transient app switch can discard a valid scan after it was already persisted.
                if(state.equals("AI_WAIT")&&IntentPolicy.pending(db.comment(pendingKey).optString("ai"))){
                    if(now-aiOffscreenLoggedAt>10000){db.log("[ai] 抖音暂不在前台，继续等待后台判断重试；已扫描记录已保存");aiOffscreenLoggedAt=now;}
                    return;
                }
                recoverUnreadableWindow(now);return;
            }
            if(windowRecovery.began>=0){long paused=windowRecovery.duration(now);db.log("[窗口恢复] 已重新读到抖音窗口；等待="+(paused/1000)+"秒；重试="+windowRecovery.rounds+"；继续原阶段="+state+"；保留已扫="+videoComments);stateAt+=paused;filterAt+=paused;if(commentUnresolvedAt>0)commentUnresolvedAt+=paused;windowRecovery.reset();}
            missingSince=0;
            if(!snapshotState.equals(state)){snapshotAll(state);snapshotState=state;}
            if(messages!=null){messages.tick();return;}
            if(state.equals("AI_WAIT")){awaitIntent();return;}
            if(findText("请完成验证")!=null||findText("拖动滑块")!=null||findText("操作太频繁")!=null){finish("抖音要求验证或提示操作频繁，请手动处理");return;}
            if(config.optBoolean("interactionMode",false)&&now-stateAt>45000&&!state.equals("FILTER")&&!state.equals("COMMENTS")){
                snapshotAll("INTERACTION_STATE_TIMEOUT");finish("[互动] 状态等待超时："+state+"；未重搜关键词，保留现场供排查");return;
            }
            if(now-stateAt>45000&&!state.equals("FILTER")&&!state.equals("COMMENTS")&&!config.optBoolean("interactionMode",false)){if(++recoveries>2){snapshotAll("TIMEOUT");finish("页面等待超时："+state+"，已停止，XML 已保存");return;}db.log("[recover] 页面等待超时："+state+"，返回搜索重新开始");workerBack();setState("SEARCH");relaunchDouyin();return;}
            switch(state){
                case "SEARCH":search();break;
                case "SUBMIT":submit();break;
                case "RESULTS":results();break;
                case "FILTER":filter();break;
                case "OPEN":openVideo();break;
                case "VIDEO":openComments();break;
                case "COMMENTS":comments();break;
                case "PROFILE":profile();break;
                case "INTERACTION_WORK":interactionWork();break;
                case "INTERACTION_SEND":interactionSend();break;
                case "INTERACTION_SEND_READY":interactionSendReady();break;
                case "INTERACTION_SEND_VERIFY":interactionSendVerify();break;
                case "INTERACTION_RETURN":returnInteraction();break;
                case "RETURN_COMMENTS":returnComments();break;
                case "CLOSE_COMMENTS":workerBack();setState("BACK_RESULTS");break;
                case "BACK_RESULTS":if(isResults())setState("OPEN");else if(findDescPrefix("评论")!=null){workerBack();settle(2200);}break;
            }
        }catch(Exception e){db.log("[异常] task="+taskId+"；state="+state+"；"+android.util.Log.getStackTraceString(e));snapshotAll("EXCEPTION");finish("任务异常，已停止，请导出诊断包");}
        finally{for(AccessibilityNodeInfo n:nodes)n.recycle();nodes.clear();roots.clear();if(running)main.postDelayed(loop,700);}
    }
    void relaunchDouyin(){
        try{Intent i=getPackageManager().getLaunchIntentForPackage(DY);if(i==null){finish("未安装抖音");return;}db.log("[导航动作] 启动抖音；阶段="+state);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);settle(3000);}catch(Exception e){db.log("[recover] 拉起抖音失败");}
    }
    boolean desktopForeground(){
        android.content.pm.ResolveInfo home=getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        return home!=null&&home.activityInfo!=null&&home.activityInfo.packageName.equals(foregroundPackage);
    }
    boolean resumeDouyinTask(){
        try{Intent intent=getPackageManager().getLaunchIntentForPackage(DY);if(intent==null)return false;
            // Launcher semantics bring an existing detail task forward without CLEAR_TOP.
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);startActivity(intent);if(!paused)settle(3000);
            db.log("[恢复前台] 已请求恢复抖音原任务；未重新搜索、未清除已扫描记录；返回后重新验证页面");return true;
        }catch(Exception e){db.log("[窗口重试] 恢复抖音失败："+e.getClass().getSimpleName());return false;}
    }
    void workerBack(){if(paused)return;db.log("[导航动作] 脚本请求系统返回；阶段="+state+"；前台="+foregroundPackage);performGlobalAction(GLOBAL_ACTION_BACK);}
    void recoverUnreadableWindow(long now){
        windowRecovery.begin(now);
        if(windowRecovery.loggedAt<0||now-windowRecovery.loggedAt>=10000){
            db.log("[窗口等待] 阶段="+state+"；已等="+(windowRecovery.duration(now)/1000)+"秒；本轮剩余="+((windowRecovery.remaining(now)+999)/1000)+"秒；恢复轮次="+windowRecovery.rounds+"/3；"+windowDiagnostic);
            windowRecovery.loggedAt=now;
            status("等待抖音窗口恢复 · "+((windowRecovery.remaining(now)+999)/1000)+"秒 · 重试 "+windowRecovery.rounds+"/3");
        }
        if(!windowRecovery.due(now))return;
        if(windowRecovery.exhausted()){finish("[窗口异常] 每轮等待40秒、恢复重试3次后仍无法读取抖音窗口；未判定到底；"+windowDiagnostic);return;}
        windowRecovery.retried(now);
        if(desktopForeground()&&messages==null&&(state.equals("COMMENTS")||state.equals("AI_WAIT"))){resumeDouyinTask();return;}
        boolean allowed=DY.equals(foregroundPackage)&&messages==null&&(state.equals("COMMENTS")||state.equals("AI_WAIT"))&&lastCommentRegion!=null;
        if(!allowed){db.log("[窗口重试] 第"+windowRecovery.rounds+"/3轮；未确认抖音前台评论区域，不滑动其他页面；再等待40秒；"+windowDiagnostic);return;}
        int[] swipe=CommentSwipe.path(lastCommentRegion.left,lastCommentRegion.top,lastCommentRegion.right,lastCommentRegion.bottom);
        if(swipe==null){db.log("[窗口重试] 原评论区域不可用；再等待40秒");return;}
        Path path=new Path();path.moveTo(swipe[0],swipe[1]);path.lineTo(swipe[0],swipe[2]);final int g=generation;
        boolean accepted=dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,720)).build(),new GestureResultCallback(){
            public void onCompleted(GestureDescription gesture){if(g==generation)db.log("[窗口重试] 恢复滑动完成，继续重新读取窗口，不作到底处理");}
            public void onCancelled(GestureDescription gesture){if(g==generation)db.log("[窗口重试] 恢复滑动被系统取消，继续等待窗口");}
        });
        db.log("[窗口重试] 第"+windowRecovery.rounds+"/3轮；40秒无窗口，按上次已确认评论区域向后扫描；手势提交="+accepted+"；区域="+lastCommentRegion+"；再等待40秒");settle(2200);
    }
    void search(){
        if(findText("排序依据")!=null){workerBack();settle(2200);return;}
        AccessibilityNodeInfo input=searchInput();
        if(input!=null){
            if(!input.isFocused()){click(input);return;}
            Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,config.optString("keyword"));
            if(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){setState("SUBMIT");settle(2000);db.log("已输入关键词");return;}
            db.log("[search] 输入控件暂未接受文本，等待焦点稳定");return;
        }
        AccessibilityNodeInfo search=findText("搜索");if(search==null)search=findDescPrefix("搜索");
        if(search!=null){click(search);return;}workerBack();settle(2000);
    }
    AccessibilityNodeInfo searchInput(){AccessibilityNodeInfo input=findId("et_search_kw");if(input!=null)return input;for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&str(n.getClassName()).equals("android.widget.EditText")&&rect(n).bottom<screenHeight()*.25f)return n;return null;}
    void submit(){AccessibilityNodeInfo input=searchInput();if(input==null)return;if(!config.optString("keyword").equals(str(input.getText())))return;AccessibilityNodeInfo b=findId("5db");if(b==null)b=findText("搜索");if(b!=null&&click(b)){setState("RESULTS");settle(3000);}}
    boolean isResults(){return findText("综合")!=null&&findText("视频")!=null&&searchInput()!=null;}
    void results(){
        if(!isResults())return;
        if(config.optBoolean("interactionMode",false)&&!needsInteractionFilter()){
            db.log("[filter] 互动助手未设置有效筛选，跳过筛选面板");
            setState("OPEN");settle(1800);return;
        }
        AccessibilityNodeInfo filter=findDescPrefix("筛选");if(filter==null)return;
        boolean alreadyOpen=findText("排序依据")!=null&&findText("查看结果")!=null;
        if(alreadyOpen||click(filter)){filterIndex=0;filterScroll=0;filterPending="";visualFilters=false;setState("FILTER");filterStep("WAIT_PANEL");db.log(alreadyOpen?"[filter] 面板已经打开，直接校验":"[filter] 筛选入口只点击一次，等待面板");}
    }
    boolean needsInteractionFilter(){
        for(int i=0;i<filters.size();i++){
            String value=filters.get(i);
            if(value==null||value.isEmpty()||"不限".equals(value))continue;
            if(i==0&&"综合排序".equals(value))continue;
            return true;
        }
        return false;
    }
    void filter(){
        long now=SystemClock.elapsedRealtime();
        if(now-filterAt>18000){snapshotAll("FILTER_FAILED_"+filterPhase);finish("筛选等待超时："+filterPhase+"，停止而不重复开关面板");return;}
        FilterPage page=filterPage();if(page==null)return;
        if(filterPhase.equals("WAIT_RESULTS")){
            if(filterWaitSnapshots==0||(filterWaitSnapshots==1&&now-filterAt>=8000)){
                filterWaitSnapshots++;snapshotAll("FILTER_WAIT_RESULTS_"+filterWaitSnapshots);
                db.log("[筛选页面] 面板仍存在="+page.isPanel()+"；结果页="+isResults()+"；来源="+(page.visual?"OCR":"控件")+"；"+windowDiagnostic);
            }
            if(!page.isPanel()&&isResults()){
                snapshotAll("FILTER_RESULTS");db.log("[filter] 所有筛选已逐项验证，面板关闭并返回结果页");
                if(config.optBoolean("filterOnly",false)){finish("搜索筛选测试通过");return;}
                setState("OPEN");settle(2500);
            }return;
        }
        if(!page.isPanel())return;
        if(filterPhase.equals("WAIT_PANEL")){snapshotAll("FILTER_PANEL");filterStep("RESET");return;}
        if(filterPhase.equals("RESET")){
            FilterPage.Item reset=page.unique("重置选项");if(reset!=null&&clickItem(reset,page)){db.log("[filter] 已点击重置，接下来逐组校验");filterStep("SELECT");}return;
        }
        while(filterIndex<filters.size()&&FilterPage.isDefaultUnfiltered(filterIndex,filters.get(filterIndex))){
            db.log("[filter] 默认不限，无需定位选项："+FilterPage.GROUPS[filterIndex]);filterIndex++;filterScroll=0;
        }
        if(filterIndex>=filters.size()){
            FilterPage.Item apply=page.unique("查看结果");
            if(apply!=null&&clickItem(apply,page)){
                snapshotAll("FILTER_APPLY_CLICKED");
                db.log("[filter] 已点击查看结果，等待面板关闭；等待阶段=WAIT_RESULTS");
                filterStep("WAIT_RESULTS");settle(3000);
            }
            return;
        }
        String value=filters.get(filterIndex);if(filterIndex==0&&value.equals("不限"))value="综合排序";
        FilterPage.Item option=page.option(filterIndex,value);
        if(option==null){
            if(filterPhase.equals("VERIFY")){db.log("[filter] 校验未定位选项；组="+FilterPage.GROUPS[filterIndex]+"；期望="+value+"；来源="+(page.visual?"OCR":"控件")+"；"+page.diagnostic(filterIndex));if(now-filterAt>7500){snapshotAll("FILTER_VERIFY_MISSING");finish("点击后无法重新确认筛选："+value);}return;}
            // Scroll only inside an identified panel; never tap an assumed option coordinate.
            FilterPage.Item footer=page.unique("查看结果");
            if(filterScroll++<3&&footer!=null){scrollPanel(page,footer);return;}
            snapshotAll("FILTER_OPTION_MISSING");finish("无法定位筛选："+FilterPage.GROUPS[filterIndex]+" / "+value+"，未擅自替换条件");return;
        }
        if(option.selected){db.log("[filter] 已验证 "+FilterPage.GROUPS[filterIndex]+"="+value+"（"+(page.visual?"截图文字与选中色":"控件状态")+"）");filterIndex++;filterScroll=0;filterStep("SELECT");return;}
        if(filterPhase.equals("VERIFY")){
            if(now-filterAt>7500){snapshotAll("FILTER_UNCONFIRMED");finish("筛选选中状态未确认："+value+"，停止以避免误操作");}return;
        }
        if(clickItem(option,page)){db.log("[filter] 已点击 "+FilterPage.GROUPS[filterIndex]+"="+value);filterStep("VERIFY");}
    }
    void filterStep(String phase){filterPhase=phase;filterAt=SystemClock.elapsedRealtime();visualPage=null;if(phase.equals("WAIT_PANEL"))nativeFilterObserved=false;if(phase.equals("WAIT_RESULTS")){filterWaitLogAt=0;filterWaitSnapshots=0;}settle(1800);status("筛选 · "+phase);}
    void settle(long millis){notBefore=Math.max(notBefore,SystemClock.elapsedRealtime()+millis);visualPage=null;}
    void collectWindows(){
        commentPageCache=null;
        nodes.clear();roots.clear();Set<Integer> ids=new HashSet<>();
        AccessibilityNodeInfo active=getRootInActiveWindow();
        windowDiagnostic="活动根="+(active==null?"空":str(active.getPackageName()))+"；最近前台="+foregroundPackage+"；前台事件距今="+((SystemClock.elapsedRealtime()-foregroundAt)/1000)+"秒；窗口数="+getWindows().size();
        // Do not act on a background Douyin window behind another foreground app.
        if(active!=null&&!DY.equals(str(active.getPackageName()))&&!getPackageName().equals(str(active.getPackageName()))){foregroundPackage=str(active.getPackageName());foregroundAt=SystemClock.elapsedRealtime();active.recycle();return;}
        if(active!=null&&DY.equals(str(active.getPackageName()))){foregroundPackage=DY;foregroundAt=SystemClock.elapsedRealtime();}
        if(active!=null&&DY.equals(str(active.getPackageName()))){roots.add(active);ids.add(active.getWindowId());walk(active,nodes);}else if(active!=null)active.recycle();
        for(AccessibilityWindowInfo window:getWindows()){
            AccessibilityNodeInfo root=window.getRoot();if(root!=null){if(DY.equals(str(root.getPackageName()))&&ids.add(root.getWindowId())){roots.add(root);walk(root,nodes);}else root.recycle();}window.recycle();
        }
    }
    FilterPage filterPage(){
        List<FilterPage.Item> items=new ArrayList<>();
        for(AccessibilityNodeInfo node:nodes)if(node.isVisibleToUser()){
            Rect r=rect(node);String text=str(node.getText());String desc=str(node.getContentDescription());
            boolean selected=node.isSelected()||node.isChecked();
            // Android 16 / Douyin 40.3 exposes the option state only through
            // descriptions such as "已选中，最新发布，按钮". Extract the
            // semantic label instead of feeding the whole description to OCR.
            String descriptionLabel=FilterPage.descriptionLabel(desc);
            if(text.isEmpty())text=descriptionLabel.isEmpty()?desc:descriptionLabel;
            if(text.isEmpty()||r.isEmpty())continue;
            String parentDesc="";AccessibilityNodeInfo p=node.getParent();if(p!=null){selected|=p.isSelected()||p.isChecked();parentDesc=str(p.getContentDescription());p.recycle();}
            selected=FilterPage.nativeSelection(text,desc,parentDesc,selected);
            items.add(new FilterPage.Item(text,r.left,r.top,r.right,r.bottom,selected));
        }
        FilterPage nativePage=new FilterPage(items,false);
        if(nativePage.isPanel())nativeFilterObserved=true;
        if(nativePage.isPanel()){
            // Some apps expose text but not selected/checked: use pixels only for verification.
            if(!visualFilters&&!filterPhase.equals("VERIFY"))return nativePage;
            if(filterIndex<filters.size()){String value=filters.get(filterIndex);if(filterIndex==0&&"不限".equals(value))value="综合排序";FilterPage.Item selected=nativePage.option(filterIndex,value);if(selected!=null&&selected.selected)return nativePage;}
        }
        // After "查看结果" the panel is expected to disappear. Do not request
        // another screenshot merely because an earlier verification used OCR;
        // the native tree is the authoritative source for the transition.
        if(filterPhase.equals("WAIT_RESULTS")&&(!visualFilters||nativeFilterObserved))return nativePage;
        if(visualPage!=null&&SystemClock.elapsedRealtime()-visualAt<3000){
            FilterPage page=visualPage;visualPage=null;
            // Hybrid anchors: title nodes are often readable even when selection flags are absent.
            for(String group:FilterPage.GROUPS){FilterPage.Item anchor=nativePage.unique(group);if(anchor!=null&&page.unique(group)==null)page.items.add(anchor);}
            return page;
        }
        if(!visualFilters&&!visionBusy&&!capturePermissionPending)db.log(nativePage.isPanel()?"[filter] 筛选文字可读，但控件未提供选中状态；使用截图文字和颜色校验，Android "+Build.VERSION.RELEASE:"[filter] 当前控件未组成完整筛选面板，尝试截图识别；阶段="+filterPhase);
        requestScreen();return null;
    }
    void captureDenied(){capturePermissionPending=false;if(paused){db.log("[capture] 暂停期间屏幕捕获未授权，继续后按需重新申请");return;}finish("[capture] 未获得屏幕捕获授权，无法核验筛选选中状态；请重新运行并允许屏幕捕获（无障碍权限与此不同）");}
    void captureReady(){
        if(!running||!capturePermissionPending){stopService(new Intent(this,LegacyCaptureService.class));return;}
        capturePermissionPending=false;if(!paused){filterAt=SystemClock.elapsedRealtime();stateAt=filterAt;settle(1200);}
        db.log("[capture] 已获得屏幕捕获授权，继续原筛选步骤；识别仅在本机进行");
    }
    void recognizeScreen(Bitmap bitmap,int g,int epoch,String phase,String captureState){
        if(overlay!=null)overlay.setVisibility(View.VISIBLE);
        if(epoch!=captureEpoch||paused){if(bitmap!=null)bitmap.recycle();return;}
        if(!running||g!=generation){visionBusy=false;if(bitmap!=null)bitmap.recycle();return;}
        if(bitmap==null){visionBusy=false;finish("截图不可读取");return;}
        if(screenText==null)screenText=new ScreenText();
        screenText.read(bitmap,(page,error)->{
            if(epoch!=captureEpoch||paused)return;
            visionBusy=false;if(!running||g!=generation)return;
            if(page==null){finish(error);return;}
            if(!filterPhase.equals(phase)||!state.equals(captureState))return;
            visualPage=page;visualAt=SystemClock.elapsedRealtime();saveVisual(page,messages==null?phase:captureState);
        });
    }
    void requestScreen(){
        if(visionBusy||paused)return;
        if(Build.VERSION.SDK_INT<30&&!LegacyCaptureService.ready()){
            if(!capturePermissionPending){
                capturePermissionPending=true;capturePermissionAt=SystemClock.elapsedRealtime();
                db.log("[capture] Android "+Build.VERSION.RELEASE+" 使用兼容屏幕捕获；请在系统提示中允许，授权后继续原步骤");
                startActivity(new Intent(this,LegacyCaptureActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }return;
        }
        visualFilters=true;visionBusy=true;visionStarted=SystemClock.elapsedRealtime();final int g=generation,epoch=captureEpoch;final String phase=filterPhase;final String captureState=state;
        if(overlay!=null)overlay.setVisibility(View.INVISIBLE);
        main.postDelayed(()->{
            if(epoch!=captureEpoch||paused)return;
            if(!running||g!=generation){visionBusy=false;return;}
            if(Build.VERSION.SDK_INT<30){
                if(!LegacyCaptureService.ready()){visionBusy=false;if(overlay!=null)overlay.setVisibility(View.VISIBLE);captureDenied();return;}
                LegacyCaptureService.instance.capture((bitmap,error)->{
                    if(epoch!=captureEpoch||paused){if(bitmap!=null)bitmap.recycle();return;}
                    if(bitmap==null){visionBusy=false;if(overlay!=null)overlay.setVisibility(View.VISIBLE);if(running&&g==generation)finish("[capture] "+error);return;}
                    recognizeScreen(bitmap,g,epoch,phase,captureState);
                });return;
            }
            takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new TakeScreenshotCallback(){
                public void onSuccess(ScreenshotResult result){
                    Bitmap hardware=Bitmap.wrapHardwareBuffer(result.getHardwareBuffer(),result.getColorSpace());
                    Bitmap bitmap=hardware==null?null:hardware.copy(Bitmap.Config.ARGB_8888,false);
                    if(hardware!=null)hardware.recycle();result.getHardwareBuffer().close();
                    recognizeScreen(bitmap,g,epoch,phase,captureState);
                }
                public void onFailure(int error){if(epoch!=captureEpoch||paused)return;visionBusy=false;if(overlay!=null)overlay.setVisibility(View.VISIBLE);if(running&&g==generation)finish("截图权限或页面不支持截图，错误码 "+error);}
            });
        },250);
    }
    boolean clickItem(FilterPage.Item item,FilterPage page){
        // Even after OCR verification, prefer the exact native button in the
        // same frame. A submitted gesture alone does not prove a button click.
        AccessibilityNodeInfo target=null;
        for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&n.isEnabled()&&rect(n).contains(item.x(),item.y())&&FilterPage.normalize(str(n.getText())).equals(item.text)){
            if(target!=null){target=null;break;}target=n;
        }
        if(target!=null){
            if(item.text.equals("查看结果")){
                if(target.isClickable()&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){
                    settle(2000);db.log("[筛选点击] 查看结果；方式=原生控件；请求已接受；位置="+rect(target));return true;
                }
            }else if(click(target))return true;
        }
        if(!item.text.equals("查看结果"))return tap(item.x(),item.y());
        Point size=new Point();wm.getDefaultDisplay().getRealSize(size);if(item.x()<=0||item.y()<=0||item.x()>=size.x||item.y()>=size.y)return false;
        Path path=new Path();path.moveTo(item.x(),item.y());final int g=generation,epoch=captureEpoch;
        boolean accepted=dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,160)).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){if(g==generation&&epoch==captureEpoch)db.log("[筛选点击回调] 查看结果手势完成；仍需核验面板关闭");}
            @Override public void onCancelled(GestureDescription gesture){if(g==generation&&epoch==captureEpoch)db.log("[筛选点击回调] 查看结果手势被取消；未确认页面切换");}
        });
        db.log("[筛选点击] 查看结果；方式=坐标手势；提交="+accepted+"；位置="+item.x()+","+item.y());if(accepted)settle(2000);return accepted;
    }
    void highlightMatch(Rect bounds){
        android.graphics.drawable.GradientDrawable border=new android.graphics.drawable.GradientDrawable();border.setColor(0x22ff2442);border.setStroke(3,0xffff2442);
        View mark=new View(this);mark.setBackground(border);
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(bounds.width(),bounds.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=bounds.left;p.y=bounds.top;wm.addView(mark,p);
        main.postDelayed(()->{try{wm.removeView(mark);}catch(Exception ignored){}},900);
    }
    boolean tap(int x,int y){
        Point size=new Point();wm.getDefaultDisplay().getRealSize(size);if(x<=0||y<=0||x>=size.x||y>=size.y)return false;
        Path path=new Path();path.moveTo(x,y);boolean ok=dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,160)).build(),null);if(ok)settle(2000);return ok;
    }
    boolean dispatchTaskGesture(GestureDescription gesture,GestureResultCallback listener){
        if(paused)return false;
        // View global-visible bounds are relative to its own window, not the screen.
        // A movable overlay must never intercept a worker gesture, including its stop button.
        final LinearLayout view=overlay;final boolean visible=view!=null&&view.getVisibility()==View.VISIBLE;
        if(visible)view.setVisibility(View.INVISIBLE);
        GestureResultCallback callback=new GestureResultCallback(){
            void restore(){if(visible&&view==overlay)view.setVisibility(View.VISIBLE);}
            @Override public void onCompleted(GestureDescription g){restore();if(listener!=null)listener.onCompleted(g);}
            @Override public void onCancelled(GestureDescription g){restore();if(listener!=null)listener.onCancelled(g);}
        };
        boolean accepted=dispatchGesture(gesture,callback,main);if(!accepted&&visible&&view==overlay)view.setVisibility(View.VISIBLE);return accepted;
    }
    void scrollPanel(FilterPage page,FilterPage.Item footer){
        int top=footer.top;for(String heading:FilterPage.GROUPS){FilterPage.Item i=page.unique(heading);if(i!=null)top=Math.min(top,i.top);}
        int height=footer.top-top;if(height<100)return;
        Path p=new Path();p.moveTo(footer.x(),top+height*.8f);p.lineTo(footer.x(),top+height*.3f);
        dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,120,650)).build(),null);settle(2200);
    }
    void snapshotAll(String label){for(int i=0;i<roots.size();i++)snapshot(label+"_window"+i,roots.get(i));}
    void saveVisual(FilterPage page,String phase){
        try{java.io.File folder=getExternalFilesDir("xml");if(folder==null)return;
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,taskId+"_"+(++captureNumber)+"_"+phase+"_ocr.xml"))){
                org.xmlpull.v1.XmlSerializer x=android.util.Xml.newSerializer();x.setOutput(out,"UTF-8");x.startDocument("UTF-8",true);x.startTag(null,"screen");x.attribute(null,"source","on-device-ocr");
                for(FilterPage.Item i:page.items){x.startTag(null,"text");x.attribute(null,"value",i.text);x.attribute(null,"bounds",new Rect(i.left,i.top,i.right,i.bottom).toShortString());x.attribute(null,"selectedColor",String.valueOf(i.selected));x.endTag(null,"text");}x.endTag(null,"screen");x.endDocument();
            }
        }catch(Exception e){db.log("OCR XML 保存失败");}
    }
    void openVideo(){
        interactionProfileWork=false;
        if(findDescPrefix("评论")!=null&&!isResults()){setState("VIDEO");return;}
        if(!isResults())return;
        for(AccessibilityNodeInfo n:nodes){
            String id=str(n.getViewIdResourceName());String title=str(n.getText());Rect r=rect(n);
            if(!id.endsWith("/desc")||title.isEmpty()||r.top<260||r.bottom>screenHeight()-110)continue;
            String low=title.toLowerCase(java.util.Locale.ROOT);
            if(low.contains("直播")||low.contains("广告")||low.contains("广告")||low.contains("立即下载")||low.contains("商品")){db.log("[结果] 跳过疑似直播/广告/商品卡片："+title);continue;}
            String key=Records.key(title);if(seenVideos.contains(key))continue;
            if(!click(n))continue;
            seenVideos.add(key);video=title;videoComments=0;interactionScrolls=0;seenComments.clear();resetCommentScan();setState("VIDEO");db.log("[结果] 打开内容（视频/笔记通用）："+title);return;
        }
        if(++idle>5){finish("连续翻页未找到可识别的新视频");return;}scroll(false,.73f,.80f,.42f);stateAt=SystemClock.elapsedRealtime();
    }
    void openComments(){
        if(config.optBoolean("interactionMode",false)&&interactionProfileWork){
            // Verify an actual work page before touching its action bar.
            if(isCustomerProfile()||findId("eyi")==null){
                if(SystemClock.elapsedRealtime()-stateAt<8000){settle(1000);return;}
                db.log("[互动] 未确认作品页输入框，本客户仅尝试一次，退出");beginReturnComments();return;
            }
            if(config.optBoolean("likeWorks",true)&&!interactionLikeRequested){
                AccessibilityNodeInfo like=findId("guy");
                if(like!=null){
                    String desc=str(like.getContentDescription());interactionLikeRequested=true;
                    if(desc.contains("未点赞")){boolean clicked=click(like);db.log("[互动] 作品点赞请求="+clicked+"，不重复点击");settle(1500);return;}
                    db.log(desc.contains("已点赞")||desc.contains("已赞")?"[互动] 作品已点赞":"[互动] 点赞状态不明，未切换点赞状态");
                }else {interactionLikeRequested=true;db.log("[互动] 未找到作品点赞控件，继续直接评论");}
            }
            db.log("[互动] 客户作品页使用底部直接评论输入框，不打开评论区面板");
            setState("INTERACTION_SEND");settle(900);return;
        }
        if(!commentRows().isEmpty()){setState(config.optBoolean("interactionMode",false)&&interactionProfileWork?"INTERACTION_SEND":"COMMENTS");return;}
        if(config.optBoolean("interactionMode",false)&&interactionProfileWork){
            AccessibilityNodeInfo like=findId("guy");
            if(like==null)db.log("[互动] 作品页未找到点赞控件 id=guy");
            else if(!config.optBoolean("likeWorks",true)||interactionLikeRequested){}
            else if(str(like.getContentDescription()).contains("已赞")||str(like.getContentDescription()).contains("已点赞")){interactionLikeRequested=true;db.log("[互动] 作品显示已点赞，跳过");}
            else if(click(like)){interactionLikeRequested=true;db.log("[互动] 已请求作品点赞，尚未确认服务端结果");settle(1500);return;}
            else db.log("[互动] 点赞控件点击失败 id=guy bounds="+rect(like));
        }else if(config.optBoolean("interactionMode",false)) db.log("[互动] 当前作品来自搜索结果，仅扫描评论，不执行点赞/评论");
        if(!interactionProfileWork&&!isResults())panelRecovery.capture(commentPage().nodes);
        if(recoverNoteComments())return;
        AccessibilityNodeInfo button=commentEntry();if(button!=null&&clickCommentEntry(button)){setState(config.optBoolean("interactionMode",false)&&interactionProfileWork?"INTERACTION_SEND":"COMMENTS");db.log("[评论入口] 点击请求已接受；等待验证面板，控件="+str(button.getViewIdResourceName())+"，描述="+str(button.getContentDescription())+"，位置="+rect(button));}
        else if(config.optBoolean("interactionMode",false)&&interactionProfileWork)db.log("[互动] 作品页未找到评论控件");
        else if(findText("直播")!=null||findText("广告")!=null){db.log("[安全返回] 当前为直播/广告页面，返回结果页");workerBack();setState("BACK_RESULTS");settle(2200);}
    }
    AccessibilityNodeInfo commentEntry(){
        AccessibilityNodeInfo best=null;int rank=-1;
        for(AccessibilityNodeInfo n:nodes){Rect r=rect(n);int score=CommentEntry.score(str(n.getViewIdResourceName()),str(n.getText()),str(n.getContentDescription()),n.isVisibleToUser(),r.left,r.top,r.right,r.bottom,getResources().getDisplayMetrics().widthPixels,screenHeight());if(score>rank){rank=score;best=n;}}
        return best;
    }
    boolean clickCommentEntry(AccessibilityNodeInfo button){
        Rect r=rect(button);if(!r.intersect(0,0,getResources().getDisplayMetrics().widthPixels,screenHeight()))return false;
        if(button.isClickable()&&button.performAction(AccessibilityNodeInfo.ACTION_CLICK)){settle(2200);return true;}
        return tap(r.centerX(),r.centerY());
    }
    void resetCommentScan(){panelRecovery.reset();noteRecovery.reset();commentLayoutAttempts=0;commentLayoutPending=false;commentEmptyAt=0;commentEmptyReason="";lastCommentRegion=null;commentUnresolvedAt=0;commentStalls=0;commentFrames=0;commentExpandAttempts=0;commentBeforeSwipe="";commentSwipePending=false;commentSwipeCompleted=false;commentBottomObserved=false;}
    boolean recoverNoteComments(){
        CommentPage page=commentPage();
        if(interactionProfileWork||isResults()||!NoteCommentRecovery.sameNote(page.nodes,panelRecovery.source))return false;
        if(NoteCommentRecovery.composer(page)){
            if(state.equals("VIDEO"))setState("COMMENTS");
            if(noteRecovery.dismissals>=2){
                snapshotAll("NOTE_COMPOSER_SKIPPED");
                db.log("[图文跳过] 评论编辑层连续2次无法收起；当前作品按无可读取评论完成；没有发送任何内容；继续下一个作品");
                completeVideo();return true;
            }
            snapshotAll("NOTE_COMPOSER_RECOVERY");noteRecovery.dismissals++;
            boolean dismissed=dismissNoteComposer(page);
            db.log("[图文恢复] 已核对原作者与被编辑层遮挡的正文；当前为评论输入框而非评论列表；收起键盘/编辑层="+noteRecovery.dismissals+"/2；方式="+(dismissed?"点击编辑层返回遮罩":"系统返回键")+"；已扫="+videoComments+"；不输入不发送");
            if(!dismissed)workerBack();commentUnresolvedAt=0;settle(2200);return true;
        }
        int width=getResources().getDisplayMetrics().widthPixels,height=screenHeight();
        if(!NoteCommentRecovery.hiddenInline(page,width,height))return false;
        if(state.equals("VIDEO"))setState("COMMENTS");
        if(noteRecovery.reveals>=3){commentProblem("NOTE_INLINE_REVEAL_FAILED","原图文评论位于屏幕外，已尝试展开3次；未判为空或到底");return true;}
        int[] drag=NoteCommentRecovery.revealPath(page,width,height);
        if(drag==null){commentProblem("NOTE_INLINE_REGION_MISSING","已核对原图文，但没有可安全滑动的正文或评论标题区域");return true;}
        if(noteRecovery.reveals==0)snapshotAll("NOTE_INLINE_BEFORE");
        noteRecovery.reveals++;boolean accepted=swipeNoteInline(drag);
        db.log("[图文展开] 评论位于正文下方；从已核对正文/标题上滑；尝试="+noteRecovery.reveals+"/3；起点="+drag[0]+","+drag[1]+"；终点="+drag[0]+","+drag[2]+"；提交="+accepted+"；保留已扫="+videoComments+"；等待评论列表或明确空提示");
        commentUnresolvedAt=0;commentSwipePending=false;settle(2500);return true;
    }
    boolean swipeNoteInline(int[] drag){
        Path path=new Path();path.moveTo(drag[0],drag[1]);path.lineTo(drag[0],drag[2]);
        final int g=generation;
        return dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,650)).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){if(g==generation)db.log("[图文展开回调] 手势完成，下一帧校验评论区域");}
            @Override public void onCancelled(GestureDescription gesture){if(g==generation)db.log("[图文展开回调] 手势被取消，未判定展开成功");}
        });
    }
    boolean ensureCommentViewport(){
        CommentPage page=commentPage();int width=getResources().getDisplayMetrics().widthPixels,height=screenHeight();
        if(!page.needsExpansion(width,height)){
            if(commentLayoutPending){commentLayoutPending=false;commentStalls=0;commentSwipePending=false;commentUnresolvedAt=0;
                db.log("[评论展开] 已确认列表可用；列表="+(page.list<0?"无":page.nodes.get(page.list).id)+"；区域="+Arrays.toString(page.viewport(width,height))+"；保留已扫="+videoComments);snapshotAll("COMMENT_LAYOUT_EXPANDED");}
        return true;
    }
        lastCommentRegion=null;commentSwipePending=false;
        if(commentLayoutAttempts>=3){snapshotAll("COMMENT_LAYOUT_FAILED");finish("[评论异常] COMMENT_LAYOUT_FAILED：评论列表仍被底栏遮挡，展开3次未成功；未判定到底，保留已扫="+videoComments);return false;}
        if(commentLayoutAttempts==0)snapshotAll("COMMENT_LAYOUT_COLLAPSED");
        commentLayoutAttempts++;commentLayoutPending=true;
        AccessibilityNodeInfo footer=null;
        for(AccessibilityNodeInfo n:nodes){Rect r=rect(n);String desc=str(n.getContentDescription());
            if(n.isVisibleToUser()&&r.top>height*.6f&&CommentEntry.score(str(n.getViewIdResourceName()),str(n.getText()),desc,true,r.left,r.top,r.right,r.bottom,width,height)>=80){footer=n;break;}}
        boolean accepted=false;String action="底栏评论按钮";
        // On video pages the fixed bottom action is also the comment composer
        // entry.  When rows are already visible, clicking it opens the text
        // editor (and the IME) instead of expanding the list.  Use it only
        // when the list has not exposed any readable row yet; otherwise drag
        // from the verified comment heading below.
        boolean footerCanExpand=CommentEntry.canUseFooterToExpand(footer!=null,page.list>=0,!page.rows.isEmpty());
        if(footerCanExpand)accepted=clickCommentEntry(footer);
        else {
            action=footer!=null?"评论标题向上展开（避免唤起评论输入框）":"评论标题向上展开";
            // Drag from the verified comment heading, never from the note image or composer.
            CommentPage.Node h=page.nodes.get(page.header);
            if(h.top>height*.45f&&h.bottom<height){Path path=new Path();float x=Math.min(width*.8f,Math.max(1,(h.left+h.right)/2f));path.moveTo(x,(h.top+h.bottom)/2f);path.lineTo(x,height*.22f);accepted=dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,650)).build(),null);}
        }
        db.log("[评论展开] 可见区域过小；列表="+(page.list<0?"尚未露出":page.nodes.get(page.list).id)+"；原始="+(page.list<0?"无":rect(nodes.get(page.list)))+"；有效="+Arrays.toString(page.viewport(width,height))+"；动作="+action+"；控件="+(footer==null?"评论标题":str(footer.getViewIdResourceName()))+"；提交="+accepted+"；尝试="+commentLayoutAttempts+"/3；未扫描遮挡评论，未计为滑动停滞");
        settle(2500);return false;
    }
    /** Close the note comment editor itself before falling back to the system back action. */
    boolean dismissNoteComposer(CommentPage page){
        for(int i=0;i<page.nodes.size()&&i<nodes.size();i++){
            CommentPage.Node n=page.nodes.get(i);
            if(!n.visible||!n.clickable||!NoteCommentRecovery.backdrop(n))continue;
            if(click(nodes.get(i))){db.log("[图文恢复] 已点击抖音评论编辑层返回遮罩；等待列表或作品页恢复");return true;}
        }
        return false;
    }
    boolean ensureSourceComments(){
        if(recoverNoteComments())return false;
        CommentPage page=commentPage();
        if(page.header>=0){
            if(panelRecovery.restored()){
                commentUnresolvedAt=0;commentSwipePending=false;commentStalls=0;
                db.log("[评论恢复] 面板已重新打开；阶段="+state+"；本作品已扫="+videoComments+"；去重记录="+seenComments.size()+"；继续原作品，不重置计数");
            }
            return true;
        }
        long now=SystemClock.elapsedRealtime();
        if(commentUnresolvedAt==0){commentUnresolvedAt=now;snapshotAll("COMMENT_PANEL_LOST");}
        boolean same=!isResults()&&!interactionProfileWork&&panelRecovery.matches(page.nodes);
        if(same&&panelRecovery.due(now)){
            AccessibilityNodeInfo entry=commentEntry();panelRecovery.requested(now);
            boolean clicked=entry!=null&&clickCommentEntry(entry);
            db.log("[评论恢复] 已核对原作品作者和正文；重新打开尝试="+panelRecovery.attempts+"/3；点击="+clicked+"；阶段="+state+"；保留已扫="+videoComments+"；保留待判断记录="+!pendingKey.isEmpty());
            settle(2500);return false;
        }
        commentProblem("COMMENT_PANEL_NOT_CONFIRMED",same?"仍在原作品，等待评论面板重开；已尝试="+panelRecovery.attempts+"/3":"未确认仍是原作品，未点击其他作品或重新搜索");
        return false;
    }
    CommentPage commentPage(){
        if(commentPageCache!=null)return commentPageCache;
        Map<AccessibilityNodeInfo,Integer> indices=new HashMap<>();for(int i=0;i<nodes.size();i++)indices.put(nodes.get(i),i);
        List<CommentPage.Node> data=new ArrayList<>();
        for(AccessibilityNodeInfo n:nodes){
            int parent=-1;AccessibilityNodeInfo p=n.getParent();if(p!=null){Integer index=indices.get(p);if(index!=null)parent=index;p.recycle();}
            Rect r=rect(n);data.add(new CommentPage.Node(str(n.getViewIdResourceName()),str(n.getText()),str(n.getContentDescription()),str(n.getClassName()),parent,r.left,r.top,r.right,r.bottom,n.isVisibleToUser(),n.isScrollable(),n.isClickable()));
        }
        return commentPageCache=new CommentPage(data);
    }
    List<AccessibilityNodeInfo> commentRows(){List<AccessibilityNodeInfo> rows=new ArrayList<>();for(CommentPage.Row row:commentPage().rows)if(row.valid())rows.add(nodes.get(row.source));return rows;}
    String commentKey(CommentPage.Row row){return Records.key(video+"\n"+row.identity());}
    void commentProblem(String code,String detail){
        long now=SystemClock.elapsedRealtime();if(commentUnresolvedAt==0){commentUnresolvedAt=now;snapshotAll(code);}
        db.log("[评论诊断] code="+code+"；"+detail+"；持续="+((now-commentUnresolvedAt)/1000)+"秒；此情况不代表到底");
        if(now-commentUnresolvedAt>16000){snapshotAll(code+"_TIMEOUT");finish("[评论异常] "+code+"："+detail+"；未将本视频标为完成，请导出诊断包");}else settle(1800);
    }
    void comments(){
        if(interactionProfileWork){db.log("[互动] 阻止扫描客户作品的评论区，改为退出客户作品");beginReturnComments();return;}
        boolean interaction=config.optBoolean("interactionMode",false);
        if(!interaction&&videoComments>=config.optInt("perVideo",20)){db.log("[评论] 已达到本视频读取上限 "+videoComments+"，结束本视频");completeVideo();return;}
        CommentPage page=commentPage();String fingerprint=page.fingerprint();int duplicates=0;
        if(!page.end.isEmpty()){commentBottomObserved=true;db.log("[评论] 已记录本作品底部标记="+page.end+"；AI 返回后允许直接完成当前作品");}
        for(CommentPage.Row row:page.rows)if(row.valid()&&seenComments.contains(commentKey(row)))duplicates++;
        db.log("[评论扫描] 帧="+(++commentFrames)+"；窗口="+roots.size()+"；节点="+nodes.size()+"；标题="+(page.header<0?"未识别":page.nodes.get(page.header).text)+"；列表="+(page.list<0?"未识别":page.nodes.get(page.list).id)+"；候选="+page.candidates+"；有效="+(page.candidates-page.invalid)+"；缺昵称/正文="+page.invalid+"；已读="+duplicates+"；底部标记="+(page.end.isEmpty()?"无":page.end));
        if(!ensureSourceComments()){commentEmptyAt=0;commentEmptyReason="";return;}
        if(!ensureCommentViewport()){commentEmptyAt=0;commentEmptyReason="";return;}
        String empty=page.emptyReason(getResources().getDisplayMetrics().widthPixels,screenHeight());
        if(empty.equals(CommentPage.EMPTY_NOTE_PLACEHOLDER)&&(!panelRecovery.matches(page.nodes)||videoComments>0))empty="";
        if(!empty.isEmpty()){
            long now=SystemClock.elapsedRealtime();
            if(commentEmptyAt==0||!empty.equals(commentEmptyReason)){commentEmptyAt=now;commentEmptyReason=empty;db.log("[空评论确认] 依据="+empty+"；等待下一帧复核，不按缺少列表猜测为空");settle(1800);return;}
            if(now-commentEmptyAt<(empty.equals(CommentPage.EMPTY_NOTE_PLACEHOLDER)?3500:1500)){settle(1000);return;}
            snapshotAll("COMMENTS_EMPTY_CONFIRMED");db.log("[评论] 连续确认无评论；依据="+empty+"；本作品完成，继续下一个作品；已扫描记录保留");
            if(config.optBoolean("scanOnly",false)&&development(this))finish("评论扫描测试通过；已确认无评论");else completeVideo();return;
        }
        commentEmptyAt=0;commentEmptyReason="";
        if(CommentPage.emptyText(page.end)){commentProblem("COMMENT_EMPTY_NOT_READY","空评论提示与加载状态不一致，等待面板稳定后核验");return;}
        if(page.list<0&&page.end.isEmpty()){commentProblem("COMMENT_LIST_NOT_FOUND","标题已识别，但未找到其下方评论列表");return;}
        if(page.rows.isEmpty()&&page.end.isEmpty()&&page.expandFolded<0){commentProblem("COMMENT_ROWS_UNRECOGNIZED","列表已找到，但没有可识别的评论行，可能正在加载或控件结构变化");return;}
        if(page.partialCount()>0)db.log("[评论补全] 半露出行="+page.partialCount()+"；可读完整行="+(page.candidates-page.invalid)+"；列表范围="+rect(nodes.get(page.list))+"；保留已扫="+videoComments+"；后续滑动="+(page.needsShortSwipe()?"20%补全底部评论":"75%继续扫描")+"；不将半行入库或判断为到底");
        if(page.list>=0){Rect r=rect(nodes.get(page.list));if(r.intersect(0,0,getResources().getDisplayMetrics().widthPixels,screenHeight())){if(page.editor>=0&&rect(nodes.get(page.editor)).top>r.top)r.bottom=Math.min(r.bottom,rect(nodes.get(page.editor)).top);lastCommentRegion=new Rect(r);}}
        if(page.expandFolded<0&&commentExpandAttempts>0){db.log("[评论展开] 折叠入口已消失，继续读取后续评论");commentExpandAttempts=0;}
        if(commentFrames==1)snapshotAll("COMMENTS_PARSED");
        if(commentSwipePending){
            boolean changed=!fingerprint.equals(commentBeforeSwipe);commentStalls=changed?0:commentStalls+1;
            db.log("[滑动校验] 回调="+(commentSwipeCompleted?"完成":"未确认完成")+"；页面变化="+changed+"；连续未移动="+commentStalls);
            commentSwipePending=false;
            if(commentStalls>=6&&page.end.isEmpty()&&page.expandFolded<0){snapshotAll("COMMENT_SCROLL_STALLED");finish("[评论异常] COMMENT_SCROLL_STALLED：连续6次滑动后评论位置未变且没有底部标记；未判定到底，请导出诊断包");return;}
        }
        commentUnresolvedAt=0;
        for(CommentPage.Row row:page.rows){
            String name=row.name,body=row.body,posted=row.posted,place=row.place;
            if(!row.valid()){db.log("[评论跳过] 缺少可读昵称或正文，可能是图片评论/半露出行；控件="+page.nodes.get(row.source).id);continue;}
            String key=commentKey(row);if(!seenComments.add(key))continue;
            if(config.optBoolean("scanOnly",false)&&development(this)){videoComments++;db.log("[测试读取] 第"+videoComments+"条；定位="+row.selector+"；正文长度="+body.length()+"；时间="+posted+"；地区="+place+"；不写入客户库");if(config.optBoolean("regressionAi")&&videoComments<=3)regressionCommentAi(body);continue;}
            boolean added=db.add(key,taskId,video,name,body,place,posted);videoComments++;taskScanned++;if(added)taskNew++;else taskExisting++;idle=0;status(interaction?"互动助手扫描评论":"读取评论");db.log("[评论] 扫描第 "+videoComments+" 条，昵称="+name+"，结果="+(added?"新记录":"重复记录")+(interaction?"；互动模式不按评论数停止":""));
            db.log("[评论解析] key="+key.substring(0,12)+"；定位="+row.selector+"；时间="+posted+"；地区="+place+"；正文长度="+body.length()+"；头像="+(row.avatar>=0));
            if(config.optBoolean("profiles",true)){
                pendingKey=key;
                JSONObject saved=db.comment(key);
                boolean needsAi=added||IntentPolicy.pending(saved.optString("ai"))||saved.optString("ai").equals("判断失败");
                boolean needsProfile=IntentPolicy.fetchProfile(true,saved.optString("ai"),saved.optString("account"))||(interaction&&"意向".equals(saved.optString("ai")));
                if(!needsAi&&!needsProfile){db.log("[评论复用] 已有判断="+saved.optString("ai")+"；"+(saved.optString("account").isEmpty()?"无需取号":"抖音号已保存")+"；不请求AI，不进入等待状态");if(!interaction&&videoComments>=config.optInt("perVideo",20)){db.log("[评论] 已达到本视频读取上限 "+videoComments);completeVideo();return;}continue;}
                if(added||IntentPolicy.pending(saved.optString("ai"))||saved.optString("ai").equals("判断失败"))queueAi(key,body);
                setState("AI_WAIT");status("等待 AI 判断，确认意向后才获取抖音号");return;
            }
            if(added)queueAi(key,body);
            if(!interaction&&videoComments>=config.optInt("perVideo",20)){db.log("[评论] 已达到本视频读取上限，准备结束");completeVideo();return;}
        }
        if(page.expandFolded>=0){
            if(commentExpandAttempts>=3){snapshotAll("COMMENT_EXPAND_FAILED");finish("[评论异常] COMMENT_EXPAND_FAILED：折叠评论入口点击3次仍未展开；未判定到底，请导出诊断包");return;}
            boolean clicked=click(nodes.get(page.expandFolded));commentExpandAttempts++;commentStalls=0;
            db.log("[评论展开] 发现已折叠部分评论；点击展开="+clicked+"；尝试="+commentExpandAttempts+"/3；等待加载后继续扫描，不作到底处理");settle(2500);return;
        }
        if(!page.end.isEmpty()){db.log("[评论] 面板内明确底部标记="+page.end+"，结束本视频");if(config.optBoolean("scanOnly",false)&&development(this))finish("评论扫描测试通过；已确认底部，实际读取="+videoComments);else completeVideo();return;}
        if(config.optBoolean("scanOnly",false)&&development(this)&&videoComments>=config.optInt("perVideo",20)){finish("评论扫描测试通过；实际读取="+videoComments+"，已完成滑动="+interactionScrolls);return;}
        if(interaction)interactionScrolls++;
        scrollComments(commentRows());
        if(interaction)db.log("[互动] 未确认到底，继续向下滑动评论区；第 "+interactionScrolls+" 屏，当前已扫描 "+videoComments+" 条");
        stateAt=SystemClock.elapsedRealtime();
    }
    void scrollComments(List<AccessibilityNodeInfo> rows){
        // Follow a comment row's ancestors, never an unrelated scrollable video/header.
        CommentPage page=commentPage();Rect panel=page.list<0?null:rect(nodes.get(page.list));
        for(AccessibilityNodeInfo row:rows){
            if(panel!=null)break;
            AccessibilityNodeInfo parent=row.getParent();
            for(int depth=0;parent!=null&&depth<12;depth++){
                Rect bounds=rect(parent);boolean match=parent.isScrollable()&&parent.isVisibleToUser()&&bounds.height()>screenHeight()/5;
                AccessibilityNodeInfo next=match?null:parent.getParent();parent.recycle();parent=next;
                if(match){panel=bounds;break;}
            }
            if(parent!=null)parent.recycle();
            if(panel!=null)break;
        }
        if(panel==null){
            // Only use bounds of recognised comments as fallback; no whole-screen swipe.
            for(AccessibilityNodeInfo row:rows){Rect bounds=rect(row);if(panel==null)panel=new Rect(bounds);else panel.union(bounds);}
        }
        if(panel==null||!panel.intersect(0,0,getResources().getDisplayMetrics().widthPixels,screenHeight())){commentProblem("COMMENT_SCROLL_REGION_MISSING","无法定位评论区内的滑动范围");return;}
        if(!ensureCommentViewport())return;
        if(page.editor>=0)panel.bottom=Math.min(panel.bottom,rect(nodes.get(page.editor)).top);
        int[] swipe=CommentSwipe.path(panel.left,panel.top,panel.right,panel.bottom,page.needsShortSwipe());
        if(swipe==null){commentProblem("COMMENT_SCROLL_REGION_SMALL","可见评论区域过小："+panel);return;}
        Path path=new Path();path.moveTo(swipe[0],swipe[1]);path.lineTo(swipe[0],swipe[2]);
        final int g=generation;commentBeforeSwipe=page.fingerprint();commentSwipeCompleted=false;
        boolean accepted=dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,720)).build(),new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){if(g==generation){commentSwipeCompleted=true;db.log("[滑动回调] 评论区手势完成，下一帧核对位置变化");}}
            @Override public void onCancelled(GestureDescription gesture){if(g==generation)db.log("[滑动回调] 评论区手势被系统取消");}
        });
        commentSwipePending=true;
        db.log("[评论滑动] 提交="+accepted+"；区域="+panel+"；起点="+swipe[0]+","+swipe[1]+"；终点="+swipe[0]+","+swipe[2]+"；时长=720ms；幅度="+Math.round(100f*(swipe[1]-swipe[2])/panel.height())+"%");settle(config.optBoolean("interactionMode",false)?1400:2200);
    }
    void profile(){
        JSONObject saved=db.comment(pendingKey);
        boolean allowed=IntentPolicy.fetchProfile(config.optBoolean("profiles",true),saved.optString("ai"),saved.optString("account"))||(config.optBoolean("interactionMode",false)&&"意向".equals(saved.optString("ai")));
        if(!allowed){db.log("[profile] 未获意向判断许可，取消取号");beginReturnComments();return;}
        if(config.optBoolean("interactionMode",false)){
            if(!isCustomerProfile()){
                if(SystemClock.elapsedRealtime()-stateAt<8000){settle(1000);return;}
                db.log("[互动] 主页未确认，不尝试作品互动");beginReturnComments();return;
            }
            if(interactionAccount.isEmpty()){
                interactionAccount=visibleProfileAccount();
                if(!interactionAccount.isEmpty()&&!interactionCustomers.add(interactionAccount)){
                    db.log("[互动] 本任务已处理此抖音号，跳过重复客户主页");beginReturnComments();return;
                }
            }
            if(!interactionFollowDone){
                // 互动助手暂不执行关注，避免误触主页统计栏中的“34 关注”等控件。
                db.log("[互动] 已跳过关注动作，仅执行作品点赞和评论");
                interactionFollowDone=true; settle(900); return;
            }
            // “作品 70”是排序入口，点击会弹出“最新/最热”菜单；这里只读取主页作品网格，不点击该标签。
            interactionProfileWork=true;
            db.log("[互动] 已进入意向客户主页，不再扫描主页评论；跳过作品排序入口，直接查找作品网格");
            setState("INTERACTION_WORK");settle(1200);return;
        }
        // A successful click dispatch is not proof that a profile actually opened.
        // If still on comments, backing out would close the panel and lose the return path.
        if(!commentRows().isEmpty()){db.log("[profile] 仍在评论区，主页未打开；继续采集");setState("COMMENTS");return;}
        for(AccessibilityNodeInfo n:nodes){String text=str(n.getText());if(text.startsWith("抖音号")){
            String account=text.replaceFirst("^抖音号[：:\\s]*","").trim();
            if(!account.isEmpty()){db.account(pendingKey,account);db.log("已补取公开抖音号");beginReturnComments();return;}
        }}
        if(SystemClock.elapsedRealtime()-stateAt>12000){snapshotAll("PROFILE_NO_ID");db.log("未能读取公开抖音号，保留为空");beginReturnComments();}
    }
    AccessibilityNodeInfo findFollowControlLegacy(){
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser()||!"关注".equals(str(n.getText())))continue;
            AccessibilityNodeInfo p=n.getParent();
            for(int i=0;p!=null&&i<4;i++){
                Rect r=rect(p);boolean ok=p.isClickable()&&r.width()>getResources().getDisplayMetrics().widthPixels/2;
                AccessibilityNodeInfo next=ok?null:p.getParent();p.recycle();p=next;
                if(ok)return n;
            }
            if(p!=null)p.recycle();
        }
        return null;
    }
    AccessibilityNodeInfo findFollowControl(){
        for(AccessibilityNodeInfo n:nodes){
            if(!n.isVisibleToUser()||!"关注".equals(str(n.getText()))) continue;
            AccessibilityNodeInfo p=n.getParent();
            for(int i=0;p!=null&&i<4;i++){
                Rect r=rect(p);
                if(p.isClickable()&&!r.isEmpty()&&r.height()>=35&&r.height()<=260){
                    AccessibilityNodeInfo result=AccessibilityNodeInfo.obtain(p); p.recycle(); return result;
                }
                AccessibilityNodeInfo next=p.getParent(); p.recycle(); p=next;
            }
            if(p!=null)p.recycle();
        }
        return null;
    }
    void interactionWork(){
        if(interactionWorksDone>=1||interactionNoMoreWorks){beginReturnComments();return;}
        if(findText("私密账号")!=null||findText("暂无作品")!=null){
            interactionNoMoreWorks=true;db.log("[互动] 作品不公开或暂无作品，返回原始扫描区");beginReturnComments();return;
        }
        AccessibilityNodeInfo work=null;
        for(AccessibilityNodeInfo n:nodes)if(isWorkTile(n)){work=n;break;}
        if(work==null){
            if(SystemClock.elapsedRealtime()-stateAt<12000){db.log("[互动] 作品网格尚未进入可视区，向下滚动主页后重试");scroll(false,.5f,.82f,.42f);return;}
            interactionNoMoreWorks=true;db.log("[互动] 未找到可打开作品，结束本客户查找，不再为凑满数量重复搜索作品");beginReturnComments();return;
        }
        interactionWorksDone=1; // Reserve before dispatch; a failed/uncertain click must not open another work.
        if(click(work)){interactionLikeRequested=false;interactionSendRequested=false;interactionDraft="";db.log("[互动] 本客户仅处理此一个作品，已请求打开；不代表评论发送成功");setState("VIDEO");settle(2200);}
        else {db.log("[互动] 作品点击未接受，本客户不再尝试其他作品");beginReturnComments();}
    }
    void interactionSend(){
        if(!interactionProfileWork){finish("[互动] 缺少客户作品上下文，禁止发送");return;}
        if(!config.optBoolean("commentWorks",true)){beginReturnComments();return;}
        if(config.optBoolean("aiComment",false)&&interactionDraft.isEmpty())db.log("[互动] 当前版本尚未接入 AI 作品文案生成，使用已配置评论候选");
        AccessibilityNodeInfo input=findId("eyi");
        if(input==null){if(SystemClock.elapsedRealtime()-stateAt<8000){settle(1000);return;}db.log("[互动] 评论输入框加载超时，退出客户作品");beginReturnComments();return;}
        if(!input.isFocused()){click(input);settle(1500);return;}
        if(interactionDraft.isEmpty()){
            List<String> candidates=new ArrayList<>();for(String line:config.optString("commentCandidates","").split("\\r?\\n"))if(!line.trim().isEmpty())candidates.add(line.trim());
            if(candidates.isEmpty()){db.log("[互动] 评论候选为空，未使用默认文案");beginReturnComments();return;}
            interactionDraft=candidates.get(new Random().nextInt(candidates.size()));
        }
        Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,interactionDraft);
        if(!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b)){db.log("[互动] 评论输入失败，退出客户作品");beginReturnComments();return;}
        db.log("[互动] 已请求填入评论，等待新控件树核对文字和发送按钮");setState("INTERACTION_SEND_READY");settle(1500);
    }
    void interactionSendReady(){
        AccessibilityNodeInfo input=findId("eyi");
        if(input!=null&&interactionDraft.equals(str(input.getText()))){
            AccessibilityNodeInfo send=findId("e2x");if(send==null)send=findText("发送");
            if(send!=null&&send.isEnabled()){
                if(interactionSendRequested){beginReturnComments();return;}
                interactionSendRequested=true;
                // Enter verification before dispatch: never retry a possibly accepted send.
                setState("INTERACTION_SEND_VERIFY");boolean requested=click(send);
                db.log("[互动] 评论发送请求="+requested+"，控件="+str(send.getViewIdResourceName())+"，位置="+rect(send)+"；等待结果，不重复发送");settle(2000);return;
            }
        }
        if(SystemClock.elapsedRealtime()-stateAt>8000){snapshotAll("COMMENT_SEND_NOT_READY");db.log("[互动] 输入文字或发送按钮未确认，未发送；退出客户作品，不扫描其评论");beginReturnComments();}
    }
    void interactionSendVerify(){
        for(AccessibilityNodeInfo row:commentRows())if(interactionDraft.equals(childText(row,"content"))){db.log("[互动] 评论区出现同文内容；可能已发送，未核验作者，不自动重发");beginReturnComments();return;}
        if(SystemClock.elapsedRealtime()-stateAt>8000){snapshotAll("COMMENT_SEND_UNCONFIRMED");db.log("[互动] 发送结果待核实，不重发，退出客户作品");beginReturnComments();}
    }
    void beginReturnComments(){
        returnAttempts=0;
        if(interactionProfileWork){setState("INTERACTION_RETURN");db.log("[互动] 开始退出客户作品：先回客户主页，再回原始评论区");return;}
        workerBack();setState("RETURN_COMMENTS");settle(2200);
    }
    void returnInteraction(){
        boolean profile=isCustomerProfile();
        if(profile){
            interactionProfileWork=false;interactionDraft="";returnAttempts=0;
            db.log("[互动] 已确认客户主页（支持顶部不可见），返回原始扫描区；作品尝试="+interactionWorksDone+"/1，发送请求="+interactionSendRequested+"，不再打开其他作品");
            interactionBack();setState("RETURN_COMMENTS");settle(2200);return;
        }
        if(isResults()||returnAttempts>=6){snapshotAll("INTERACTION_RETURN_UNCONFIRMED");finish("[互动] 未确认返回客户主页，停止；不重新搜索、不扫描客户作品评论");return;}
        returnAttempts++;db.log("[互动] 退出客户作品/键盘/评论面板，第 "+returnAttempts+" 步；不打开评论区");interactionBack();settle(1800);
    }
    void interactionBack(){workerBack();}
    void returnComments(){
        if(config.optBoolean("interactionMode",false)&&isCustomerProfile()){
            if(returnAttempts++<2){workerBack();settle(1800);return;}
            snapshotAll("SOURCE_RETURN_FAILED");finish("[互动] 无法退出客户主页，保留现场");return;
        }
        if(!commentRows().isEmpty()){setState("COMMENTS");return;}
        // Some profile routes return to the video with its comment sheet closed.
        AccessibilityNodeInfo comment=commentEntry();
        if(comment!=null&&!isResults()&&returnAttempts<2){if(clickCommentEntry(comment)){returnAttempts++;db.log("[profile] 返回视频/图文详情，重新打开评论区");}return;}
        boolean stillProfile=false;for(AccessibilityNodeInfo n:nodes)if(str(n.getText()).startsWith("抖音号"))stillProfile=true;
        if(stillProfile&&returnAttempts<2){returnAttempts++;workerBack();settle(2200);db.log("[profile] 仍在主页，等待再次返回");return;}
        if(isResults()){snapshotAll("RETURN_TO_RESULTS");db.log("[profile] 已退到结果页；保留本视频已采评论，继续下一个视频");setState("OPEN");return;}
        if(SystemClock.elapsedRealtime()-stateAt>16000){snapshotAll("RETURN_COMMENTS_FAILED");finish("未能恢复评论区，已保存 XML 并停止，避免在未知页面继续操作");}
    }
    void completeVideo(){done++;db.log("[采集] 视频完成，读取评论 "+videoComments);if(config.optBoolean("quantity",true)&&done>=config.optInt("limit",1)){setState("DRAIN");return;}workerBack();setState("BACK_RESULTS");settle(2200);}
    void startCommentRegression(boolean toBottom){startCommentRegression(toBottom,false);}
    void startCommentRegression(boolean toBottom,boolean withAi){
        if(!development(this)||running)return;
        try{
            config=new JSONObject().put("scanOnly",true).put("regressionAi",withAi).put("interactionMode",true).put("quantity",true).put("limit",1).put("perVideo",toBottom?Integer.MAX_VALUE:12).put("profiles",false);
            messages=null;interactionProfileWork=false;generation++;running=true;began=SystemClock.elapsedRealtime();lease=Long.MAX_VALUE;nextAuth=Long.MAX_VALUE;
            taskId=String.valueOf(System.currentTimeMillis());video="评论扫描专项回归";videoComments=0;taskScanned=0;taskNew=0;taskExisting=0;done=0;pendingAi=0;seenComments.clear();interactionScrolls=0;resetCommentScan();missingSince=0;notBefore=0;visionBusy=false;
            prefs.edit().putBoolean("running",true).putString("lastTask",taskId).apply();
            db.log("[worker] 任务开始：评论扫描专项回归，自动打开当前作品评论；"+(withAi?"前三条调用AI":"不调用AI")+"；不进入主页、不发送消息");db.log("[环境] task="+taskId+"；"+Diagnostics.environment(this));db.log("[配置] "+Diagnostics.taskSettings(config));
            if(withAi)db.log("[AI回归] 额外取前三条实际评论调用后台意向判断；使用已保存业务身份和目标；只记录诊断结果，不进入主页");
            windowRecovery.reset();showOverlay();setState("VIDEO");
            Intent intent=getPackageManager().getLaunchIntentForPackage(DY);if(intent!=null){intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);}
            settle(1800);main.postDelayed(loop,2000);
        }catch(Exception e){finish("评论专项测试启动失败："+e.getClass().getSimpleName());}
    }
    void regressionCommentAi(String body){
        final String who=prefs.getString("identity",""),target=prefs.getString("target",""),mode=prefs.getString("ai_mode","均衡");
        ai.execute(()->{long at=SystemClock.elapsedRealtime();db.log("[图文AI回归] 实际评论="+body+"；业务身份="+who+"；目标="+target);try{JSONObject result=AiJudge.judge(this,prefs,body,who,target,mode);db.log("[图文AI回归] 返回="+result+"；耗时="+(SystemClock.elapsedRealtime()-at)+"ms");}catch(Exception e){db.log("[图文AI回归] 失败="+aiFailure(e));}});
    }
    void queueAi(String key,String comment){
        if(!AiJudge.configured(prefs)){String reason="缺少后台AI访问凭据；请安装带后台连接配置的版本或登录后重试，未调用模型、未进入主页";db.ai(key,"判断失败",null,reason);db.log("[ai] 未发起判断："+reason);return;}
        db.ai(key,"判断中",null,"正在判断评论意向");
        final int g=generation;final String who=config.optString("identity"),target=config.optString("target"),mode=config.optString("aiMode","均衡"),id=key.substring(0,Math.min(10,key.length()));pendingAi++;
        db.log("[ai] 已排队 id="+id+"；待处理="+pendingAi+"；口径="+mode+"；业务身份="+(who.trim().isEmpty()?"未填写":"已填写")+"；目标客户="+(target.trim().isEmpty()?"未填写":"已填写"));
        ai.execute(()->{
            if(g!=generation)return;
            long started=SystemClock.elapsedRealtime();String excerpt=comment.replace('\n',' ').substring(0,Math.min(100,comment.length()));
            db.log("[ai] 请求开始 id="+id+"；评论="+excerpt+"；最多重试="+AI_MAX_RETRIES);
            boolean completed=false;
            for(int attempt=1;attempt<=AI_MAX_RETRIES+1&&g==generation;attempt++){
                try{
                    db.log("[ai] 请求尝试 "+attempt+"/"+(AI_MAX_RETRIES+1)+" id="+id);
                    JSONObject r=AiJudge.judge(this,prefs,comment,who,target,mode);
                    String result=r.optString("status");if(result.trim().isEmpty())throw new IllegalStateException("AI返回缺少status");
                    double score=r.optDouble("score",0);String reason=r.optString("reason","未提供判断依据");
                    if(g==generation){db.ai(key,result,score,reason);db.log("[ai] 判断完成 id="+id+"；结果="+result+"；置信度="+score+"；依据="+reason+"；尝试="+attempt+"；耗时="+(SystemClock.elapsedRealtime()-started)+"ms");completed=true;}break;
                }catch(Exception e){
                    if(g!=generation)break;
                    String reason=aiFailure(e);
                    if(attempt<=AI_MAX_RETRIES){
                        long delay=AI_FIRST_RETRY_DELAY_MS<<(attempt-1);
                        db.log("[ai] 请求无有效返回 id="+id+"；尝试="+attempt+"；原因="+reason+"；将在"+(delay/1000)+"秒后重试；已扫描记录保留");
                        try{Thread.sleep(delay);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}
                    }else{
                        db.ai(key,"判断失败",null,reason);
                        db.log("[ai] 重试耗尽 id="+id+"；共尝试="+attempt+"；耗时="+(SystemClock.elapsedRealtime()-started)+"ms；原因="+reason+"；未按意向客户处理");
                    }
                }
            }
            if(g==generation&&!completed)db.log("[ai] 当前评论结束；扫描记录仍保留，状态="+db.comment(key).optString("ai"));
            main.post(()->{if(g==generation)pendingAi--;});
        });
    }
    void startAiRegression(){
        if(!development(this)||running)return;
        final String who=prefs.getString("identity",""),target=prefs.getString("target",""),mode=prefs.getString("ai_mode","均衡");
        db.log("[AI回归] 仅测试后台意向判断，不进入主页、不点赞、不发送评论；口径="+mode+"；身份="+who+"；目标="+target);
        ai.execute(()->{for(String comment:new String[]{"我下个月想出国旅游，需要办理签证，请问怎么办理、需要什么材料？","风景真漂亮，背景音乐也好听"}){long started=SystemClock.elapsedRealtime();db.log("[AI回归] 请求开始："+comment);try{JSONObject r=AiJudge.judge(this,prefs,comment,who,target,mode);db.log("[AI回归] 返回="+r.toString()+"；耗时="+(SystemClock.elapsedRealtime()-started)+"ms");}catch(Exception e){db.log("[AI回归] 失败="+aiFailure(e));}}db.log("[AI回归] 两条样例测试结束，不产生客户记录");});
    }
    String aiFailure(Exception e){
        String reason=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());
        for(String secret:new String[]{prefs.getString("token",""),BuildConfig.AI_PROXY_TOKEN})if(!secret.isEmpty())reason=reason.replace(secret,"[隐藏]");
        return reason.substring(0,Math.min(400,reason.length()));
    }
    void awaitIntent(){
        JSONObject saved=db.comment(pendingKey);String status=saved.optString("ai");
        if(IntentPolicy.pending(status)){
            if(SystemClock.elapsedRealtime()-stateAt<180000)return;
            db.ai(pendingKey,"判断失败",null,"后台重试线程未返回，超过180秒等待上限");
            db.log("[ai] 判断等待超过180秒，标记失败并保留已扫描记录；继续下一条评论");setState("COMMENTS");return;
        }
        // The final AI callback can arrive while Douyin briefly removes the
        // comment sheet. An explicit bottom marker already proves this work
        // is complete, so do not wait for a panel that is being torn down.
        if(commentBottomObserved&&commentPage().header<0&&pendingAi==0){
            db.log("[评论] 已确认本作品到底；AI 返回后评论面板短暂消失，直接完成当前作品并继续下一个作品");
            completeVideo();return;
        }
        if(development(this)&&config.optBoolean("regressionClosePanelOnce")&&!regressionPanelClosed&&commentPage().header>=0&&!panelRecovery.source.isEmpty()){
            regressionPanelClosed=true;db.log("[回归注入] 主动关闭一次原作品评论面板，验证 AI 返回后可原地恢复；保留已扫="+videoComments);workerBack();settle(2500);return;
        }
        if(!ensureSourceComments())return;
        if(!ensureCommentViewport())return;
        boolean allowed=IntentPolicy.fetchProfile(config.optBoolean("profiles",true),status,saved.optString("account"))||(config.optBoolean("interactionMode",false)&&"意向".equals(status));
        if(!allowed){
            db.log("[ai] "+status+"；"+(saved.optString("account").isEmpty()?"跳过主页取号":"已有抖音号，不重复取号"));setState("COMMENTS");return;
        }
        for(CommentPage.Row parsed:commentPage().rows){
            if(!parsed.valid())continue;AccessibilityNodeInfo row=nodes.get(parsed.source);
            String key=commentKey(parsed);
            if(!pendingKey.equals(key))continue;
            AccessibilityNodeInfo avatar=parsed.avatar>=0?AccessibilityNodeInfo.obtain(nodes.get(parsed.avatar)):child(row,"avatar");
            if(avatar==null) avatar=commentAvatar(row);
            if(avatar!=null){interactionWorksDone=0;interactionNoMoreWorks=false;interactionAccount="";interactionSendRequested=false;interactionFollowDone=false;interactionProfileWork=false;boolean opened=click(avatar);avatar.recycle();if(opened){returnAttempts=0;setState("PROFILE");db.log("[ai] 已确认意向客户，进入该评论人的主页"+(config.optBoolean("interactionMode",false)?"执行互动":"获取公开抖音号"));return;}}
            db.log("[profile] 意向客户头像无法点击，保留记录继续采集");setState("COMMENTS");return;
        }
        db.log("[profile] 意向评论已不在当前页面，未按昵称猜测取号");setState("COMMENTS");
    }
    String visibleProfileAccount(){
        for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()){
            String text=str(n.getText());if(text.startsWith("抖音号"))return text.replaceFirst("^抖音号[：:\\s]*","").trim();
        }return "";
    }
    boolean isWorkTile(AccessibilityNodeInfo n){
        if(!n.isVisibleToUser()||!n.isClickable()||rect(n).isEmpty())return false;
        String id=str(n.getViewIdResourceName());if(id.endsWith("/qbt"))return true;
        if(id.endsWith("/container")){
            AccessibilityNodeInfo cover=child(n,"cover");if(cover!=null){cover.recycle();return true;}
        }return false;
    }
    boolean isCustomerProfile(){
        if(isResults()||findId("eyi")!=null||!commentRows().isEmpty())return false;
        String account=visibleProfileAccount();
        if(!account.isEmpty())return interactionAccount.isEmpty()||interactionAccount.equals(account);
        boolean works=false,tile=false;
        for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()){
            works|=str(n.getText()).matches("作品\\s*\\d+.*")||str(n.getContentDescription()).matches("作品\\s*\\d+.*");
            tile|=isWorkTile(n);
        }
        return works&&tile;
    }
    AccessibilityNodeInfo commentAvatar(AccessibilityNodeInfo row){
        for(int i=0;i<row.getChildCount();i++){
            AccessibilityNodeInfo c=row.getChild(i); if(c==null)continue;
            Rect r=rect(c); String cls=str(c.getClassName()),id=str(c.getViewIdResourceName());
            if(c.isVisibleToUser()&&!r.isEmpty()&&r.left<screenHeight()*.28f&&(cls.contains("Image")||id.contains("avatar"))){AccessibilityNodeInfo out=AccessibilityNodeInfo.obtain(c);c.recycle();return out;}
            AccessibilityNodeInfo out=commentAvatar(c);c.recycle();if(out!=null)return out;
        }
        return null;
    }
    AccessibilityNodeInfo targetRoot(){
        AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null&&DY.equals(str(root.getPackageName())))return root;if(root!=null)root.recycle();
        for(AccessibilityWindowInfo w:getWindows()){AccessibilityNodeInfo r=w.getRoot();if(r!=null&&DY.equals(str(r.getPackageName())))return r;if(r!=null)r.recycle();}return null;
    }
    static void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out){out.add(n);for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null)walk(c,out);}}
    AccessibilityNodeInfo findId(String id){for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&str(n.getViewIdResourceName()).endsWith("/"+id))return n;return null;}
    AccessibilityNodeInfo findText(String value){for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&(str(n.getText()).replace(" ","").equals(value.replace(" ",""))||str(n.getContentDescription()).equals(value)))return n;return null;}
    AccessibilityNodeInfo findDescPrefix(String prefix){for(AccessibilityNodeInfo n:nodes)if(n.isVisibleToUser()&&str(n.getContentDescription()).startsWith(prefix))return n;return null;}
    static AccessibilityNodeInfo child(AccessibilityNodeInfo n,String id){if(str(n.getViewIdResourceName()).endsWith("/"+id))return AccessibilityNodeInfo.obtain(n);for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){AccessibilityNodeInfo found=child(c,id);c.recycle();if(found!=null)return found;}}return null;}
    static String childText(AccessibilityNodeInfo n,String id){AccessibilityNodeInfo c=child(n,id);if(c==null)return "";String text=str(c.getText());c.recycle();return text;}
    boolean click(AccessibilityNodeInfo n){
        if(paused)return false;
        Rect bounds=rect(n);if(!n.isVisibleToUser()||bounds.isEmpty())return false;
        if(n.isClickable()&&n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){settle(2000);return true;}
        AccessibilityNodeInfo p=n.getParent();int depth=0;while(p!=null&&depth++<3){if(p.isClickable()&&rect(p).contains(bounds)){boolean clicked=p.performAction(AccessibilityNodeInfo.ACTION_CLICK);p.recycle();p=null;if(clicked){settle(2000);return true;}break;}AccessibilityNodeInfo next=p.getParent();p.recycle();p=next;}if(p!=null)p.recycle();
        return tap(bounds.centerX(),bounds.centerY());
    }
    void scroll(boolean up,float x,float from,float to){Path path=new Path();int w=getResources().getDisplayMetrics().widthPixels,h=screenHeight();path.moveTo(w*x,h*from);path.lineTo(w*x,h*to);dispatchTaskGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,120,650)).build(),null);settle(2200);}
    int screenHeight(){return getResources().getDisplayMetrics().heightPixels;}
    static Rect rect(AccessibilityNodeInfo n){Rect r=new Rect();n.getBoundsInScreen(r);return r;}
    static String str(CharSequence s){return s==null?"":s.toString();}
    void snapshot(String label,AccessibilityNodeInfo root){
        try{java.io.File folder=getExternalFilesDir("xml");if(folder==null)return;java.io.File file=new java.io.File(folder,taskId+"_"+(++snapshotNumber)+"_"+label+".xml");
            try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){org.xmlpull.v1.XmlSerializer x=android.util.Xml.newSerializer();x.setOutput(out,"UTF-8");x.startDocument("UTF-8",true);writeNode(x,root);x.endDocument();}db.log("[页面结构] state="+state+"；文件="+file.getName());}
        catch(Exception e){db.log("[locator] XML 保存失败："+e.getClass().getSimpleName());}
    }
    void writeNode(org.xmlpull.v1.XmlSerializer x,AccessibilityNodeInfo n)throws Exception{
        x.startTag(null,"node");x.attribute(null,"text",n.isPassword()?"":str(n.getText()));x.attribute(null,"desc",n.isPassword()?"":str(n.getContentDescription()));x.attribute(null,"id",str(n.getViewIdResourceName()));x.attribute(null,"class",str(n.getClassName()));x.attribute(null,"bounds",rect(n).toShortString());x.attribute(null,"selected",String.valueOf(n.isSelected()));x.attribute(null,"clickable",String.valueOf(n.isClickable()));x.attribute(null,"visible",String.valueOf(n.isVisibleToUser()));
        x.attribute(null,"scrollable",String.valueOf(n.isScrollable()));x.attribute(null,"enabled",String.valueOf(n.isEnabled()));x.attribute(null,"checked",String.valueOf(n.isChecked()));x.attribute(null,"package",str(n.getPackageName()));x.attribute(null,"windowId",String.valueOf(n.getWindowId()));
        x.attribute(null,"focused",String.valueOf(n.isFocused()));x.attribute(null,"editable",String.valueOf(n.isEditable()));
        if(Build.VERSION.SDK_INT>=26){x.attribute(null,"showingHint",String.valueOf(n.isShowingHintText()));x.attribute(null,"hint",n.isPassword()?"":str(n.getHintText()));}
        for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null){writeNode(x,c);c.recycle();}}x.endTag(null,"node");
    }
}
