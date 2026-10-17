package dy.lead.client;

/** Regression for TYH612M where the fixed footer opens the comment editor. */
public final class CommentFooterPolicyTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        check(!CommentEntry.canUseFooterToExpand(true,true,true),"Readable rows: expand from the comment heading, not the fixed footer");
        check(CommentEntry.canUseFooterToExpand(true,true,false),"Unparsed rows: retain the footer fallback");
        check(CommentEntry.canUseFooterToExpand(true,false,false),"No list yet: retain the footer fallback");
        check(!CommentEntry.canUseFooterToExpand(false,false,false),"Missing footer: do not click");
        System.out.println("PASS: fixed comment footer policy avoids the TYH612M editor while preserving unopened-list fallback");
    }
}
