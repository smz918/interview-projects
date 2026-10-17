package dy.lead.client;

import java.util.*;

/** Semantic comment sheet parser, shared by the device worker and XML fixture tests. */
final class CommentPage {
    static final class Node {
        final String id,text,desc,type; final int parent,left,top,right,bottom; final boolean visible,scrollable,clickable;
        Node(String id,String text,String desc,String type,int parent,int l,int t,int r,int b,boolean visible,boolean scrollable){
            this(id,text,desc,type,parent,l,t,r,b,visible,scrollable,false);
        }
        Node(String id,String text,String desc,String type,int parent,int l,int t,int r,int b,boolean visible,boolean scrollable,boolean clickable){
            this.id=id;this.text=text;this.desc=desc;this.type=type;this.parent=parent;left=l;top=t;right=r;bottom=b;this.visible=visible;this.scrollable=scrollable;this.clickable=clickable;
        }
        boolean id(String suffix){return id.endsWith("/"+suffix);}
    }
    static final class Row {
        int source,avatar=-1;String name="",body="",posted="",place="",selector="";boolean partial;
        boolean valid(){return !partial&&!name.isEmpty()&&!body.isEmpty();}
        String identity(){return name+"\n"+body+"\n"+posted;}
    }
    final List<Node> nodes;final List<Row> rows=new ArrayList<>();
    int list=-1,header=-1,editor=-1,expandFolded=-1;String end="";int candidates,invalid;
    CommentPage(List<Node> nodes){this.nodes=nodes;parse();}
    boolean inside(int child,int ancestor){for(int i=child;i>=0;i=nodes.get(i).parent)if(i==ancestor)return true;return false;}
    static boolean header(String text){
        String count="[0-9]+(?:[.,][0-9]+)*[万亿wW]?\\+?";
        return text.replaceAll("[\\s\\u00a0\\u3000]+","").matches("(?:评论(?:"+count+")?|"+count+"条?评论)");
    }
    static boolean avatar(Node n){return n.id("avatar")||n.desc.endsWith("的头像");}
    static final java.util.regex.Pattern POSTED=java.util.regex.Pattern.compile("(?:刚刚|今天|昨天|前天|\\d+(?:秒|分钟|小时|天|周|个月|年)前|(?:\\d{4}[-/.年])?\\d{1,2}[-/.月]\\d{1,2}日?)(?:[\\s\\u00a0\\u3000]+(?:[01]?\\d|2[0-3]):[0-5]\\d)?");
    static boolean posted(String text){return POSTED.matcher(text.trim()).matches();}
    // Douyin 40.5 renders comments as ID-less Views. Bind only a complete local
    // row: clickable name, square avatar, body block, date and reply siblings.
    Row anonymousRow(int source){
        Node root=nodes.get(source);if(!root.id.isEmpty())return null;
        int name=-1,date=-1,reply=-1,avatar=-1;
        for(int i=source+1;i<nodes.size();i++){
            Node n=nodes.get(i);if(n.parent!=source)continue;
            if(n.text.trim().equals("回复")&&n.clickable)reply=i;
            else if(posted(n.text))date=i;
        }
        boolean missingMetadata=date<0||reply<0;
        boolean bottomClipped=list>=0&&root.bottom>=nodes.get(list).bottom-2;
        if(missingMetadata&&!bottomClipped)return null;
        int bodyBottom=date<0?root.bottom:nodes.get(date).top;
        for(int i=source+1;i<nodes.size();i++){
            Node n=nodes.get(i);if(n.parent!=source)continue;
            if(n.visible&&n.clickable&&n.type.endsWith("TextView")&&!n.text.trim().isEmpty()&&n.bottom<=bodyBottom&&i!=reply){if(name<0||n.top<nodes.get(name).top)name=i;}
        }
        if(name<0){
            // A large picture can leave only the tail of a row in the viewport.
            // This is evidence for scrolling, never a customer or bottom marker.
            if(!missingMetadata&&list>=0&&root.top<=nodes.get(list).top+2){Row tail=new Row();tail.source=source;tail.partial=true;tail.posted=nodes.get(date).text.trim();tail.selector="semantic:idless-clipped-top";return tail;}
            return null;
        }
        Node label=nodes.get(name);
        for(int i=source+1;i<nodes.size();i++){
            Node n=nodes.get(i);int w=n.right-n.left,h=n.bottom-n.top;
            if(n.parent==source&&n.visible&&n.clickable&&n.text.isEmpty()&&n.desc.isEmpty()&&w>0&&h>0&&w>=h*.7f&&w<=h*1.4f&&n.right<=label.left&&n.top<=label.bottom&&n.bottom>=label.top){avatar=i;break;}
        }
        if(avatar<0)return null;
        Row row=new Row();row.source=source;row.name=label.text.trim();row.avatar=avatar;row.posted=date<0?"":nodes.get(date).text.trim();row.partial=missingMetadata;row.selector=missingMetadata?"semantic:idless-clipped-bottom":"semantic:idless-avatar+name+body+date+reply";
        StringBuilder body=new StringBuilder();
        for(int i=source+1;i<nodes.size();i++){
            Node n=nodes.get(i);if(!inside(i,source))continue;
            if(n.parent==source&&n.text.trim().startsWith("·"))row.place=n.text.replace("·","").trim();
            // The body is a nested block below the nickname and above metadata;
            // never use the nickname, reply controls or adjacent rows as body.
            if(n.parent!=source&&n.visible&&!n.text.trim().isEmpty()&&n.top>=label.bottom&&n.bottom<=bodyBottom&&n.left>=label.left){
                boolean duplicate=false;for(int p=n.parent;p>=0&&p!=source;p=nodes.get(p).parent)if(!nodes.get(p).text.trim().isEmpty()){duplicate=true;break;}
                if(!duplicate){if(body.length()>0)body.append(' ');body.append(n.text.trim());}
            }
        }
        row.body=body.toString();return row;
    }
    static boolean emptyText(String text){return Arrays.asList("暂无评论","还没有评论","暂时还没有评论","暂无评论，快来抢沙发吧","暂无评论，快来抢沙发","还没有评论，快来抢沙发吧").contains(text.trim());}
    static final String EMPTY_NOTE_PLACEHOLDER="图文评论区仅有友善交流占位，且有首评输入提示";
    static boolean end(String text){return emptyText(text)||Arrays.asList("暂时没有更多了","没有更多了","没有更多评论了","已显示全部评论").contains(text.trim());}
    String emptyReason(int width,int height){
        if(header<0||!rows.isEmpty()||expandFolded>=0||needsExpansion(width,height))return "";
        for(Node n:nodes)if(n.visible&&(n.type.endsWith("ProgressBar")||n.text.trim().matches("(?:正在加载|加载中|加载失败|网络异常|点击重试).*")))return "";
        if(emptyText(end))return end;
        if(emptyNotePlaceholder(width,height))return EMPTY_NOTE_PLACEHOLDER;
        Node h=nodes.get(header);
        String title=h.text.replaceAll("[\\s\\u00a0\\u3000]+","");
        if(!title.matches("(?:评论0|0条?评论)")||editor<0)return "";
        return nodes.get(editor).top-h.bottom>=height*.18f?"展开的评论面板标题明确为0条评论":"";
    }
    boolean emptyNotePlaceholder(int width,int height){
        // This sentence alone is NOT an empty marker: it can also occur in a
        // comment or a normal footer. Match the complete captured note layout.
        if(list<0||header<0||editor<0||!rows.isEmpty()||expandFolded>=0)return false;
        if(!nodes.get(header).text.trim().equals("评论")||CommentPanelRecovery.identity(nodes).isEmpty())return false;
        Node container=nodes.get(list);int[] bounds=viewport(width,height);
        if(!(container.type.contains("RecyclerView")||container.type.endsWith("ListView"))||bounds==null||bounds[2]<=bounds[0]||bounds[3]-bounds[1]<height*.18f)return false;
        boolean prompt=false,footer=false;int placeholders=0;
        for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i);if(!n.visible||n.bottom<=n.top||n.right<=n.left)continue;
            if(n.type.endsWith("EditText")&&n.text.trim().equals("发条评论，说说你的感受")&&n.top>=nodes.get(header).bottom&&n.bottom<=container.top)prompt=true;
            if(n.desc.equals("评论评论，按钮")&&n.clickable&&n.top>=nodes.get(editor).top-height*.05f)footer=true;
            if(i==list||!inside(i,list))continue;
            // Reject unknown/image/partial comment rows, including unparsed
            // rows: the empty list contains only plain noninteractive wrappers
            // and its single placeholder TextView.
            if(n.clickable||n.scrollable)return false;
            if(n.type.endsWith("TextView")&&n.text.trim().equals("平等表达，友善交流")&&n.desc.isEmpty()){
                if(n.left<bounds[0]||n.right>bounds[2]||n.top<bounds[1]||n.bottom>bounds[3])return false;
                placeholders++;
            }else if(!n.text.isEmpty()||!n.desc.isEmpty()||!(n.type.endsWith("FrameLayout")||n.type.endsWith("LinearLayout")||n.type.endsWith("RelativeLayout")))return false;
        }
        return prompt&&footer&&placeholders==1;
    }
    boolean aboveEditor(Node n){return editor<0||Math.min(n.bottom,nodes.get(editor).top)-n.top>=Math.min(24,Math.max(1,(n.bottom-n.top)/2));}
    int[] viewport(int width,int height){
        if(list<0)return null;
        Node n=nodes.get(list);int bottom=Math.min(height,n.bottom);
        if(editor>=0)bottom=Math.min(bottom,nodes.get(editor).top);
        return new int[]{Math.max(0,n.left),Math.max(0,n.top),Math.min(width,n.right),bottom};
    }
    boolean needsExpansion(int width,int height){
        if(header<0||!end.isEmpty())return false;
        if(list<0){
            // A note can expose only its tab at the screen bottom; its list is
            // wholly offscreen, including on notes with zero comments.
            Node h=nodes.get(header);
            return editor>=0&&h.top>height*.6f&&nodes.get(editor).top-h.bottom<height*.18f;
        }
        int[] b=viewport(width,height);
        return b[3]-b[1]<Math.max(80,Math.round(height*.18f));
    }
    private void parse(){
        for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i);if(!n.visible)continue;
            if(header(n.text)&&(header<0||n.top<nodes.get(header).top))header=i;
            // Notes have an inline composer above the list AND a fixed bottom composer.
            // The lower composer bounds the list; the upper one must not hide its footer.
            if(n.type.endsWith("EditText")&&(n.text.contains("评论")||n.text.contains("想法")||n.text.contains("展开说说")||n.text.startsWith("说点什么")||n.id("eyi")||n.id("eyj")||n.id("fn2")||n.id("fn3"))&&(editor<0||n.top>nodes.get(editor).top))editor=i;
        }
        // Anchor to the sheet heading and footer, not to a video feed scroll view.
        int bestListRank=-1;
        if(header>=0)for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i),h=nodes.get(header);
            if(!n.visible||n.top<h.bottom||n.bottom-n.top<60||(!n.type.contains("RecyclerView")&&!n.type.endsWith("ListView")&&!n.scrollable))continue;
            // A collapsed note can put the real list UNDER its fixed composer.
            // Keep it as the source so the worker can expand it before reading.
            boolean hasRows=false;
            for(int j=i+1;j<nodes.size();j++)if(inside(j,i)&&(nodes.get(j).id("flq")||nodes.get(j).id("flr")||nodes.get(j).id("content")||(nodes.get(j).text.equals("回复")&&nodes.get(j).parent>=0&&anonymousRow(nodes.get(j).parent)!=null))){hasRows=true;break;}
            boolean verticalList=n.type.contains("RecyclerView")||n.type.endsWith("ListView");
            int rank=(hasRows?200:0)+(verticalList?100:n.type.contains("ViewPager")?0:80);
            if(rank>bestListRank||(rank==bestListRank&&(list<0||n.bottom-n.top>nodes.get(list).bottom-nodes.get(list).top))){list=i;bestListRank=rank;}
        }
        if(header<0)return;
        if(list>=0){
            LinkedHashMap<Integer,String> sources=new LinkedHashMap<>();
            for(int i=0;i<nodes.size();i++){
                Node n=nodes.get(i);if(!n.visible||!inside(i,list))continue;
                if(n.id("flq")||n.id("flr"))sources.put(i,"known-row:"+n.id.substring(n.id.lastIndexOf('/')+1));
            }
            // New obfuscated row IDs: smallest subtree containing exactly one
            // nickname, a body and an avatar/reply control inside the comment list.
            for(int i=0;i<nodes.size();i++)if(nodes.get(i).visible&&nodes.get(i).id("title")&&inside(i,list)){
                int p=nodes.get(i).parent;
                for(int depth=0;p>=0&&p!=list&&depth<7;depth++,p=nodes.get(p).parent){
                    int titles=0,bodies=0,avatars=0;boolean reply=false;
                    for(int j=p;j<nodes.size();j++)if(inside(j,p)){
                        Node n=nodes.get(j);if(n.id("title"))titles++;if(n.id("content"))bodies++;if(avatar(n))avatars++;if(n.text.equals("回复"))reply=true;
                    }
                    if(titles==1&&bodies==1&&(avatars==1||reply)){
                        boolean covered=false;for(int known:sources.keySet())if(inside(p,known)||inside(known,p)){covered=true;break;}
                        if(!covered)sources.put(p,"semantic:title+content+avatar/reply");break;
                    }
                    if(titles>1)break;
                }
            }
            for(Map.Entry<Integer,String> source:sources.entrySet()){
                Row row=new Row();row.source=source.getKey();row.selector=source.getValue();
                for(int j=row.source;j<nodes.size();j++)if(inside(j,row.source)){
                    Node n=nodes.get(j);
                    if(n.id("title"))row.name=n.text.trim();if(n.visible&&n.id("content"))row.body=n.text.trim();if(n.visible&&avatar(n))row.avatar=j;
                    if(n.id("e4t")||n.id("e4u"))row.posted=n.text.trim();
                    if(n.id("e0z")||n.id("e00"))row.place=n.text.replace("·","").trim();
                }
                // Metadata IDs also change between Douyin builds.
                for(int j=row.source;j<nodes.size();j++)if(inside(j,row.source)){
                    Node n=nodes.get(j);String t=n.text.trim();
                    if(row.posted.isEmpty()&&posted(t))row.posted=t;
                    if(row.place.isEmpty()&&t.startsWith("·"))row.place=t.substring(1).trim();
                }
                candidates++;if(!row.valid())invalid++;rows.add(row);
            }
            Set<Integer> anonymousSources=new HashSet<>();
            for(int i=0;i<nodes.size();i++){
                Node n=nodes.get(i);if(!(n.text.equals("回复")||(n.clickable&&n.type.endsWith("TextView")&&!n.text.trim().isEmpty()))||n.parent<0||!inside(i,list)||!anonymousSources.add(n.parent))continue;
                boolean covered=false;for(Row old:rows)if(inside(i,old.source)||inside(old.source,n.parent)){covered=true;break;}
                if(covered)continue;Row row=anonymousRow(n.parent);if(row==null||!nodes.get(row.source).visible)continue;
                candidates++;if(!row.valid())invalid++;rows.add(row);
            }
            rows.sort(Comparator.comparingInt(r->nodes.get(r.source).top));
        }
        for(int i=0;i<nodes.size();i++){
            Node n=nodes.get(i);if(!n.visible||n.top<nodes.get(header).bottom||!aboveEditor(n)||!end(n.text))continue;
            boolean commentBody=false;for(Row row:rows)if(inside(i,row.source)){commentBody=true;break;}
            if(!commentBody){end=n.text;break;}
        }
        // Douyin can stop scrolling at a folded-comments gate instead of a bottom marker.
        // Only accept its explicit label and sibling button, never a user's "展开" text.
        for(int i=0;i<nodes.size();i++){
            Node label=nodes.get(i);
            if(!label.visible||!label.text.trim().equals("已折叠部分评论")||label.parent<0||label.top<nodes.get(header).bottom||!aboveEditor(label))continue;
            boolean inRow=false;for(Row row:rows)if(inside(i,row.source)){inRow=true;break;}if(inRow)continue;
            for(int j=0;j<nodes.size();j++){
                Node button=nodes.get(j);
                if(button.visible&&button.parent==label.parent&&button.text.trim().equals("展开")&&button.right>button.left&&button.bottom>button.top){expandFolded=j;return;}
            }
        }
    }
    int partialCount(){int count=0;for(Row row:rows)if(row.partial)count++;return count;}
    boolean needsShortSwipe(){for(Row row:rows)if(row.partial&&row.avatar>=0)return true;return false;}
    String fingerprint(){StringBuilder s=new StringBuilder();for(Row r:rows){Node n=nodes.get(r.source);s.append(r.identity()).append('@').append(n.top).append(':').append(n.bottom).append('|');}return s.toString();}
}
