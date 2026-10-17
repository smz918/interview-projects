package dy.lead.client;
import java.util.*;

public class AnonymousCommentTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> nodes=NoteAndRecoveryTest.read(args[0]);CommentPage page=new CommentPage(nodes);
        check(page.header>=0&&page.list>=0,"Recognize anonymous comment sheet");
        check(nodes.get(page.list).id.isEmpty()&&!nodes.get(page.list).type.contains("ViewPager"),"Scroll actual list, not outer pager");
        check(page.rows.size()==7&&page.invalid==1,"Six complete rows plus one clipped row: rows="+page.rows.size()+" invalid="+page.invalid);
        String[] names={"璀璨剧场","蔷薇i","草莓🍓气泡水","生活小确幸","西呱","露露"};
        String[] bodies={"多少一克","项链回收多少钱","你好，咨询一下","现在黄金多少钱一克","黄金回收怎么算","黄金40来克"};
        for(int i=0;i<names.length;i++){
            CommentPage.Row row=page.rows.get(i);
            check(row.name.equals(names[i])&&row.body.equals(bodies[i]),"Correct local name/body #"+i+": "+row.name+" / "+row.body);
            check(row.avatar>=0&&page.inside(row.avatar,row.source)&&nodes.get(row.avatar).clickable,"Own clickable avatar #"+i);
            check(!row.posted.isEmpty()&&!row.place.isEmpty(),"Metadata retained #"+i);
        }
        check(page.end.isEmpty()&&!page.needsExpansion(1080,2292),"Do not mistake unread tail for bottom");
        for(int mode=0;mode<3;mode++){
            List<CommentPage.Node> variant=new ArrayList<>();
            for(int i=0;i<nodes.size();i++){
                CommentPage.Node n=nodes.get(i);boolean body=n.text.equals(bodies[0]);
                variant.add(new CommentPage.Node(n.id,mode==0&&body?"没有更多了":n.text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,n.visible&&!(mode==1&&body),n.scrollable,n.clickable&&!(mode==2&&i==page.rows.get(0).avatar)));
            }
            CommentPage parsed=new CommentPage(variant);
            check(parsed.end.isEmpty(),"No false bottom with user text or incomplete row");
            if(mode==1)check(parsed.invalid==2,"Missing body marked invalid, not borrowed from next row");
            if(mode==2)check(parsed.rows.size()==6,"Missing avatar cannot target a different person");
        }
        for(String stamp:new String[]{"昨天 17:16","今天 8:04","前天 23:59","09-16 08:24","2026-09-16 08:24","2026年9月16日 08:24","2小时前","昨天"})check(CommentPage.posted(stamp),"Date format: "+stamp);
        for(String stamp:new String[]{"昨天 25:00","昨天 08:60","昨天要多少钱","回复","多少一克"})check(!CommentPage.posted(stamp),"Reject non-date: "+stamp);
        CommentPage second=new CommentPage(NoteAndRecoveryTest.read(args[1]));
        check(second.rows.size()==5&&second.invalid==0,"Second actual note with yesterday/time: five valid rows");
        check(second.rows.get(0).body.equals("有这么高吗？...")&&second.rows.get(0).posted.equals("昨天 08:24"),"Do not merge nested first-comment badge into body");
        check(second.rows.get(2).body.equals("今日au750回收多少一克"),"Keep actual second note body");
        for(int width:new int[]{540,720,1440}){
            List<CommentPage.Node> scaled=new ArrayList<>();
            for(CommentPage.Node n:nodes)scaled.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,n.left*width/1080,n.top*width/1080,n.right*width/1080,n.bottom*width/1080,n.visible,n.scrollable,n.clickable));
            CommentPage parsed=new CommentPage(scaled);check(parsed.rows.size()==page.rows.size()&&parsed.invalid==page.invalid,"Resolution-independent rows: "+width);
            for(int i=0;i<page.rows.size();i++)check(parsed.rows.get(i).identity().equals(page.rows.get(i).identity()),"Resolution-independent identity: "+width);
        }
        if(args.length>2){CommentPage video=new CommentPage(NoteAndRecoveryTest.read(args[2]));check(video.rows.stream().filter(r->r.valid()).count()==3&&video.end.isEmpty(),"Actual 40.5 video retains three complete rows, clipped tails do not count");}
        System.out.println("PASS: Douyin 40.5 anonymous comments, local avatars, date/time variants, badges, clipping and false-bottom protection");
    }
}
