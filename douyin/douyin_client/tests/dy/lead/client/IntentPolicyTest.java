package dy.lead.client;

public class IntentPolicyTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args){
        check(IntentPolicy.fetchProfile(true,"意向",""));
        check(!IntentPolicy.fetchProfile(false,"意向",""));
        check(!IntentPolicy.fetchProfile(true,"意向","existing_account"));
        for(String state:new String[]{"非意向","不确定","判断失败","待判断","判断中","", "意向客户"})check(!IntentPolicy.fetchProfile(true,state,""));
        check(IntentPolicy.pending("判断中"));check(IntentPolicy.pending("待判断"));check(!IntentPolicy.pending("判断失败"));
        System.out.println("PASS: only confirmed intent without an account can fetch a profile; pending/failure/unknown cannot");
    }
}
