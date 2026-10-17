package dy.lead.client;

import java.io.File;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Actual device XML regression, no Android runtime or live social actions. */
public class CommentPageTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void walk(Element element,int parent,List<CommentPage.Node> nodes){
        Matcher m=Pattern.compile("-?\\d+").matcher(element.getAttribute("bounds"));int[] b=new int[4];for(int i=0;i<4&&m.find();i++)b[i]=Integer.parseInt(m.group());
        int index=nodes.size();nodes.add(new CommentPage.Node(element.getAttribute("id"),element.getAttribute("text"),element.getAttribute("desc"),element.getAttribute("class"),parent,b[0],b[1],b[2],b[3],"true".equals(element.getAttribute("visible")),"true".equals(element.getAttribute("scrollable")),"true".equals(element.getAttribute("clickable"))));
        NodeList children=element.getChildNodes();for(int i=0;i<children.getLength();i++)if(children.item(i) instanceof Element)walk((Element)children.item(i),index,nodes);
    }
    static List<CommentPage.Node> modify(List<CommentPage.Node> source,String rowId,boolean hideBodies,boolean hideHeader,boolean misleadingEnd){
        List<CommentPage.Node> out=new ArrayList<>();for(CommentPage.Node n:source){String id=n.id.replace("/flr","/"+rowId);String text=misleadingEnd&&n.id("content")?"没有更多了":n.text;boolean visible=n.visible&&!(hideBodies&&n.id("content"))&&!(hideHeader&&CommentPage.header(n.text));out.add(new CommentPage.Node(id,text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,visible,n.scrollable));}return out;
    }
    public static void main(String[] args)throws Exception{
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        List<CommentPage.Node> nodes=new ArrayList<>();walk(factory.newDocumentBuilder().parse(new File(args[0])).getDocumentElement(),-1,nodes);
        CommentPage page=new CommentPage(nodes);
        check(page.header>=0&&page.list>=0,"Actual Huawei comment sheet must be recognized");
        check(page.rows.size()==4&&page.invalid==1,"Three full rows plus one clipped row expected");
        check(page.rows.get(0).posted.equals("07-08")&&page.rows.get(0).place.equals("湖北"),"Changed metadata IDs must resolve");
        check(page.rows.get(0).avatar>=0,"Avatar must be bound to its own row");
        check(page.end.isEmpty(),"Non-bottom sheet must not end");
        List<CommentPage.Node> clippedMetadata=new ArrayList<>();for(CommentPage.Node n:nodes)clippedMetadata.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,n.visible&&!n.id("e4u")&&!n.id("e00"),n.scrollable));
        check(new CommentPage(clippedMetadata).rows.get(0).identity().equals(page.rows.get(0).identity()),"Clipped date must not duplicate a comment after scrolling");
        for(String rowId:new String[]{"flq","flr","new_obfuscated_row"}){
            CommentPage variant=new CommentPage(modify(nodes,rowId,false,false,false));check(variant.rows.size()==4&&variant.invalid==1,"Row ID compatibility: "+rowId);
            check(variant.rows.get(0).identity().equals(page.rows.get(0).identity()),"Stable identity and profile matching across IDs");
        }
        CommentPage broken=new CommentPage(modify(nodes,"flr",true,false,false));check(broken.invalid==4&&broken.end.isEmpty(),"Missing text is a parsing issue, never end-of-list");
        check(new CommentPage(modify(nodes,"flr",false,true,false)).rows.isEmpty(),"Without a comment sheet heading do not scan unrelated cards");
        check(new CommentPage(modify(nodes,"flr",false,false,true)).end.isEmpty(),"A user comment saying no more is not a bottom marker");
        List<CommentPage.Node> bottom=new ArrayList<>(nodes);bottom.add(new CommentPage.Node("footer","暂时没有更多了","","android.widget.TextView",page.list,0,2110,1080,2170,true,false));
        check(new CommentPage(bottom).end.equals("暂时没有更多了"),"Explicit footer must be recognized");
        List<CommentPage.Node> empty=new ArrayList<>(modify(nodes,"flr",true,true,false));
        empty.add(new CommentPage.Node("header","评论 0","","android.widget.TextView",0,0,900,1080,1000,true,false));
        empty.add(new CommentPage.Node("empty","暂无评论","","android.widget.TextView",0,0,1400,1080,1500,true,false));
        check(new CommentPage(empty).end.equals("暂无评论"),"Empty-state marker must be recognized");
        if(args.length>1){
            List<CommentPage.Node> folded=new ArrayList<>();walk(factory.newDocumentBuilder().parse(new File(args[1])).getDocumentElement(),-1,folded);
            CommentPage gate=new CommentPage(folded);check(gate.expandFolded>=0&&gate.end.isEmpty(),"Real folded-comments footer must expand, never end the video");
            check(folded.get(gate.expandFolded).text.equals("展开"),"Select only the matching footer expand button");
            List<CommentPage.Node> fake=new ArrayList<>();for(CommentPage.Node n:folded){boolean label=n.text.equals("已折叠部分评论");fake.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,label?gate.rows.get(0).source:n.parent,n.left,n.top,n.right,n.bottom,n.visible,n.scrollable));}
            check(new CommentPage(fake).expandFolded<0,"A user comment containing folded-footer text must not trigger a click");
        }
        System.out.println("PASS: Huawei real XML, old/new/unknown row IDs, date/location, avatar binding, clipped rows, sheet gate, true/false bottom markers");
    }
}
