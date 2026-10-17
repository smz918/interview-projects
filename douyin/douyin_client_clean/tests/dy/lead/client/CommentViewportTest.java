package dy.lead.client;
import java.util.*;

public class CommentViewportTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> nodes=NoteAndRecoveryTest.read(args[0]);CommentPage page=new CommentPage(nodes);
        check(page.nodes.get(page.list).id("rkp"),"Select actual comment RecyclerView instead of outer u5e ViewPager");
        check(page.nodes.get(page.editor).id("fn3"),"Vivo fixed composer must bound visible list");
        check(page.needsExpansion(720,1470),"Clipped list under composer must expand before reading");
        int[] b=page.viewport(720,1470);check(b[1]==1430&&b[3]==1424,"Actual captured list is behind fixed footer");
        check(CommentSwipe.path(b[0],b[1],b[2],b[3])==null,"Do not submit the old 73px ineffective swipe");
        int button=-1;int score=-1;for(int i=0;i<nodes.size();i++){CommentPage.Node n=nodes.get(i);int s=CommentEntry.score(n.id,n.text,n.desc,n.visible,n.left,n.top,n.right,n.bottom,720,1470);if(s>score){score=s;button=i;}}
        check(button>=0&&nodes.get(button).id("qsn"),"Existing selector finds Vivo footer button to expand note");
        check(!CommentEntry.canUseFooterToExpand(true,true,true),"Do not tap video footer when a readable list already exists; it opens the comment editor on TYH612M");
        check(CommentEntry.canUseFooterToExpand(true,true,false),"Keep footer expansion for an exposed list with no readable rows yet");
        check(CommentEntry.canUseFooterToExpand(true,false,false),"Keep footer expansion when the list has not appeared");
        check(!CommentEntry.canUseFooterToExpand(false,false,false),"Never use an absent footer");
        // Resolution and obfuscated row IDs must not change the decision.
        List<CommentPage.Node> scaled=new ArrayList<>();
        for(CommentPage.Node n:nodes)scaled.add(new CommentPage.Node(n.id.replace("/flq","/new_row"),n.text,n.desc,n.type,n.parent,n.left*2,n.top*2,n.right*2,n.bottom*2,n.visible,n.scrollable));
        check(new CommentPage(scaled).needsExpansion(1440,2940),"Resolution independent expansion");
        for(int i=1;i<args.length;i++){
            CommentPage old=new CommentPage(NoteAndRecoveryTest.read(args[i]));
            check(old.header>=0&&old.list>=0,"Old phone parser remains available: "+args[i]);
            check(!old.needsExpansion(1080,2239),"Already open Huawei panels must not be reopened: "+args[i]);
        }
        System.out.println("PASS: actual Vivo hidden list, fn3 footer, qsn expansion, scaled/new row IDs, Huawei open/bottom compatibility");
    }
}
