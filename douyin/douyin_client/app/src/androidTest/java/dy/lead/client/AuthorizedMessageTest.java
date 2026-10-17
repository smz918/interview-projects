package dy.lead.client;
import android.content.Intent;
import android.os.*;
import android.test.InstrumentationTestCase;
import android.database.Cursor;
import org.json.*;

/** Explicit opt-in only. One user-authorized recipient and body, no broad campaign. */
public class AuthorizedMessageTest extends InstrumentationTestCase {
    public void testSendOnceToAuthorizedAccount() throws Exception {
        Bundle args=((android.test.InstrumentationTestRunner)getInstrumentation()).getArguments();
        assertEquals("Explicit recipient required", "huainanwangd75", args.getString("authorizedRecipient"));
        getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),AutomationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until=SystemClock.elapsedRealtime()+20000;while(AutomationService.instance==null&&SystemClock.elapsedRealtime()<until)Thread.sleep(200);
        assertNotNull("Accessibility service unavailable",AutomationService.instance);
        AutomationService service=AutomationService.instance;assertFalse("Another task is running",service.running);
        Records records=new Records(getInstrumentation().getTargetContext());
        try(Cursor c=records.getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts WHERE account=? AND send_requested>0",new String[]{"huainanwangd75"})){
            c.moveToFirst();assertEquals("Already dispatched once; do not repeat test",0,c.getInt(0));
        }
        JSONObject config=new JSONObject().put("dmMode",true).put("dryRun",false).put("retry",true).put("accounts",new JSONArray().put("huainanwangd75"))
            .put("body","你好").put("min",20).put("max",20).put("batchMin",1).put("batchMax",1).put("restMin",60).put("restMax",60).put("daily",30).put("quantity",true);
        getInstrumentation().runOnMainSync(()->service.startTask(config));
        until=SystemClock.elapsedRealtime()+180000;
        while(service.running&&SystemClock.elapsedRealtime()<until)Thread.sleep(500);
        if(service.running)getInstrumentation().runOnMainSync(()->service.finish("测试等待超时，停止"));
        Bundle result=new Bundle();result.putString("stream","run="+service.taskId+"\n"+service.messages.summary()+"\n"+records.logs());getInstrumentation().sendStatus(0,result);
        assertEquals("Must confirm success in app evidence",1,service.messages.success);records.close();
    }
}
