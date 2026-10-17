package dy.lead.client;

/** Rank visible comment action buttons; clipped inline headings are not buttons. */
final class CommentEntry {
    static boolean canUseFooterToExpand(boolean footerAvailable,boolean listAvailable,boolean readableRowsAvailable){
        // A fixed video footer can also open the comment editor. Use it only
        // before the list exposes readable rows; otherwise expand from header.
        return footerAvailable&&(!listAvailable||!readableRowsAvailable);
    }
    static boolean action(String desc){return desc.matches("^评论(?:评论|[\\s0-9.,万亿wW]*)(?:[，,]按钮|按钮)$");}
    static int score(String id,String text,String desc,boolean visible,int l,int t,int r,int b,int width,int height){
        int w=Math.min(width,r)-Math.max(0,l),h=Math.min(height,b)-Math.max(0,t);
        if(!visible||w<width*.025f||h<height*.012f||t<height*.15f||t>=height||l>=width)return -1;
        if(id.endsWith("/qn9"))return 100;
        if(action(desc))return 80;
        if(desc.matches("^评论[\\s0-9.,万亿wW]*$"))return 50;
        return -1;
    }
}
