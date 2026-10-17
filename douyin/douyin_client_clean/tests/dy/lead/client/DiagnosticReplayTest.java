package dy.lead.client;
import java.nio.file.*;
import java.util.*;

/** Offline XML replay only; does not claim a live device run. */
public class DiagnosticReplayTest {
    public static void main(String[] args)throws Exception{
        int sheets=0,partial=0,complete=0,hiddenNotes=0;
        try(java.util.stream.Stream<Path> files=Files.list(Paths.get(args[0]))){
            for(Path file:(Iterable<Path>)files.filter(p->p.toString().endsWith(".xml")&&!p.toString().endsWith("_ocr.xml"))::iterator){
                CommentPage page=new CommentPage(NoteAndRecoveryTest.read(file.toString()));
                if(NoteCommentRecovery.sameNote(page.nodes,CommentPanelRecovery.identity(page.nodes))&&NoteCommentRecovery.hiddenInline(page,1080,2141)&&NoteCommentRecovery.revealPath(page,1080,2141)!=null){hiddenNotes++;continue;}
                if(page.header<0)continue;sheets++;
                if(page.rows.isEmpty()&&page.end.isEmpty()&&page.expandFolded<0)throw new AssertionError("Unrecognized captured comment sheet: "+file.getFileName());
                if(page.partialCount()>0)partial++;
                complete+=page.rows.stream().filter(r->r.valid()).count();
            }
        }
        if(sheets==0)throw new AssertionError("No comment sheets tested");
        System.out.println("PASS: diagnostic replay sheets="+sheets+", recoverable hidden notes="+hiddenNotes+", partial frames="+partial+", complete row observations="+complete+" (not unique customers)");
    }
}
