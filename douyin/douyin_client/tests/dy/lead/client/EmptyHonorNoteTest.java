package dy.lead.client;

import java.nio.file.*;
import java.util.*;

/** Replay the 20260924 101515 empty-note failure and reject ambiguous pages. */
public final class EmptyHonorNoteTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static CommentPage.Node copy(CommentPage.Node n,String text,String desc,String type,boolean visible,double s){
        return new CommentPage.Node(n.id,text,desc,type,n.parent,(int)(n.left*s),(int)(n.top*s),(int)(n.right*s),(int)(n.bottom*s),visible,n.scrollable,n.clickable);
    }
    static List<CommentPage.Node> variant(List<CommentPage.Node> original,String mode,double scale){
        List<CommentPage.Node> out=new ArrayList<>();
        for(CommentPage.Node n:original){
            String text=n.text,desc=n.desc,type=n.type;boolean visible=n.visible;
            if(mode.equals("no_prompt")&&text.equals("发条评论，说说你的感受"))text="留下你的友善评论吧";
            if(mode.equals("no_marker")&&text.equals("平等表达，友善交流"))text="";
            if(mode.equals("hidden_marker")&&text.equals("平等表达，友善交流"))visible=false;
            if(mode.equals("loading")&&text.equals("平等表达，友善交流"))type="android.widget.ProgressBar";
            if(mode.equals("network")&&text.equals("平等表达，友善交流"))text="网络异常，点击重试";
            if(mode.equals("positive_count")&&desc.equals("评论评论，按钮"))desc="评论3，按钮";
            if(mode.equals("no_author")&&n.id("tv_nickname"))text="";
            if(mode.equals("header_count")&&text.equals("评论"))text="评论3";
            out.add(copy(n,text,desc,type,visible,scale));
        }
        return out;
    }
    public static void main(String[] args)throws Exception{
        Path dir=Paths.get(args[0]);
        List<CommentPage.Node> original=NoteAndRecoveryTest.read(dir.resolve("1790215751574_32_COMMENT_ROWS_UNRECOGNIZED_TIMEOUT_window0.xml").toString());
        for(double scale:new double[]{.67,1,1.5}){
            int width=(int)(1200*scale),height=(int)(2408*scale);
            CommentPage empty=new CommentPage(variant(original,"",scale));
            check(empty.rows.isEmpty()&&empty.list>=0,"Fixture has visible blank list");
            check(empty.emptyReason(width,height).equals(CommentPage.EMPTY_NOTE_PLACEHOLDER),"Actual empty layout recognized at scale "+scale);
            for(String mode:new String[]{"no_prompt","no_marker","hidden_marker","loading","network","positive_count","no_author","header_count"}){
                check(new CommentPage(variant(original,mode,scale)).emptyReason(width,height).isEmpty(),"Reject "+mode+" scale "+scale);
            }
        }
        for(String kind:new String[]{"android.widget.ImageView","android.view.View","android.widget.TextView"}){
            List<CommentPage.Node> uncertain=new ArrayList<>(original);CommentPage p=new CommentPage(uncertain);
            uncertain.add(new CommentPage.Node("unknown","","",kind,p.list,0,1300,120,1400,true,false));
            check(new CommentPage(uncertain).emptyReason(1200,2408).isEmpty(),"Unknown row/image must prevent empty classification");
        }
        List<CommentPage.Node> prose=new ArrayList<>(original);CommentPage p=new CommentPage(prose);
        prose.add(new CommentPage.Node("/content","平等表达，友善交流","","android.widget.TextView",p.list,0,1400,1100,1500,true,false,true));
        check(new CommentPage(prose).emptyReason(1200,2408).isEmpty(),"User comment text cannot act as empty placeholder");
        int emptyFrames=0,nonemptyFrames=0;
        try(java.util.stream.Stream<Path> files=Files.list(dir)){
            for(Path file:(Iterable<Path>)files.filter(f->f.toString().endsWith(".xml"))::iterator){
                CommentPage frame=new CommentPage(NoteAndRecoveryTest.read(file.toString()));
                String reason=frame.emptyReason(1200,2408);
                if(!reason.isEmpty()){emptyFrames++;check(file.getFileName().toString().matches("1790215751574_(30|31|32)_.*"),"Unexpected empty classification: "+file);}
                if(!frame.rows.isEmpty()){nonemptyFrames++;check(reason.isEmpty(),"Real nonempty frame cannot be skipped: "+file);}
            }
        }
        check(emptyFrames==3&&nonemptyFrames>20,"Replay must cover actual failure frames and many nonempty frames");
        System.out.println("PASS: 3 scales, 8 negative variants per scale, unknown/image rows, prose guard; actual empty frames="+emptyFrames+", nonempty frames="+nonemptyFrames);
    }
}
