package dy.lead.client;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Comment identity is separate from a public account ID; names are never account IDs. */
public final class Records extends SQLiteOpenHelper {
    private volatile String activeTask="";
    public Records(Context c) { super(c,"automation.db",null,3); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE comments (key TEXT PRIMARY KEY, task TEXT, video TEXT, name TEXT, body TEXT, place TEXT, posted TEXT, account TEXT DEFAULT '', ai TEXT DEFAULT '待判断', score REAL, reason TEXT DEFAULT '', contacted INTEGER DEFAULT 0)");
        db.execSQL("CREATE TABLE logs (id INTEGER PRIMARY KEY AUTOINCREMENT, created INTEGER, message TEXT, task_id TEXT NOT NULL DEFAULT '')");
        createMessages(db);
    }
    static void createMessages(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS dm_contacts(account TEXT PRIMARY KEY,state TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',updated INTEGER NOT NULL,sent_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE IF NOT EXISTS dm_attempts(id INTEGER PRIMARY KEY AUTOINCREMENT,run TEXT NOT NULL,account TEXT NOT NULL,body TEXT NOT NULL,state TEXT NOT NULL,reason TEXT NOT NULL DEFAULT '',started INTEGER NOT NULL,updated INTEGER NOT NULL,send_requested INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX IF NOT EXISTS dm_attempt_day ON dm_attempts(send_requested)");
        db.execSQL("INSERT OR IGNORE INTO dm_contacts(account,state,reason,updated,sent_at) SELECT trim(account),'成功','旧版已发送标记',0,1 FROM comments WHERE contacted=1 AND trim(account)<>''");
    }
    public void onUpgrade(SQLiteDatabase db,int a,int b) {if(a<2)createMessages(db);if(a<3)db.execSQL("ALTER TABLE logs ADD COLUMN task_id TEXT NOT NULL DEFAULT ''");}
    /** Never resume an interrupted send automatically: the network may have accepted it. */
    public void recoverMessages(){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            d.execSQL("UPDATE dm_attempts SET state=CASE WHEN send_requested>0 THEN '待核实' ELSE '失败' END,reason=CASE WHEN send_requested>0 THEN '软件退出或任务中断，可能已发送；请手动核实' ELSE '查找中断，未点击发送' END WHERE state IN ('查找中','发送中')");
            d.execSQL("UPDATE dm_contacts SET state=CASE WHEN state='发送中' THEN '待核实' ELSE '失败' END,reason=CASE WHEN state='发送中' THEN '软件退出或任务中断，可能已发送；请手动核实' ELSE '查找中断，未点击发送' END WHERE state IN ('查找中','发送中')");
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public JSONArray messageRows(){
        String sql="SELECT c.*, COALESCE(d.state,'未尝试') AS dm_state,COALESCE(d.reason,'') AS dm_reason,COALESCE(d.sent_at,0) AS dm_sent_at FROM comments c LEFT JOIN dm_contacts d ON trim(c.account)=d.account ORDER BY c.rowid DESC";
        JSONArray rows=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery(sql,null)){while(c.moveToNext())rows.put(json(c));}return rows;
    }
    static JSONObject json(Cursor c){JSONObject o=new JSONObject();for(String key:c.getColumnNames())try{int i=c.getColumnIndexOrThrow(key);o.put(key,c.isNull(i)?JSONObject.NULL:c.getString(i));}catch(JSONException e){throw new IllegalStateException(e);}return o;}
    public String messageState(String account){try(Cursor c=getReadableDatabase().rawQuery("SELECT state FROM dm_contacts WHERE account=?",new String[]{account})){return c.moveToFirst()?c.getString(0):"未尝试";}}
    public long beginMessage(String run,String account,String body,boolean retry){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            if(!MessagePolicy.canSelect(account,messageState(account),retry))return -1;
            long now=System.currentTimeMillis();ContentValues v=new ContentValues();v.put("run",run);v.put("account",account);v.put("body",body);v.put("state","查找中");v.put("started",now);v.put("updated",now);
            long id=d.insertOrThrow("dm_attempts",null,v);ContentValues contact=new ContentValues();contact.put("account",account);contact.put("state","查找中");contact.put("reason","正在按抖音号查找");contact.put("updated",now);d.insertWithOnConflict("dm_contacts",null,contact,SQLiteDatabase.CONFLICT_REPLACE);d.setTransactionSuccessful();return id;
        }finally{d.endTransaction();}
    }
    public int sendsToday(){java.util.Calendar c=java.util.Calendar.getInstance();c.set(java.util.Calendar.HOUR_OF_DAY,0);c.set(java.util.Calendar.MINUTE,0);c.set(java.util.Calendar.SECOND,0);c.set(java.util.Calendar.MILLISECOND,0);try(Cursor r=getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts WHERE send_requested>=?",new String[]{String.valueOf(c.getTimeInMillis())})){r.moveToFirst();return r.getInt(0);}}
    public boolean reserveSend(long attempt,String account,int daily){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            if(sendsToday()>=daily||!messageState(account).equals("查找中"))return false;
            ContentValues v=new ContentValues();v.put("state","发送中");v.put("send_requested",System.currentTimeMillis());v.put("updated",System.currentTimeMillis());
            if(d.update("dm_attempts",v,"id=? AND send_requested=0 AND state='查找中'",new String[]{String.valueOf(attempt)})!=1)return false;
            v.remove("send_requested");d.update("dm_contacts",v,"account=?",new String[]{account});d.setTransactionSuccessful();return true;
        }finally{d.endTransaction();}
    }
    public void messageResult(long attempt,String account,String state,String reason){
        SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("state",state);v.put("reason",reason);v.put("updated",System.currentTimeMillis());
            d.update("dm_attempts",v,"id=?",new String[]{String.valueOf(attempt)});
            if(state.equals("成功"))v.put("sent_at",System.currentTimeMillis());
            d.update("dm_contacts",v,"account=?",new String[]{account});
            if(state.equals("成功")){ContentValues sent=new ContentValues();sent.put("contacted",1);d.update("comments",sent,"trim(account)=?",new String[]{account});}
            d.setTransactionSuccessful();
        }finally{d.endTransaction();}
    }
    public static String key(String raw) {
        try { byte[] bytes=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)); StringBuilder s=new StringBuilder(); for(byte b:bytes)s.append(String.format("%02x",b));return s.toString(); } catch(Exception e){throw new IllegalStateException(e);}
    }
    public boolean add(String key,String task,String video,String name,String body,String place,String posted) {
        ContentValues v=new ContentValues();v.put("key",key);v.put("task",task);v.put("video",video);v.put("name",name);v.put("body",body);v.put("place",place);v.put("posted",posted);
        return getWritableDatabase().insertWithOnConflict("comments",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public void account(String key,String account) { ContentValues v=new ContentValues();v.put("account",account);getWritableDatabase().update("comments",v,"key=?",new String[]{key}); }
    public JSONObject comment(String key){try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM comments WHERE key=?",new String[]{key})){return c.moveToFirst()?json(c):new JSONObject();}}
    public void ai(String key,String status,Double score,String reason) {ContentValues v=new ContentValues();v.put("ai",status);if(score==null)v.putNull("score");else v.put("score",score);v.put("reason",reason);getWritableDatabase().update("comments",v,"key=?",new String[]{key});}
    public void setTask(String task){activeTask=task==null?"":task;}
    public void log(String message) { ContentValues v=new ContentValues();v.put("created",System.currentTimeMillis());v.put("message",message);v.put("task_id",activeTask);getWritableDatabase().insert("logs",null,v);android.util.Log.i("LeadAutomation",message); }
    public String taskLogs(String task){StringBuilder out=new StringBuilder();java.text.SimpleDateFormat time=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",java.util.Locale.ROOT);try(Cursor c=getReadableDatabase().rawQuery("SELECT created,message FROM (SELECT id,created,message FROM logs WHERE task_id=? ORDER BY id DESC LIMIT 3000) ORDER BY id",new String[]{task})){while(c.moveToNext()){String line=time.format(new java.util.Date(c.getLong(0)))+" "+c.getString(1)+"\n";if(out.length()+line.length()>250000)break;out.append(line);}}return out.toString();}
    public JSONArray tasks(){JSONArray out=new JSONArray();java.util.LinkedHashMap<String,Long> found=new java.util.LinkedHashMap<>();try(Cursor c=getReadableDatabase().rawQuery("SELECT task_id,MAX(created) AS latest FROM logs WHERE task_id<>'' GROUP BY task_id ORDER BY latest DESC",null)){while(c.moveToNext())found.put(c.getString(0),c.getLong(1));}try(Cursor c=getReadableDatabase().rawQuery("SELECT task,MAX(rowid) FROM comments WHERE task<>'' GROUP BY task ORDER BY MAX(rowid) DESC",null)){while(c.moveToNext())if(!found.containsKey(c.getString(0)))found.put(c.getString(0),0L);}for(java.util.Map.Entry<String,Long> e:found.entrySet())try{out.put(new JSONObject().put("task",e.getKey()).put("created",e.getValue()));}catch(JSONException ex){throw new IllegalStateException(ex);}return out;}
    public int deleteTask(String task){SQLiteDatabase d=getWritableDatabase();d.beginTransaction();try{int n=d.delete("comments","task=?",new String[]{task});d.delete("logs","task_id=?",new String[]{task});d.delete("dm_attempts","run=?",new String[]{task});d.setTransactionSuccessful();return n;}finally{d.endTransaction();}}
    public String logs() {java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();try{writeLogs(out,150,true);}catch(java.io.IOException e){throw new IllegalStateException(e);}return "最近日志（最新在上，含历史记录）\n"+new String(out.toByteArray(),StandardCharsets.UTF_8);}
    public void writeLogs(java.io.OutputStream out,int limit)throws java.io.IOException {
        writeLogs(out,limit,false);
    }
    void writeLogs(java.io.OutputStream out,int limit,boolean newestFirst)throws java.io.IOException {
        java.text.SimpleDateFormat time=new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",java.util.Locale.ROOT);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT created,message FROM (SELECT id,created,message FROM logs ORDER BY id DESC LIMIT ?) ORDER BY id"+(newestFirst?" DESC":""),new String[]{String.valueOf(limit)})){
            while(c.moveToNext())out.write((time.format(new java.util.Date(c.getLong(0)))+" "+c.getString(1)+"\n").getBytes(StandardCharsets.UTF_8));
        }
    }
    public JSONArray all() {JSONArray a=new JSONArray();try(Cursor c=getReadableDatabase().rawQuery("SELECT * FROM comments ORDER BY rowid DESC LIMIT 5000",null)){while(c.moveToNext()){JSONObject o=new JSONObject();for(String k:c.getColumnNames()){int i=c.getColumnIndexOrThrow(k);try{o.put(k,c.isNull(i)?JSONObject.NULL:c.getString(i));}catch(JSONException e){throw new IllegalStateException(e);}}a.put(o);}}return a;}
    public int count() {try(Cursor c=getReadableDatabase().rawQuery("SELECT count(*) FROM comments",null)){c.moveToFirst();return c.getInt(0);}}
}
