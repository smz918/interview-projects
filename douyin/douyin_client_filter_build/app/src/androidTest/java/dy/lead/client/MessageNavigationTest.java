package dy.lead.client;
import android.content.Intent;
import android.os.*;
import android.test.InstrumentationTestCase;
import org.json.*;

/** Debug-only read-only probe; never sends or fills a draft. */
public class MessageNavigationTest extends InstrumentationTestCase {
    public void testExactAccountAndMissingAccountWithoutSending() throws Exception {
        getInstrumentation().startActivitySync(new Intent(getInstrumentation().getTargetContext(),AutomationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long until=SystemClock.elapsedRealtime()+20000;while(AutomationService.instance==null&&SystemClock.elapsedRealtime()<until)Thread.sleep(200);
        assertNotNull("Accessibility service must be enabled",AutomationService.instance);
        final AutomationService service=AutomationService.instance;assertFalse(service.running);
        final JSONObject config=new JSONObject().put("dmMode",true).put("dryRun",true).put("accounts",new JSONArray().put("343266215").put("2739330746"))
            .put("body","").put("min",2).put("max",2).put("batchMin",2).put("batchMax",2).put("restMin",2).put("restMax",2).put("daily",1).put("quantity",true);
        Records records=new Records(getInstrumentation().getTargetContext());long before;
        try(android.database.Cursor c=records.getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts",null)){c.moveToFirst();before=c.getLong(0);}
        getInstrumentation().runOnMainSync(()->service.startTask(config));
        until=SystemClock.elapsedRealtime()+180000;while(service.running&&SystemClock.elapsedRealtime()<until)Thread.sleep(500);
        if(service.running)getInstrumentation().runOnMainSync(()->service.finish("只检查测试超时"));
        try(android.database.Cursor c=records.getReadableDatabase().rawQuery("SELECT count(*) FROM dm_attempts",null)){c.moveToFirst();assertEquals("Dry run must never write send attempts",before,c.getLong(0));}
        Bundle result=new Bundle();result.putString("stream",service.messages.summary()+"\n");getInstrumentation().sendStatus(0,result);
        assertEquals("Verified known account should reach chat",1,service.messages.success);assertEquals("Missing account should have specific failure",1,service.messages.failed);records.close();
    }
}
