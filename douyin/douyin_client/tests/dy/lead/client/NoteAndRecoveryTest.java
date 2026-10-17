package dy.lead.client;
import java.io.File;
import java.util.*;
import javax.xml.parsers.*;
public class NoteAndRecoveryTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static List<CommentPage.Node> read(String file)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        List<CommentPage.Node> n=new ArrayList<>();CommentPageTest.walk(f.newDocumentBuilder().parse(new File(file)).getDocumentElement(),-1,n);return n;
    }
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> closed=read(args[0]);CommentPage.Node best=null;int score=-1;
        for(CommentPage.Node n:closed){int s=CommentEntry.score(n.id,n.text,n.desc,n.visible,n.left,n.top,n.right,n.bottom,1080,2239);if(s>score){score=s;best=n;}}
        check(best!=null&&best.id("qn9"),"Choose actual note footer button, not 12px clipped heading");
        check(CommentEntry.score("legacy","","评论 50，按钮",true,920,1000,1060,1150,1080,2239)>0,"Keep legacy video button");
        check(CommentEntry.score("legacy","","评论 50",true,920,1000,1060,1150,1080,2239)>0,"Keep old description selector");
        List<CommentPage.Node> opened=read(args[1]);CommentPage p=new CommentPage(opened);
        check(p.header>=0&&p.list>=0&&p.rows.size()>=2,"Read real inline note comments");
        check(opened.get(p.editor).id("fn2"),"Use lower fixed editor, not upper inline composer");
        List<CommentPage.Node> bottom=new ArrayList<>(opened);bottom.add(new CommentPage.Node("footer","暂时没有更多了","","android.widget.TextView",p.list,0,2010,1080,2080,true,false));
        check(!new CommentPage(bottom).end.isEmpty(),"Note footer below inline composer must still be recognized");
        for(String id:new String[]{"flq","flr","new_row"})check(new CommentPage(CommentPageTest.modify(opened,id,false,false,false)).rows.size()==p.rows.size(),"Old/new note rows "+id);
        check(new CommentPage(CommentPageTest.modify(opened,"flr",false,false,true)).end.isEmpty(),"Comment body cannot fake note footer");
        if(args.length>2){CommentPage actualBottom=new CommentPage(read(args[2]));check(actualBottom.end.equals("暂时没有更多了"),"Real note bottom overlaps composer by 6px but remains readable");}
        WindowReadRecovery r=new WindowReadRecovery();r.begin(100);
        for(int i=0;i<3;i++){long time=100+(i+1)*40000L;check(!r.due(time-1),"Must wait full 40 seconds");check(r.due(time)&&!r.exhausted(),"Three recovery rounds");r.retried(time);}
        check(r.exhausted()&&!r.due(160099)&&r.due(160100),"Third swipe gets a final 40 seconds before stopping");
        check(r.duration(160100)==160000,"Pause duration retains original stage deadline");r.reset();r.begin(200000);check(r.rounds==0&&!r.due(200000),"Successful recovery / new task clears retries");
        System.out.println("PASS: real note entry, legacy selectors, note footer, old/new rows, bounded 40s x 3 recovery and final observation");
    }
}
