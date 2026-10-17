package dy.lead.client;
import java.util.*;

/** Replays the actual HONOR diagnostic; synthetic variants are explicitly marked. */
public class PartialCommentTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> nodes=NoteAndRecoveryTest.read(args[0]);CommentPage p=new CommentPage(nodes);
        check(p.header>=0&&p.list>=0&&nodes.get(p.list).scrollable,"Keep verified comment list");
        check(!nodes.get(p.list).type.contains("ViewPager"),"Never scroll outer pager");
        check(p.rows.size()==2&&p.partialCount()==2&&p.invalid==2,"Actual failure contains two clipped rows, not an unknown layout");
        check(p.rows.get(0).name.isEmpty()&&!p.rows.get(0).valid(),"Upper picture tail cannot become a customer");
        CommentPage.Row bottom=p.rows.get(1);
        check(bottom.name.equals("琳达同学")&&bottom.body.equals("两个多少钱")&&bottom.posted.isEmpty()&&!bottom.valid(),"Keep lower row text local but defer AI until metadata is visible");
        check(bottom.avatar>=0&&p.inside(bottom.avatar,bottom.source),"Never borrow upper row's avatar/date");
        check(p.end.isEmpty(),"Partial rows do not mean bottom");
        check(p.needsShortSwipe(),"Visible customer clipped at bottom needs cautious advancement");
        int[] viewport=p.viewport(720,1552),drag=CommentSwipe.path(viewport[0],viewport[1],viewport[2],viewport[3],true);
        check(drag!=null&&drag[1]-drag[2]>=150&&drag[1]-drag[2]<=160,"Actual viewport gets about 155px recovery, not 583px");
        // Synthetic next frame: the lower row moves up and its own date/reply become visible.
        List<CommentPage.Node> shifted=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){
            CommentPage.Node n=nodes.get(i);int dy=p.inside(i,bottom.source)?-160:0;
            shifted.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,n.left,n.top+dy,n.right,n.bottom+dy,n.visible,n.scrollable,n.clickable));
        }
        shifted.add(new CommentPage.Node("","09-17","","android.widget.TextView",bottom.source,120,1400,200,1436,true,false));
        shifted.add(new CommentPage.Node("","回复","","android.widget.TextView",bottom.source,290,1400,342,1436,true,false,true));
        CommentPage complete=new CommentPage(shifted);CommentPage.Row resolved=null;
        for(CommentPage.Row r:complete.rows)if(r.name.equals(bottom.name))resolved=r;
        check(resolved!=null&&resolved.valid()&&resolved.posted.equals("09-17")&&resolved.body.equals(bottom.body),"Once metadata appears, parse this same user without mixing adjacent rows");
        check(!p.fingerprint().equals(complete.fingerprint()),"Recovery movement has a non-empty measurable fingerprint");
        check(p.fingerprint().equals(new CommentPage(nodes).fingerprint()),"Stationary partial rows remain identical for bounded stall protection");
        List<CommentPage.Node> fake=new ArrayList<>();
        for(int i=0;i<nodes.size();i++){CommentPage.Node n=nodes.get(i);fake.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,n.visible,n.scrollable,n.clickable&&i!=bottom.avatar));}
        check(new CommentPage(fake).rows.stream().noneMatch(r->r.name.equals(bottom.name)),"Without own avatar do not fabricate a partial customer");
        check(!new CommentPage(fake).needsShortSwipe(),"Top-only picture tail does not unnecessarily slow ordinary scanning");
        System.out.println("PASS: HONOR actual clipped image rows; 20% bounded recovery; no premature AI/bottom; synthetic completion and stationary fingerprints");
    }
}
