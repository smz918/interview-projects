package dy.lead.client;

import java.util.List;

/** Recovery restricted to the captured note, never a generic feed swipe. */
final class NoteCommentRecovery {
    int dismissals,reveals;
    void reset(){dismissals=0;reveals=0;}
    static boolean footer(CommentPage.Node n){
        return n.id("fn3")||n.id("fn2")||(n.type.endsWith("EditText")&&n.text.startsWith("说点什么"));
    }
    static boolean inline(CommentPage.Node n){
        return n.id("u5e")||n.id("comment_container");
    }
    static boolean backdrop(CommentPage.Node n){
        // A full-page dismiss layer, not the small navigation back button.
        return n.desc.matches("返回(?:[，,]按钮)?")&&(n.id("uir")||n.id("ufz")||
            (n.type.equals("android.view.View")&&n.clickable&&n.left<=0&&n.top<=0&&n.right>0&&n.bottom>n.right));
    }
    static boolean composer(CommentPage page){
        boolean editor=false,send=false,backdrop=false;
        for(CommentPage.Node n:page.nodes)if(n.visible){
            if(n.type.endsWith("EditText")&&(n.id("eyi")||n.id("eyo")||n.text.contains("评论")||n.text.contains("想法")))editor=true;
            if(n.text.equals("发送"))send=true;
            if(backdrop(n))backdrop=true;
        }
        return page.header<0&&editor&&send&&backdrop;
    }
    static boolean sameNote(List<CommentPage.Node> nodes,String source){
        if(source.isEmpty())return false;
        String author="",caption="";boolean footer=false,inline=false,editor=false,backdrop=false,send=false;
        for(CommentPage.Node n:nodes){
            if(n.id("tv_nickname")&&n.visible)author=CommentPanelRecovery.normalize(n.text).replaceFirst("^@", "");
            // The overlay marks the unchanged underlying caption invisible.
            if(n.id("tv_desc"))caption=CommentPanelRecovery.normalize(n.text);
            if(footer(n))footer=true;
            if(inline(n))inline=true;
            if(n.visible&&n.type.endsWith("EditText")&&(n.id("eyi")||n.id("eyo")||n.text.contains("评论")||n.text.contains("想法")))editor=true;
            if(n.visible&&n.text.equals("发送"))send=true;
            if(n.visible&&backdrop(n))backdrop=true;
        }
        boolean knownOverlay=footer&&inline;
        boolean editorOverlay=editor&&send&&backdrop;
        return (knownOverlay||editorOverlay)&&!author.isEmpty()&&!caption.isEmpty()&&source.equals(author+"\n"+caption);
    }
    static boolean hiddenInline(CommentPage page,int width,int height){
        boolean hidden=false;
        for(CommentPage.Node n:page.nodes)if(inline(n)&&(!n.visible||n.top>=height||n.bottom<=n.top))hidden=true;
        return hidden&&(page.header<0||page.needsExpansion(width,height));
    }
    static int[] revealPath(CommentPage page,int width,int height){
        int footer=height;
        for(CommentPage.Node n:page.nodes)if(n.visible&&footer(n))footer=Math.min(footer,n.top);
        for(CommentPage.Node n:page.nodes){
            // Drag verified note text/tab, below the carousel and above composer.
            if(!n.visible||!(n.id("tv_desc")||CommentPage.header(n.text)))continue;
            int top=Math.max(n.top,(int)(height*.4f)),bottom=Math.min(n.bottom,footer-12);
            if(bottom-top<20)continue;
            int start=(top+bottom)/2,end=Math.max((int)(height*.28f),start-(int)(height*.4f));
            int x=(Math.max(0,n.left)+Math.min(width,n.right))/2;
            if(start-end>=height*.12f&&x>0&&x<width)return new int[]{x,start,end};
        }
        return null;
    }
}
