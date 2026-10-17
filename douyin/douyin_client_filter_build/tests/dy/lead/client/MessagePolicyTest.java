package dy.lead.client;
import java.util.Random;
public class MessagePolicyTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(MessagePolicy.canSelect("64354607333","未尝试",false));
        check(!MessagePolicy.canSelect("","未尝试",true));check(!MessagePolicy.canSelect("重名昵称","未尝试",true));
        check(!MessagePolicy.canSelect("id1","成功",true));check(!MessagePolicy.canSelect("id1","待核实",true));
        check(!MessagePolicy.canSelect("id1","发送中",true));check(!MessagePolicy.canSelect("id1","失败",false));check(MessagePolicy.canSelect("id1","失败",true));
        check(MessagePolicy.color("成功")!=MessagePolicy.color("失败"));check(MessagePolicy.color("失败")!=MessagePolicy.color("未尝试"));check(MessagePolicy.color("待核实")!=MessagePolicy.color("未尝试"));
        Random r=new Random(7);for(int i=0;i<10000;i++){int n=MessagePolicy.between(r,20,60);check(n>=20&&n<=60);}check(MessagePolicy.between(r,20,20)==20);
        for(int[] v:new int[][]{{60,20,3,5,60,180,30},{20,60,5,3,60,180,30},{20,60,3,5,180,60,30},{20,60,3,5,60,180,0}}){boolean rejected=false;try{MessagePolicy.validate(v[0],v[1],v[2],v[3],v[4],v[5],v[6]);}catch(IllegalArgumentException e){rejected=true;}check(rejected);}
        System.out.println("PASS: dedupe/retry policy, outcome colors, account validity, random interval bounds and invalid ranges");
    }
}
