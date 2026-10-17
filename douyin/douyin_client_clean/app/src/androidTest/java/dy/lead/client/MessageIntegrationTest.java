package dy.lead.client;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.test.InstrumentationTestCase;
import org.json.*;
import java.io.File;

/** Uses its own DB; never mutates real lead outcomes. */
public class MessageIntegrationTest extends InstrumentationTestCase {
    Context isolated;Records db;
    @Override protected void setUp() throws Exception {super.setUp();Context target=getInstrumentation().getTargetContext();isolated=new ContextWrapper(target){
        @Override public File getDatabasePath(String name){return super.getDatabasePath("message_integration_test.db");}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory);}
        @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,android.database.DatabaseErrorHandler error){return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(),factory,error);}
    };isolated.getDatabasePath("automation.db").delete();db=new Records(isolated);}
    @Override protected void tearDown() throws Exception {if(db!=null)db.close();isolated.getDatabasePath("automation.db").delete();super.tearDown();}
    public void testCrossTaskSuccessAndHistory() throws Exception {
        db.add("a","task1","video1","same","one","","today");db.account("a","person1");
        db.add("b","task2","video2","same","two","","today");db.account("b","person1");
        long attempt=db.beginMessage("run1","person1","hello",false);assertTrue(attempt>0);assertTrue(db.reserveSend(attempt,"person1",1));assertFalse(db.reserveSend(attempt,"person1",1));
        db.messageResult(attempt,"person1","成功","simulated assertion only");assertEquals("成功",db.messageState("person1"));assertEquals(-1,db.beginMessage("run2","person1","again",true));
        JSONArray rows=db.messageRows();assertEquals(2,rows.length());for(int i=0;i<2;i++){assertEquals("成功",rows.getJSONObject(i).getString("dm_state"));assertEquals("1",rows.getJSONObject(i).getString("contacted"));}
    }
    public void testEmptyCommentsAdvanceOnlyAfterConfirmation() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            java.util.List<CommentPage.Node> page=new java.util.ArrayList<>();
            page.add(new CommentPage.Node("root","","","FrameLayout",-1,0,0,1080,2200,true,false));
            page.add(new CommentPage.Node("header","评论","","TextView",0,0,100,1080,180,true,false));
            page.add(new CommentPage.Node("empty","暂无评论","","TextView",0,0,400,1080,450,true,false));
            AutomationService service=new AutomationService(){
                @Override public android.content.res.Resources getResources(){return getInstrumentation().getTargetContext().getResources();}
                @Override int screenHeight(){return 2200;}
                @Override CommentPage commentPage(){return new CommentPage(page);}
                @Override boolean ensureSourceComments(){return true;}
                @Override boolean ensureCommentViewport(){return true;}
                @Override void snapshotAll(String label){}
                @Override void completeVideo(){done++;state="BACK_RESULTS";}
                @Override void commentProblem(String code,String detail){state=code;}
            };
            try{
                service.db=db;service.config=new JSONObject().put("quantity",true).put("limit",100).put("perVideo",10);
                service.running=true;service.state="COMMENTS";service.comments();
                assertEquals(0,service.done);assertEquals("COMMENTS",service.state);
                service.commentEmptyAt-=2000;service.comments();
                assertTrue(service.running);assertEquals(1,service.done);assertEquals("BACK_RESULTS",service.state);
                page.remove(2);service.state="COMMENTS";service.comments();
                assertEquals(1,service.done);assertEquals("COMMENT_LIST_NOT_FOUND",service.state);
            }catch(Exception e){throw new RuntimeException(e);}finally{service.network.shutdownNow();service.ai.shutdownNow();}
        });
    }
    public void testHonorEmptyNoteReturnsToResultsWithoutStopping() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            java.util.List<CommentPage.Node> page=new java.util.ArrayList<>();
            page.add(new CommentPage.Node("root","","","FrameLayout",-1,0,0,1080,2200,true,false));
            page.add(new CommentPage.Node("/tv_nickname","author","","TextView",0,0,80,500,150,true,false));
            page.add(new CommentPage.Node("/tv_desc","caption","","TextView",0,0,200,1080,400,true,false));
            page.add(new CommentPage.Node("header","评论","","TextView",0,0,500,200,580,true,false));
            page.add(new CommentPage.Node("prompt","发条评论，说说你的感受","","EditText",0,0,620,1080,720,true,false));
            page.add(new CommentPage.Node("/comment_container","","","FrameLayout",0,0,750,1080,2000,true,false));
            page.add(new CommentPage.Node("list","","","RecyclerView",5,0,750,1080,2000,true,false));
            page.add(new CommentPage.Node("placeholder","平等表达，友善交流","","TextView",6,100,1600,900,1700,true,false));
            page.add(new CommentPage.Node("/fn3","说点什么...","","EditText",0,0,2000,400,2180,true,false));
            page.add(new CommentPage.Node("button","","评论评论，按钮","LinearLayout",0,600,2000,800,2180,true,false,true));
            final int[] backs={0};
            AutomationService service=new AutomationService(){
                @Override public android.content.res.Resources getResources(){return getInstrumentation().getTargetContext().getResources();}
                @Override int screenHeight(){return 2200;}
                @Override CommentPage commentPage(){return new CommentPage(page);}
                @Override boolean isResults(){return false;}
                @Override void snapshotAll(String label){}
                @Override void workerBack(){backs[0]++;}
                @Override void setState(String next){state=next;}
                @Override void settle(long millis){}
                @Override void commentProblem(String code,String detail){state=code;}
            };
            try{
                service.db=db;service.config=new JSONObject().put("quantity",true).put("limit",30).put("perVideo",100);
                service.running=true;service.state="COMMENTS";service.panelRecovery.capture(page);
                db.add("kept","previous","video","name","body","","");service.seenVideos.add("current");
                service.comments();assertEquals(0,service.done);assertEquals(0,backs[0]);
                service.commentEmptyAt-=2000;service.comments();assertEquals(0,service.done);
                service.commentEmptyAt-=2000;service.comments();
                assertTrue(service.running);assertEquals(1,service.done);assertEquals(1,backs[0]);assertEquals("BACK_RESULTS",service.state);
                assertEquals(1,db.count());assertTrue(service.seenVideos.contains("current"));assertEquals(0,service.pendingAi);
                service.resetCommentScan();assertEquals(0L,service.commentEmptyAt);assertEquals("",service.commentEmptyReason);
                service.done=29;service.state="COMMENTS";service.panelRecovery.capture(page);
                service.comments();service.commentEmptyAt-=4000;service.comments();
                assertEquals(30,service.done);assertEquals("DRAIN",service.state);assertEquals(1,backs[0]);
            }catch(Exception e){throw new RuntimeException(e);}finally{service.network.shutdownNow();service.ai.shutdownNow();}
        });
    }
    public void testNewestLogFirstButExportChronological() throws Exception {
        db.log("older_fixture_entry");db.log("newer_fixture_entry");
        String display=db.logs();assertTrue(display.indexOf("newer_fixture_entry")<display.indexOf("older_fixture_entry"));
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();db.writeLogs(out,10);
        String export=out.toString("UTF-8");assertTrue(export.indexOf("older_fixture_entry")<export.indexOf("newer_fixture_entry"));
    }
    public void testNoteComposerRecoveryPreservesProgressAndIsBounded() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            java.util.List<CommentPage.Node> page=new java.util.ArrayList<>();
            page.add(new CommentPage.Node("root","","","FrameLayout",-1,0,0,1080,2200,true,false));
            page.add(new CommentPage.Node("/tv_nickname","author","","TextView",0,0,100,600,200,true,false));
            page.add(new CommentPage.Node("/tv_desc","caption","","TextView",0,0,1700,1080,1900,false,false));
            page.add(new CommentPage.Node("/fn3","说点什么","","EditText",0,0,2000,500,2150,false,false));
            page.add(new CommentPage.Node("/u5e","","","ViewPager",0,0,2500,1080,2200,false,true));
            page.add(new CommentPage.Node("/eyi","留下你的友善评论吧","","EditText",0,0,900,1080,1100,true,false));
            page.add(new CommentPage.Node("/e2x","发送","","TextView",0,900,1100,1080,1250,true,false));
            page.add(new CommentPage.Node("/uir","","返回，按钮","View",0,0,0,1080,2200,true,false));
            final int[] actions={0,0};
            AutomationService service=new AutomationService(){
                @Override public android.content.res.Resources getResources(){return getInstrumentation().getTargetContext().getResources();}
                @Override int screenHeight(){return 2200;}
                @Override CommentPage commentPage(){return new CommentPage(page);}
                @Override boolean isResults(){return false;}
                @Override void snapshotAll(String label){}
                @Override void workerBack(){actions[0]++;}
                @Override boolean swipeNoteInline(int[] path){actions[1]++;return true;}
                @Override void commentProblem(String code,String detail){state=code;}
            };
            try{
                service.db=db;service.state="COMMENTS";service.panelRecovery.source="author\ncaption";
                try{service.config=new JSONObject().put("quantity",true).put("limit",30).put("perVideo",100);}catch(JSONException e){throw new RuntimeException(e);}
                service.videoComments=7;service.seenComments.add("saved");service.pendingKey="pending";
                assertTrue(service.recoverNoteComments());assertTrue(service.recoverNoteComments());
                assertTrue(service.recoverNoteComments());assertEquals(3,actions[0]);
                assertEquals("BACK_RESULTS",service.state);
                page.subList(5,8).clear();
                CommentPage.Node caption=page.get(2);page.set(2,new CommentPage.Node(caption.id,caption.text,"",caption.type,0,0,1700,1080,1900,true,false));
                for(int i=0;i<4;i++)assertTrue(service.recoverNoteComments());
                assertEquals(3,actions[1]);assertEquals("NOTE_INLINE_REVEAL_FAILED",service.state);
                assertEquals(7,service.videoComments);assertEquals(1,service.seenComments.size());assertEquals("pending",service.pendingKey);
                service.panelRecovery.source="other\nwork";assertFalse(service.recoverNoteComments());
                assertEquals(3,actions[0]);assertEquals(3,actions[1]);
            }finally{service.network.shutdownNow();service.ai.shutdownNow();}
        });
    }
    public void testPausePreservesAiAndDispatchedMessage() throws Exception {
        getInstrumentation().runOnMainSync(()->{
            AutomationService service=new AutomationService(){
                @Override void collectWindows(){roots.add(android.view.accessibility.AccessibilityNodeInfo.obtain());}
                @Override public android.content.pm.ApplicationInfo getApplicationInfo(){android.content.pm.ApplicationInfo a=new android.content.pm.ApplicationInfo();a.flags=android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE;return a;}
            };
            try{
                service.db=db;service.prefs=isolated.getSharedPreferences("pause_regression",0);
                service.config=new JSONObject().put("quantity",true);service.running=true;service.taskId="pause_fixture";
                service.state="AI_WAIT";service.pendingKey="pause_ai";service.pendingAi=1;service.taskScanned=17;service.videoComments=7;
                service.began=service.stateAt=android.os.SystemClock.elapsedRealtime()-5000;
                db.add("pause_ai",service.taskId,"video","name","body","","");
                service.pauseTask("回归测试");int generation=service.generation;
                service.stateAt-=600000;service.tick();service.main.removeCallbacks(service.loop);
                assertTrue(service.running);assertTrue(service.paused);assertEquals("AI_WAIT",service.state);
                assertEquals(generation,service.generation);assertEquals(17,service.taskScanned);
                db.ai("pause_ai","意向",0.9,"模拟已提交的请求在暂停期间返回");
                service.resumeRequested=true;service.resumeIfReady(android.os.SystemClock.elapsedRealtime()+600000);
                assertFalse(service.paused);assertEquals("AI_WAIT",service.state);assertEquals("意向",db.comment("pause_ai").optString("ai"));
                for(android.view.accessibility.AccessibilityNodeInfo root:service.roots)root.recycle();service.roots.clear();
                JSONObject options=new JSONObject().put("accounts",new JSONArray().put("pause_fixture_account")).put("body","hello").put("dryRun",false)
                    .put("min",20).put("max",20).put("batchMin",1).put("batchMax",1).put("restMin",60).put("restMax",60).put("daily",30);
                service.messages=new DirectMessageRunner(service,options);service.messages.account="pause_fixture_account";
                service.messages.attempt=db.beginMessage(service.taskId,"pause_fixture_account","hello",false);
                assertTrue(db.reserveSend(service.messages.attempt,"pause_fixture_account",30));service.messages.dispatched=true;
                service.state="DM_VERIFY";service.messages.priorBubbles=2;service.messages.index=0;
                service.pauseTask("发送后暂停");service.tick();service.main.removeCallbacks(service.loop);
                assertTrue(service.messages.dispatched);assertEquals("DM_VERIFY",service.state);
                long resumed=android.os.SystemClock.elapsedRealtime()+600000;service.resumeRequested=true;service.resumeIfReady(resumed);
                assertEquals(resumed,service.stateAt);assertEquals(0,service.messages.evidenceAt);
                assertTrue(service.messages.dispatched);assertEquals(0,service.messages.index);assertEquals(2,service.messages.priorBubbles);
                try(android.database.Cursor c=db.getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts WHERE send_requested>0",null)){assertTrue(c.moveToFirst());assertEquals(1,c.getInt(0));}
            }catch(Exception e){throw new RuntimeException(e);}finally{
                service.main.removeCallbacks(service.loop);for(android.view.accessibility.AccessibilityNodeInfo root:service.roots)root.recycle();service.roots.clear();
                service.network.shutdownNow();service.ai.shutdownNow();
            }
        });
    }
    public void testAiMustFinishBeforeProfileLookup() throws Exception {
        // Isolated state machine; no real device navigation, AI request or messages.
        AutomationService service=new AutomationService(){
            @Override void setState(String value){state=value;stateAt=android.os.SystemClock.elapsedRealtime();}
            @Override void beginReturnComments(){state="RETURN_COMMENTS";}
            @Override java.util.List<android.view.accessibility.AccessibilityNodeInfo> commentRows(){return java.util.Collections.emptyList();}
        };
        service.db=db;service.config=new JSONObject().put("profiles",true);service.pendingKey="intent_key";
        db.add("intent_key","task","video","name","comment","","");
        service.setState("AI_WAIT");db.ai("intent_key","判断中",null,"");service.awaitIntent();assertEquals("AI_WAIT",service.state);
        for(String status:new String[]{"非意向","不确定","判断失败"}){
            db.ai("intent_key",status,0.2,"");service.setState("AI_WAIT");service.awaitIntent();assertEquals("COMMENTS",service.state);
            service.setState("PROFILE");service.profile();assertEquals("RETURN_COMMENTS",service.state);
        }
        db.ai("intent_key","意向",0.9,"");db.account("intent_key","already_known");service.setState("AI_WAIT");service.awaitIntent();assertEquals("COMMENTS",service.state);
        db.account("intent_key","");service.setState("AI_WAIT");service.awaitIntent();assertEquals("COMMENTS",service.state);assertTrue(db.logs().contains("未按昵称猜测取号"));
        db.ai("intent_key","判断中",null,"");service.setState("AI_WAIT");service.stateAt-=61000;service.awaitIntent();assertEquals("COMMENTS",service.state);assertTrue(db.logs().contains("判断等待超时"));
        service.network.shutdownNow();service.ai.shutdownNow();
    }
    public void testMessageDialogAndEditorNotices() throws Exception {
        AutomationService service=new AutomationService();service.db=db;
        JSONObject options=new JSONObject().put("accounts",new JSONArray().put("fixture_account")).put("body","hello").put("dryRun",false)
            .put("min",2).put("max",6).put("batchMin",3).put("batchMax",5).put("restMin",6).put("restMax",18).put("daily",30);
        DirectMessageRunner runner=new DirectMessageRunner(service,options);
        android.view.accessibility.AccessibilityNodeInfo notice=android.view.accessibility.AccessibilityNodeInfo.obtain();notice.setVisibleToUser(true);notice.setText("身份安全验证");service.nodes.add(notice);
        assertEquals("身份安全验证",runner.restriction());notice.setText("为保障用户沟通安全，请遵守社区规范");assertEquals("",runner.restriction());
        notice.setText("");notice.setViewIdResourceName("com.ss.android.ugc.aweme:id/msg_et");notice.setShowingHintText(true);notice.setText("只能发送一条消息");assertEquals("",runner.blocked());
        notice.setText("对方回复后可发");assertEquals("对方回复后可发",runner.blocked());
        android.view.accessibility.AccessibilityNodeInfo failure=android.view.accessibility.AccessibilityNodeInfo.obtain();failure.setVisibleToUser(true);failure.setContentDescription("发送失败");service.nodes.add(failure);
        runner.dispatched=true;assertEquals("抖音显示本次消息发送失败",runner.blocked());
        runner.dispatched=false;notice.setText("只能发送一条消息");assertEquals("",runner.blocked());
        notice.recycle();failure.recycle();service.nodes.clear();service.network.shutdownNow();service.ai.shutdownNow();
    }
    public void testCrashWindowAndDailyLimit() throws Exception {
        long id=db.beginMessage("run1","person1","hello",false);assertTrue(db.reserveSend(id,"person1",1));db.recoverMessages();
        assertEquals("待核实",db.messageState("person1"));assertEquals(-1,db.beginMessage("run2","person1","again",true));
        long second=db.beginMessage("run2","person2","hello",false);assertTrue(second>0);assertFalse(db.reserveSend(second,"person2",1));
        db.recoverMessages();assertEquals("失败",db.messageState("person2"));assertEquals(-1,db.beginMessage("run3","person2","again",false));assertTrue(db.beginMessage("run3","person2","again",true)>0);
    }
    public void testMigrationKeepsExistingComments() throws Exception {
        db.close();isolated.getDatabasePath("automation.db").delete();SQLiteDatabase old=isolated.openOrCreateDatabase("automation.db",0,null);
        old.execSQL("CREATE TABLE comments(key TEXT PRIMARY KEY,task TEXT,video TEXT,name TEXT,body TEXT,place TEXT,posted TEXT,account TEXT,ai TEXT,score REAL,reason TEXT,contacted INTEGER)");old.execSQL("CREATE TABLE logs(id INTEGER PRIMARY KEY AUTOINCREMENT,created INTEGER,message TEXT)");
        old.execSQL("INSERT INTO comments VALUES('key','old_task','video','name','comment','','','12345','意向',0.9,'',1)");old.setVersion(1);old.close();
        db=new Records(isolated);assertEquals(1,db.count());assertEquals("成功",db.messageState("12345"));assertEquals("old_task",db.messageRows().getJSONObject(0).getString("task"));
    }
    public void testAttemptColorsSurviveReopen() throws Exception {
        String[] states={"成功","失败","待核实"};
        for(int i=0;i<states.length;i++){
            String account="color_person"+i;
            db.add("color"+i,"task","video","name","comment","","today");db.account("color"+i,account);
            long attempt=db.beginMessage("run",account,"test",false);
            if(!states[i].equals("失败"))assertTrue(db.reserveSend(attempt,account,10));
            db.messageResult(attempt,account,states[i],"test reason "+i);
        }
        db.close();db=new Records(isolated);
        JSONArray rows=db.messageRows();assertEquals(3,rows.length());int success=0;
        for(int i=0;i<rows.length();i++){
            JSONObject row=rows.getJSONObject(i);String state=row.getString("dm_state");
            assertTrue(MessagePolicy.color(state)!=MessagePolicy.color("未尝试"));
            assertTrue(row.getString("dm_reason").startsWith("test reason"));
            if(row.getString("contacted").equals("1")){success++;assertEquals("成功",state);}
        }
        assertEquals(1,success);
    }
}
