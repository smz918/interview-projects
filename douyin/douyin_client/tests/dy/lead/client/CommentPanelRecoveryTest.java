package dy.lead.client;
import java.util.*;
public class CommentPanelRecoveryTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> video=NoteAndRecoveryTest.read(args[0]),note=NoteAndRecoveryTest.read(args[1]);
        CommentPanelRecovery recovery=new CommentPanelRecovery();recovery.capture(video);
        check(!recovery.source.isEmpty()&&recovery.matches(video),"Identify real video after its sheet closed");
        check(!recovery.matches(note),"Never reopen another work");
        List<CommentPage.Node> otherAuthor=new ArrayList<>();
        for(CommentPage.Node n:video)otherAuthor.add(new CommentPage.Node(n.id,n.text.startsWith("@")?"@别的作者":n.text,n.desc,n.type,n.parent,n.left,n.top,n.right,n.bottom,n.visible,n.scrollable));
        check(!recovery.matches(otherAuthor),"Same caption with another author must not match");
        recovery.capture(note);check(recovery.matches(video),"Missing panel cannot replace source identity");
        for(int i=0;i<3;i++){check(recovery.due(i*4000),"Retry available");recovery.requested(i*4000);check(!recovery.due(i*4000+3999),"Allow UI time to settle");}
        check(!recovery.due(100000),"Three reopen requests maximum per work");
        check(recovery.restored()&&!recovery.restored(),"Report restoration once");
        recovery.reset();recovery.capture(note);check(recovery.matches(note)&&recovery.attempts==0,"Next work captures actual note identity");
        recovery.reset();recovery.capture(Collections.emptyList());check(!recovery.matches(Collections.emptyList()),"Unknown pages cannot match");
        check(new CommentPage(NoteAndRecoveryTest.read(args[2])).header>=0,"Pre-failure AI snapshot was an open comment sheet");
        check(new CommentPage(video).header<0,"Failure snapshot is a closed comment sheet");
        System.out.println("PASS: real failure video, note identity, wrong author/work rejection, retry timing/budget, open/closed XML evidence");
    }
}
