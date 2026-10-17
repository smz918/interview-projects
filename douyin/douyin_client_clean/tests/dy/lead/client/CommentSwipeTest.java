package dy.lead.client;

public class CommentSwipeTest {
    public static void main(String[] args){
        int[][] panels={{0,800,1080,2200},{0,700,720,1450},{60,200,1400,2300},{100,400,600,600}};
        for(int[] p:panels){
            int[] drag=CommentSwipe.path(p[0],p[1],p[2],p[3]);
            if(drag==null||drag[0]<=p[0]||drag[0]>=p[2]||drag[2]<=p[1]||drag[1]>=p[3])throw new AssertionError("Drag left comment panel");
            double fraction=(double)(drag[1]-drag[2])/(p[3]-p[1]);
            if(fraction<.74||fraction>.76)throw new AssertionError("Drag must advance about 75%, leaving overlap");
            int[] shortDrag=CommentSwipe.path(p[0],p[1],p[2],p[3],true);
            double shortFraction=(double)(shortDrag[1]-shortDrag[2])/(p[3]-p[1]);
            if(shortFraction<.19||shortFraction>.21||shortDrag[2]<=p[1]||shortDrag[1]>=p[3])throw new AssertionError("Partial rows need 20% recovery within list");
        }
        if(CommentSwipe.path(0,0,500,0)!=null||CommentSwipe.path(500,0,0,600)!=null)throw new AssertionError("Invalid panel should not swipe");
        System.out.println("CommentSwipeTest passed: phone, tablet, inset and small comment panels");
    }
}
