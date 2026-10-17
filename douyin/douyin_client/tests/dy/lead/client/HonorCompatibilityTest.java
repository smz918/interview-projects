package dy.lead.client;

import java.io.File;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/** Customer 20260924 fixtures, plus identity/selection guards. Offline only. */
public final class HonorCompatibilityTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static FilterPage nativePage(String path)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        NodeList entries=f.newDocumentBuilder().parse(new File(path)).getElementsByTagName("node");
        List<FilterPage.Item> items=new ArrayList<>();
        for(int i=0;i<entries.getLength();i++){
            Element e=(Element)entries.item(i);if(!e.getAttribute("visible").equals("true"))continue;
            String text=e.getAttribute("text"),desc=e.getAttribute("desc"),parentDesc="";
            if(e.getParentNode() instanceof Element)parentDesc=((Element)e.getParentNode()).getAttribute("desc");
            if(text.isEmpty()){String label=FilterPage.descriptionLabel(desc);text=label.isEmpty()?desc:label;}
            if(text.isEmpty())continue;
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("-?\\d+").matcher(e.getAttribute("bounds"));
            int[] b=new int[4];for(int j=0;j<4;j++){check(m.find(),"Bounds");b[j]=Integer.parseInt(m.group());}
            if(b[2]<=b[0]||b[3]<=b[1])continue;
            items.add(new FilterPage.Item(text,b[0],b[1],b[2],b[3],FilterPage.nativeSelection(text,desc,parentDesc,false)));
        }
        return new FilterPage(items,false);
    }
    static List<CommentPage.Node> scale(List<CommentPage.Node> nodes,double scale,boolean rename){
        List<CommentPage.Node> out=new ArrayList<>();
        for(CommentPage.Node n:nodes){
            String id=rename&&(n.id("eyo")||n.id("ufz")||n.id("fn5"))?"":n.id;
            out.add(new CommentPage.Node(id,n.text,n.desc,n.type,n.parent,(int)(n.left*scale),(int)(n.top*scale),(int)(n.right*scale),(int)(n.bottom*scale),n.visible,n.scrollable,n.clickable));
        }
        return out;
    }
    public static void main(String[] args)throws Exception{
        String dir=args[0]+File.separator;
        FilterPage page=nativePage(dir+"1790149585210_16_FILTER_VERIFY_MISSING_window2.xml");
        check(page.isPanel(),"Native failed-screen panel identified");
        for(int group=0;group<6;group++){
            FilterPage.Item option=page.option(group,group==0?"最新发布":"不限");
            check(option!=null&&option.selected,"Native selected group "+group);
        }
        check(!page.option(0,"综合排序").selected,"Unselected must not become selected");
        check(FilterPage.descriptionLabel("未选中，最新发布，按钮").equals("最新发布"),"Label extraction");
        check(!FilterPage.nativeSelection("最新发布","未选中，最新发布，按钮","",true),"Explicit unselected overrides stale flag");
        check(!FilterPage.nativeSelection("最新发布","","已选中，最多点赞，按钮",false),"Different parent option is not evidence");
        check(!FilterPage.nativeSelection("最新发布","已选中，最新发布，按钮","未选中，最新发布，按钮",false),"Conflicting descriptions fail closed");
        List<CommentPage.Node> before=NoteAndRecoveryTest.read(dir+"1790148819969_83_VIDEO_window0.xml");
        List<CommentPage.Node> after=NoteAndRecoveryTest.read(dir+"1790148819969_88_COMMENT_PANEL_NOT_CONFIRMED_TIMEOUT_window0.xml");
        String identity=CommentPanelRecovery.identity(before);check(!identity.isEmpty(),"Captured original note");
        check(CommentPanelRecovery.identity(after).isEmpty(),"Fixture reproduces hidden caption");
        for(double factor:new double[]{.67,1,1.5})for(boolean rename:new boolean[]{false,true}){
            int width=(int)(1200*factor),height=(int)(2408*factor);
            CommentPage closed=new CommentPage(scale(before,factor,rename)),composer=new CommentPage(scale(after,factor,rename));
            check(NoteCommentRecovery.sameNote(closed.nodes,identity),"Original note recognized before clicking");
            check(NoteCommentRecovery.sameNote(composer.nodes,identity),"Original note recognized below overlay");
            check(NoteCommentRecovery.composer(composer),"Comment editor overlay detected");
            check(!NoteCommentRecovery.composer(closed),"Normal note is not editor overlay");
            check(NoteCommentRecovery.hiddenInline(closed,width,height),"Offscreen comments trigger safe reveal");
            int[] drag=NoteCommentRecovery.revealPath(closed,width,height);check(drag!=null,"Reveal gesture available");
            check(drag[1]>1960*factor&&drag[1]<2385*factor&&drag[2]>0,"Gesture stays on caption above fixed footer");
            check(closed.emptyReason(width,height).isEmpty(),"Offscreen comments are not zero comments");
            check(!NoteCommentRecovery.sameNote(composer.nodes,"different\nwork"),"Other work cannot be dismissed");
            check(!NoteCommentRecovery.sameNote(composer.nodes,""),"Uncaptured work cannot be dismissed");
            List<CommentPage.Node> noSend=new ArrayList<>();
            for(CommentPage.Node n:composer.nodes)if(!n.text.equals("发送"))noSend.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,-1,n.left,n.top,n.right,n.bottom,n.visible,n.scrollable,n.clickable));
            check(!NoteCommentRecovery.composer(new CommentPage(noSend)),"Editor without send evidence is not auto-dismissed");
        }
        System.out.println("PASS: customer native six-group selection, negative guards, actual hidden note/editor and ID changes at three screen scales");
    }
}
