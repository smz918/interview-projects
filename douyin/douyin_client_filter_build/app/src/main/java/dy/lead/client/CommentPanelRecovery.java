package dy.lead.client;

import java.util.List;

/** Reopen a lost sheet only on the exact work captured before scanning. */
final class CommentPanelRecovery {
    String source="";
    int attempts;
    boolean reopening;
    long nextAttempt;
    static String normalize(String text){return text.replaceAll("\\s+", "").replaceFirst("展开$", "");}
    static String identity(List<CommentPage.Node> nodes){
        String caption="",author="";
        for(CommentPage.Node n:nodes){
            if(!n.visible||n.bottom<=n.top||n.right<=n.left)continue;
            if(n.id("desc")||n.id("tv_desc"))caption=normalize(n.text);
            if(n.id("tv_nickname")||n.id("nickname")||(n.id("title")&&n.text.startsWith("@")))author=normalize(n.text).replaceFirst("^@", "");
        }
        return caption.isEmpty()||author.isEmpty()?"":author+"\n"+caption;
    }
    void reset(){source="";attempts=0;reopening=false;nextAttempt=0;}
    void capture(List<CommentPage.Node> nodes){if(source.isEmpty())source=identity(nodes);}
    boolean matches(List<CommentPage.Node> nodes){return !source.isEmpty()&&source.equals(identity(nodes));}
    boolean due(long now){return attempts<3&&now>=nextAttempt;}
    void requested(long now){attempts++;reopening=true;nextAttempt=now+4000;}
    boolean restored(){boolean was=reopening;reopening=false;return was;}
}
