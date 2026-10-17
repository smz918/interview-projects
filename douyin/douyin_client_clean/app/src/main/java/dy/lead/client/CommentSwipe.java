package dy.lead.client;

/** A moderate drag inside the visible comment list, independent of screen resolution. */
final class CommentSwipe {
    static int[] path(int left,int top,int right,int bottom){
        return path(left,top,right,bottom,false);
    }
    static int[] path(int left,int top,int right,int bottom,boolean partial){
        int width=right-left,height=bottom-top;if(width<20||height<80)return null;
        // Leave 25% overlap for rows crossing screen edges, while keeping clear of header/footer.
        return new int[]{left+Math.round(width*.55f),top+Math.round(height*.88f),top+Math.round(height*(partial?.68f:.13f))};
    }
}
