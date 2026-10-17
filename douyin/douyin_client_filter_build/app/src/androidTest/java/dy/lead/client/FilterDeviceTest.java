package dy.lead.client;

import android.content.*;
import android.os.*;
import android.test.InstrumentationTestCase;
import org.json.JSONObject;

/** Explicitly invoked on a connected phone: search/filter only, no social actions. */
public class FilterDeviceTest extends InstrumentationTestCase {
    public void testSavedSearchFilters() throws Exception {
        Context context=getInstrumentation().getTargetContext();
        getInstrumentation().startActivitySync(new Intent(context,AutomationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline=SystemClock.elapsedRealtime()+20000;
        while(AutomationService.instance==null&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(200);
        final AutomationService service=AutomationService.instance;
        assertNotNull("Enable accessibility before running this test",service);
        assertFalse("Do not interrupt an existing task",service.running);
        SharedPreferences prefs=context.getSharedPreferences("workspace",0);
        JSONObject config=new JSONObject().put("filterOnly",true).put("quantity",true).put("keyword",prefs.getString("keyword","旅游签证")).put("limit",1).put("perVideo",1);
        for(String key:new String[]{"sort","published","duration","scope","form","distance"})config.put(key,prefs.getString(key,key.equals("sort")?"最多评论":"不限"));
        getInstrumentation().runOnMainSync(()->service.startTask(config));
        try{
            deadline=SystemClock.elapsedRealtime()+180000;
            while(service.running&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(500);
            assertFalse("Filter task timed out",service.running);
            String status=prefs.getString("task_status","");
            Bundle result=new Bundle();result.putString("stream",status+"\n");getInstrumentation().sendStatus(0,result);
            assertTrue(status,status.startsWith("搜索筛选测试通过"));
            assertEquals("Must not open any content",0,service.done);
        }finally{getInstrumentation().runOnMainSync(()->{if(service.running)service.finish("筛选回归测试超时");});}
    }
}
