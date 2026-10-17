package dy.lead.client;
import java.util.*;

/** Real failed Vivo note plus negative cases: missing controls never mean zero comments. */
public final class EmptyNoteTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> actual=NoteAndRecoveryTest.read(args[0]);
        for(double scale:new double[]{0.67,1,1.5}){
            int width=(int)(1080*scale),height=(int)(2141*scale);List<CommentPage.Node> nodes=new ArrayList<>();
            for(CommentPage.Node n:actual)nodes.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,(int)(n.left*scale),(int)(n.top*scale),(int)(n.right*scale),(int)(n.bottom*scale),n.visible,n.scrollable,n.clickable));
            CommentPage p=new CommentPage(nodes);check(p.header>=0&&p.list<0,"Actual note exposes a tab, no visible list");
            check(p.needsExpansion(width,height),"No list yet must still trigger note expansion");
            check(p.emptyReason(width,height).isEmpty(),"Do not skip the unopened note as zero comments");
            int best=-1,score=-1;for(int i=0;i<nodes.size();i++){CommentPage.Node n=nodes.get(i);int s=CommentEntry.score(n.id,n.text,n.desc,n.visible,n.left,n.top,n.right,n.bottom,width,height);if(s>score){best=i;score=s;}}
            check(best>=0&&nodes.get(best).id("qsn"),"Click actual footer 评论评论 button instead of tab");
        }
        check(!CommentEntry.action("回复评论，按钮"),"Never select reply action");
        check(!CommentEntry.action("评论很好看"),"Never select user prose");
        List<CommentPage.Node> empty=new ArrayList<>();
        empty.add(new CommentPage.Node("root","","","FrameLayout",-1,0,0,1080,2141,true,false));
        empty.add(new CommentPage.Node("header","评论 0","","TextView",0,0,700,1080,780,true,false));
        empty.add(new CommentPage.Node("editor","说点什么...","","EditText",0,0,2000,1080,2141,true,false));
        check(!new CommentPage(empty).emptyReason(1080,2141).isEmpty(),"Expanded zero-count panel is explicit evidence even without a list");
        empty.set(1,new CommentPage.Node("header","评论","","TextView",0,0,700,1080,780,true,false));
        check(new CommentPage(empty).emptyReason(1080,2141).isEmpty(),"Bare 评论 header does not prove zero");
        empty.add(new CommentPage.Node("empty","暂无评论，快来抢沙发吧","","TextView",0,0,1100,1080,1180,true,false));
        check(!new CommentPage(empty).emptyReason(1080,2141).isEmpty(),"Explicit visible empty prompt is recognized");
        empty.add(new CommentPage.Node("loading","","","ProgressBar",0,0,1200,100,1300,true,false));
        check(new CommentPage(empty).emptyReason(1080,2141).isEmpty(),"Loading must not be marked complete");
        for(int i=1;i<args.length;i++){
            CommentPage old=new CommentPage(NoteAndRecoveryTest.read(args[i]));
            check(old.list>=0&&!old.rows.isEmpty(),"Legacy comment rows still parsed: "+args[i]);
            check(old.emptyReason(1080,2239).isEmpty(),"Nonempty old phone sheet cannot be skipped");
        }
        System.out.println("PASS: actual Vivo no-list note, three resolutions, correct footer, explicit empty/zero, loading and legacy nonempty sheets");
    }
}
