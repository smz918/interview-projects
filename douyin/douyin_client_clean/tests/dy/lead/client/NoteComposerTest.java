package dy.lead.client;
import java.util.*;

public final class NoteComposerTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> before=NoteAndRecoveryTest.read(args[0]),after=NoteAndRecoveryTest.read(args[1]);
        String source=CommentPanelRecovery.identity(before);
        check(!source.isEmpty(),"Actual note identity captured before entry");
        check(CommentPanelRecovery.identity(after).isEmpty(),"Reproduce old failure: editor hides caption");
        check(NoteCommentRecovery.sameNote(after,source),"Overlay still belongs to captured note");
        check(NoteCommentRecovery.composer(new CommentPage(after)),"Actual failure is composer, not comment sheet");
        check(!NoteCommentRecovery.sameNote(after,"different\nwork"),"Different note cannot be dismissed or scrolled");
        check(!NoteCommentRecovery.sameNote(after,""),"Uncaptured note cannot be recovered");
        for(double scale:new double[]{.67,1,1.5}){
            int w=(int)(1080*scale),h=(int)(2141*scale);List<CommentPage.Node> scaled=new ArrayList<>();
            for(CommentPage.Node n:before)scaled.add(new CommentPage.Node(n.id,n.text,n.desc,n.type,n.parent,(int)(n.left*scale),(int)(n.top*scale),(int)(n.right*scale),(int)(n.bottom*scale),n.visible,n.scrollable,n.clickable));
            CommentPage p=new CommentPage(scaled);check(NoteCommentRecovery.hiddenInline(p,w,h),"Hidden inline region detected at each resolution");
            int[] drag=NoteCommentRecovery.revealPath(p,w,h);check(drag!=null,"Verified caption is draggable");
            check(drag[1]>1905*scale&&drag[1]<2076*scale&&drag[2]>0,"Gesture starts on caption, not image or fixed footer");
            check(p.emptyReason(w,h).isEmpty(),"No digit on footer never proves empty comments");
        }
        for(int i=2;i<args.length;i++){
            CommentPage p=new CommentPage(NoteAndRecoveryTest.read(args[i]));
            check(!NoteCommentRecovery.composer(p),"Existing comment sheet not dismissed: "+args[i]);
            check(!NoteCommentRecovery.hiddenInline(p,1080,2141),"Existing sheet not moved as note: "+args[i]);
        }
        System.out.println("PASS: actual note/editor failure, identity guards, safe drag at 3 resolutions, legacy comment sheets");
    }
}
