package dy.lead.client;
import java.util.*;
public class AccountMatchTest {
    static FilterPage.Item row(String text,int y){return new FilterPage.Item(text,180,y,430,y+30,false);}
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        check(AccountMatch.labeled("抖音号： 343266215").equals("343266215"));
        check(AccountMatch.labeled("抖音号 343266215").equals("343266215"));
        check(!AccountMatch.labeled("抖音号：273933074").equals("2739330746"));
        check(AccountMatch.labeled("抖音号：343266215 推荐").isEmpty());
        FilterPage p=new FilterPage(Arrays.asList(row("343266215",70),row("抖音号：34326621",250),row("抖音号：343266215",350)),true);
        check(AccountMatch.visual(p,"343266215",200,1600).top==350);
        check(AccountMatch.visual(p,"3432662",200,1600)==null);
        p=new FilterPage(Arrays.asList(row("抖音号：343266215",350),row("抖音号：343266215",550)),true);
        check(AccountMatch.visual(p,"343266215",200,1600)==null);
        p=new FilterPage(Arrays.asList(new FilterPage.Item("抖音号：",150,350,240,380,false),new FilterPage.Item("343266215",246,350,410,380,true)),true);
        check(AccountMatch.visual(p,"343266215",200,1600)!=null);
        FilterPage.Item truncated=new FilterPage.Item("抖音号:LiGang19780……",185,359,489,387,true);
        p=new FilterPage(Arrays.asList(truncated),true);
        check(AccountMatch.visual(p,"LiGang19780628",200,1600)==null);
        check(AccountMatch.truncatedCandidate(p,200,1600)==truncated);
        check(AccountMatch.labeled(truncated.text).isEmpty());
        p=new FilterPage(Arrays.asList(truncated,new FilterPage.Item("抖音号:Other123…",185,559,489,587,true)),true);
        check(AccountMatch.truncatedCandidate(p,200,1600)==null);
        p=new FilterPage(Arrays.asList(new FilterPage.Item("抖音号:LiGang19780……",185,359,489,387,false),new FilterPage.Item("关注",500,359,600,387,true)),true);
        check(AccountMatch.truncatedCandidate(p,200,1600)==null);
        check(AccountMatch.truncatedCandidate(new FilterPage(Arrays.asList(truncated),true),400,1600)==null);
        FilterPage.Item huawei=new FilterPage.Item("抖音号:huainanwarngd.",255,550,731,598,true);
        p=new FilterPage(Arrays.asList(huawei),true);
        check(AccountMatch.visual(p,"huainanwangd75",330,2239)==null);
        check(AccountMatch.truncatedCandidate(p,330,2239)==huawei);
        check(AccountMatch.truncatedCandidate(new FilterPage(Arrays.asList(new FilterPage.Item(huawei.text,255,550,731,598,false)),true),330,2239)==null);
        check(!AccountMatch.labeled("抖音号:huainanwarngd.").equals("huainanwangd75"));
        System.out.println("PASS: exact identity, ellipsized navigation candidates, ambiguity and non-highlight rejection");
        check(MessageIdentity.profileName("com.ss.android.ugc.aweme:id/tlq","Stewie Griffin","Stewie Griffin，复制名字和修改备注"));
        check(MessageIdentity.profileName("com.ss.android.ugc.aweme:id/ttw","Stewie Griffin",""));
        check(MessageIdentity.chatTitle("com.ss.android.ugc.aweme:id/vw_",120,210,2239));
        check(!MessageIdentity.chatTitle("com.ss.android.ugc.aweme:id/vw_",700,800,2239));
        check(MessageIdentity.same(true,"Stewie Griffin","Stewie Griffin"));
    }
}
