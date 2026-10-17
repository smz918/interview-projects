package dy.lead.client;

import java.util.*;

/** Device-independent semantic filter layout; coordinates always come from this frame. */
final class FilterPage {
    private static final java.util.regex.Pattern STATE_DESCRIPTION=java.util.regex.Pattern.compile("^(已选中|未选中)[，,](.+?)(?:[，,]按钮)?$");
    static String descriptionLabel(String description){
        java.util.regex.Matcher m=STATE_DESCRIPTION.matcher(description==null?"":description.trim());
        return m.matches()?normalize(m.group(2)):"";
    }
    static int descriptionState(String description,String label){
        java.util.regex.Matcher m=STATE_DESCRIPTION.matcher(description==null?"":description.trim());
        return m.matches()&&normalize(m.group(2)).equals(normalize(label))?(m.group(1).equals("已选中")?1:0):-1;
    }
    static boolean nativeSelection(String label,String description,String parentDescription,boolean flags){
        int own=descriptionState(description,label),parent=descriptionState(parentDescription,label);
        if(own>=0&&parent>=0&&own!=parent)return false;
        return own>=0?own==1:parent>=0?parent==1:flags;
    }
    static final String[] GROUPS={"排序依据","发布时间","视频时长","搜索范围","内容形式","位置距离"};
    static boolean isDefaultUnfiltered(int group,String value){return group>0&&"不限".equals(normalize(value));}
    static final class Item {
        final String text; final int left,top,right,bottom; final boolean selected;
        Item(String text,int l,int t,int r,int b,boolean selected){this.text=normalize(text);left=l;top=t;right=r;bottom=b;this.selected=selected;}
        int x(){return (left+right)/2;} int y(){return (top+bottom)/2;}
    }
    final List<Item> items; final boolean visual;
    FilterPage(List<Item> items,boolean visual){this.items=items;this.visual=visual;}
    /**
     * OCR frequently attaches the separator used by Douyin's filter rows to
     * the first/last option (for example "|一周内").  Keep the semantic text
     * strict, but discard only harmless row punctuation at the edges.
     */
    static String normalize(String s){
        if(s==null)return "";
        String value=s.replaceAll("\\s+","").replace('—','-').replace('–','-').replace('內','内');
        value=value.replaceAll("^[|丨¦·•,，。:：;；]+","");
        value=value.replaceAll("[|丨¦·•,，。:：;；]+$","");
        return value;
    }
    static boolean fuzzy(String actual,String wanted){
        actual=normalize(actual);wanted=normalize(wanted);
        if(actual.equals(wanted))return true;
        // Very short labels are too ambiguous to guess (for example "不限").
        if(actual.length()<3||wanted.length()<3)return false;
        int limit=Math.max(actual.length(),wanted.length())>=10?2:1;
        if(Math.abs(actual.length()-wanted.length())>limit)return false;
        int[] previous=new int[wanted.length()+1],current=new int[wanted.length()+1];
        for(int j=0;j<=wanted.length();j++)previous[j]=j;
        for(int i=1;i<=actual.length();i++){
            current[0]=i;int rowMin=current[0];
            for(int j=1;j<=wanted.length();j++){
                current[j]=Math.min(Math.min(current[j-1]+1,previous[j]+1),previous[j-1]+(actual.charAt(i-1)==wanted.charAt(j-1)?0:1));
                rowMin=Math.min(rowMin,current[j]);
            }
            if(rowMin>limit)return false;
            int[] swap=previous;previous=current;current=swap;
        }
        return previous[wanted.length()]<=limit;
    }
    static boolean sameLocation(Item a,Item b){return Math.abs(a.y()-b.y())<=8&&Math.abs(a.x()-b.x())<=8;}
    Item unique(String text){Item match=null;for(Item i:items)if(i.text.equals(normalize(text))){if(match!=null&&!sameLocation(i,match))return null;match=i;}return match;}
    Item heading(String label){
        Item exact=unique(label);if(exact!=null||!visual)return exact;
        Item match=null;for(Item i:items)if(fuzzy(i.text,label)){
            if(match!=null&&!sameLocation(match,i))return null;match=i;
        }
        return match;
    }
    boolean isPanel(){int n=0;for(String group:GROUPS)if(heading(group)!=null)n++;return n>=2&&(unique("查看结果")!=null||unique("重置选项")!=null);}
    Item option(int group,String value){
        Item heading=heading(GROUPS[group]);if(heading==null)return null;
        int end=Integer.MAX_VALUE;
        for(String g:GROUPS){Item h=heading(g);if(h!=null&&h.top>heading.top)end=Math.min(end,h.top);}
        Item footer=unique("查看结果");if(footer!=null)end=Math.min(end,footer.top);
        String wanted=normalize(value);Item exact=null,fuzzyMatch=null;boolean fuzzyAmbiguous=false;
        for(Item i:items)if(i.top>=heading.bottom&&i.bottom<=end){
            if(i.text.equals(wanted)){
                if(exact!=null&&!sameLocation(exact,i))return null;
                // Douyin exposes the same option twice on some builds: once as
                // the selected parent (from its content description) and once
                // as the child TextView. Keep the selected observation.
                if(exact==null||(!exact.selected&&i.selected))exact=i;
            }
            else if(!(group==1&&Arrays.asList("不限","一天内","一周内","半年内").contains(i.text))&&fuzzy(i.text,wanted)){if(fuzzyMatch!=null&&!sameLocation(fuzzyMatch,i))fuzzyAmbiguous=true;fuzzyMatch=i;}
        }
        return exact!=null?exact:fuzzyAmbiguous?null:fuzzyMatch;
    }
    String diagnostic(int group){
        Item h=heading(GROUPS[group]);StringBuilder out=new StringBuilder("标题="+(h==null?"缺失":h.text)+"；候选=");
        int end=Integer.MAX_VALUE;if(h!=null)for(String label:GROUPS){Item next=heading(label);if(next!=null&&next.top>h.top)end=Math.min(end,next.top);}
        for(Item i:items)if(h!=null&&i.top>=h.bottom&&i.bottom<=end&&out.length()<900)out.append('[').append(i.text).append("|选中=").append(i.selected).append('|').append(i.left).append(',').append(i.top).append(']');
        return out.toString();
    }
}
