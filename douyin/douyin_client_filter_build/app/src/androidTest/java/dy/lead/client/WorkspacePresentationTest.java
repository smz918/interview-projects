package dy.lead.client;

import android.test.InstrumentationTestCase;
import android.widget.*;
import org.json.*;

/** Fixture-only presentation checks: no collection, messages or production DB writes. */
public class WorkspacePresentationTest extends InstrumentationTestCase {
    AutomationActivity launched;
    AutomationActivity getActivity() throws Exception {
        android.app.Instrumentation.ActivityMonitor monitor=getInstrumentation().addMonitor(AutomationActivity.class.getName(),null,false);
        try(android.os.ParcelFileDescriptor command=getInstrumentation().getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).executeShellCommand("am start -n dy.lead.client/.AutomationActivity")){
            launched=(AutomationActivity)getInstrumentation().waitForMonitorWithTimeout(monitor,10000);assertNotNull("Activity launch timed out",launched);
            getInstrumentation().waitForIdleSync();return launched;
        }finally{getInstrumentation().removeMonitor(monitor);}
    }
    @Override protected void tearDown() throws Exception {if(launched!=null)getInstrumentation().runOnMainSync(()->launched.finish());super.tearDown();}
    public void testTaskCollapsePaginationAndSharedSelection() throws Throwable {
        AutomationActivity activity=getActivity();
        int originalCount=activity.db.count();assertEquals(0,activity.db.comment("duplicate").length());
        JSONArray fixture=new JSONArray();
        for(int i=0;i<81;i++)fixture.put(row("a"+i,"a","user"+i));
        fixture.put(row("duplicate","a","user0"));fixture.put(row("b0","b","user0"));
        fixture.put(row("no_intent","c","notshown").put("ai","非意向"));
        java.io.File fixtureDb=activity.getDatabasePath("workspace_presentation_fixture.db");
        android.content.Context isolated=new android.content.ContextWrapper(activity){
            @Override public java.io.File getDatabasePath(String name){return fixtureDb;}
        };
        Records source=new Records(isolated);
        for(int i=0;i<fixture.length();i++){JSONObject row=fixture.getJSONObject(i);source.add(row.getString("key"),row.getString("task"),"video",row.getString("name"),row.getString("body"),"","");source.account(row.getString("key"),row.getString("account"));source.ai(row.getString("key"),row.getString("ai"),.99,"");}
        runTestOnUiThread(()->{
            activity.ui.removeCallbacks(activity.refresh);
            android.content.SharedPreferences prefs=activity.getSharedPreferences("presentation_test",0);prefs.edit().clear().commit();
            MessagePanel panel=new MessagePanel(activity,source,prefs);panel.build(new LinearLayout(activity),activity.style);
            assertEquals(82,panel.displayed.size());assertTrue(panel.expanded.isEmpty());assertTrue(panel.taskBodies.isEmpty());assertEquals(3,panel.list.getChildCount());
            panel.expanded.add("a");panel.expanded.add("b");panel.render();
            assertEquals(40,panel.taskBodies.get("a").getChildCount());panel.appendRows("a",80);panel.appendRows("a",120);assertEquals(81,panel.taskBodies.get("a").getChildCount());
            LinearLayout first=(LinearLayout)panel.taskBodies.get("a").getChildAt(0);CheckBox choice=(CheckBox)((LinearLayout)first.getChildAt(0)).getChildAt(0);choice.setChecked(true);
            assertEquals(1,panel.selected.size());LinearLayout second=(LinearLayout)panel.taskBodies.get("b").getChildAt(0);assertTrue(((CheckBox)((LinearLayout)second.getChildAt(0)).getChildAt(0)).isChecked());
            assertEquals(2,panel.taskRows.get("a").get(0).optInt("merged"));
            panel.expanded.clear();panel.render();assertEquals(3,panel.list.getChildCount());assertTrue(panel.taskBodies.isEmpty());source.close();android.database.sqlite.SQLiteDatabase.deleteDatabase(fixtureDb);assertEquals(originalCount,activity.db.count());assertEquals(0,activity.db.comment("duplicate").length());
        });
    }
    public void testRoundedMenuSelectionAndDismissal() throws Throwable {
        AutomationActivity activity=getActivity();
        runTestOnUiThread(()->{
            activity.ui.removeCallbacks(activity.refresh);
            WorkspaceUi.RoundedSpinner spinner=(WorkspaceUi.RoundedSpinner)activity.filters.get("sort");
            assertTrue(spinner.getWidth()>0);assertTrue(spinner.performClick());assertTrue(spinner.popup.isShowing());assertEquals(spinner.getWidth(),spinner.popup.getWidth());
            ScrollView scroll=(ScrollView)spinner.popup.getContentView();LinearLayout options=(LinearLayout)scroll.getChildAt(0);
            assertTrue(options.getClipToOutline());assertEquals(5,options.getChildCount());options.getChildAt(0).performClick();assertEquals("综合排序",spinner.getSelectedItem());assertFalse(spinner.popup.isShowing());
            spinner.performClick();spinner.popup.dismiss();assertFalse(spinner.popup.isShowing());
        });
    }
    private JSONObject row(String key,String task,String account) throws JSONException {
        return new JSONObject().put("key",key).put("task",task).put("account",account).put("name",key).put("body","fixture comment").put("place","").put("posted","").put("ai","意向").put("score",.99).put("dm_state","未尝试");
    }
}
