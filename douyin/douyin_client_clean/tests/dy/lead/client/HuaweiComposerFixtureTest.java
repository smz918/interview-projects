package dy.lead.client;

import java.util.List;

/** Regression for the Huawei Android 10 note composer captured on 2026-09-28. */
public final class HuaweiComposerFixtureTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        List<CommentPage.Node> before=NoteAndRecoveryTest.read(args[0]);
        List<CommentPage.Node> composer=NoteAndRecoveryTest.read(args[1]);
        String source=CommentPanelRecovery.identity(before);
        CommentPage page=new CommentPage(composer);
        check(!source.isEmpty(),"Original Huawei note identity captured");
        check(NoteCommentRecovery.sameNote(page.nodes,source),"Composer remains bound to original note");
        check(NoteCommentRecovery.composer(page),"Huawei editor overlay recognized");
        boolean backdrop=false;
        for(CommentPage.Node n:page.nodes)if(n.visible&&n.clickable&&NoteCommentRecovery.backdrop(n))backdrop=true;
        check(backdrop,"Huawei full-screen editor back layer recognized");
        System.out.println("PASS: Huawei SEA-AL10 composer overlay, note identity and full-screen back layer");
    }
}
