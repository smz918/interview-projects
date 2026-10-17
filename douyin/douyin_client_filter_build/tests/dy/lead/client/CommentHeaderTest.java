package dy.lead.client;
import java.util.*;

public class CommentHeaderTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(String title:new String[]{"评论","评论7","评论 1955","7条评论","0条评论","7 评论"," 7\u00a0条\u3000评论 ","1.2万条评论","评论1,955","999+条评论"})
            check(CommentPage.header(title),"Accept heading: "+title);
        for(String text:new String[]{"期待你的评论","发条评论","回复7条评论","我发了7条评论","7条评论，按钮","没有更多评论了","评论很好看","条评论","万条评论"})
            check(!CommentPage.header(text),"Reject non-heading: "+text);
        List<CommentPage.Node> nodes=NoteAndRecoveryTest.read(args[0]);CommentPage actual=new CommentPage(nodes);
        check(actual.header>=0&&actual.nodes.get(actual.header).text.equals("7条评论"),"Recognize actual TYH612M title");
        check(actual.list>=0&&actual.nodes.get(actual.list).id("rkp"),"Anchor actual comments RecyclerView");
        check(actual.rows.size()==5&&actual.invalid==0,"Read five captured comments including author reply");
        check(actual.rows.get(0).body.equals("28万首付？")&&actual.rows.get(0).posted.equals("09-06"),"Keep original body and date");
        check(actual.end.isEmpty()&&!actual.needsExpansion(1080,2292),"Open non-bottom sheet needs neither reopening nor expansion");
        for(String title:new String[]{"评论7","7条评论","1.2万条评论"}){
            List<CommentPage.Node> variant=new ArrayList<>();
            for(int i=0;i<nodes.size();i++){CommentPage.Node n=nodes.get(i);variant.add(new CommentPage.Node(n.id,i==actual.header?title:n.text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,n.visible,n.scrollable));}
            CommentPage parsed=new CommentPage(variant);check(parsed.rows.size()==actual.rows.size(),"Same rows with heading: "+title);
            for(int i=0;i<parsed.rows.size();i++)check(parsed.rows.get(i).identity().equals(actual.rows.get(i).identity()),"Header variants keep dedup identities");
        }
        check(new CommentPage(NoteAndRecoveryTest.read(args[1])).header<0,"Video before opening is not a comment sheet");
        System.out.println("PASS: actual TYH612M 7条评论 XML, old/new headings, unchanged rows/identity, non-heading rejection");
    }
}
