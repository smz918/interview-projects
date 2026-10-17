package dy.lead.client;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.test.InstrumentationTestCase;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;

/** Synthetic UI trees and isolated logs; never clicks or posts on Douyin. */
public class InteractionSingleWorkTest extends InstrumentationTestCase {
    Probe s;
    static class Probe extends AutomationService {
        int clicks, exits, backs;
        @Override boolean click(AccessibilityNodeInfo n){clicks++;return true;}
        @Override void beginReturnComments(){exits++;}
        @Override void setState(String state){this.state=state;}
        @Override void settle(long ms){}
        @Override void interactionBack(){backs++;}
    }
    @Override protected void setUp() throws Exception {
        super.setUp();s=new Probe();
        Context base=getInstrumentation().getTargetContext();
        Context isolated=new ContextWrapper(base){
            @Override public java.io.File getDatabasePath(String name){return super.getDatabasePath("single_work_fixture.db");}
        };
        s.db=new Records(isolated);
        s.config=new JSONObject().put("interactionMode",true).put("worksPerUser",3).put("likeWorks",true);
        s.interactionProfileWork=true;
    }
    @Override protected void tearDown() throws Exception {
        for(AccessibilityNodeInfo n:s.nodes)n.recycle();s.db.close();
        getInstrumentation().getTargetContext().deleteDatabase("single_work_fixture.db");super.tearDown();
    }
    AccessibilityNodeInfo node(String id,String text,String desc,boolean clickable){
        AccessibilityNodeInfo n=AccessibilityNodeInfo.obtain();n.setViewIdResourceName("com.ss.android.ugc.aweme:id/"+id);
        n.setText(text);n.setContentDescription(desc);n.setVisibleToUser(true);n.setClickable(clickable);
        n.setBoundsInScreen(new Rect(10,500,220,820));s.nodes.add(n);return n;
    }
    public void testOldThreeSettingCannotReopenFirstTile(){
        node("qbt","","",true);s.interactionWork();s.interactionWork();
        assertEquals(1,s.clicks);assertEquals(1,s.interactionWorksDone);assertEquals(1,s.exits);
        assertFalse(s.interactionSendRequested);
    }
    public void testScrolledProfileReturnsToSourceRegardlessOfOldCount(){
        node("text1","作品 70","",true);node("qbt","","",true);
        assertTrue(s.isCustomerProfile());s.interactionWorksDone=1;s.returnInteraction();
        assertEquals("RETURN_COMMENTS",s.state);assertEquals(1,s.backs);assertEquals(0,s.clicks);
        assertFalse(s.interactionProfileWork);
    }
    public void testNoWorksDoesNotRestartWorkLoop(){
        node("account","抖音号：fixture","",false);s.interactionNoMoreWorks=true;s.returnInteraction();
        assertEquals("RETURN_COMMENTS",s.state);assertEquals(1,s.backs);assertEquals(0,s.clicks);
    }
    public void testDirectCommentPreservesLikeAndNeverOpensPanel(){
        node("eyi","期待你的评论","",true);node("guy","","未点赞，喜欢19，按钮",true);
        node("e5x","","评论12，按钮",true);
        assertFalse(s.isCustomerProfile());s.openComments();s.openComments();
        assertEquals(1,s.clicks);assertEquals("INTERACTION_SEND",s.state);
    }
    public void testWorkEditorPreventsFalseProfileDetection(){
        node("account","抖音号：fixture","",false);node("eyi","期待你的评论","",true);
        assertFalse(s.isCustomerProfile());
    }
}
