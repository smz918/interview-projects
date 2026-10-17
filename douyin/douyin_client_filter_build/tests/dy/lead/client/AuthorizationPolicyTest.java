package dy.lead.client;

/** Daily cache boundaries and session changes; no server/network calls. */
public final class AuthorizationPolicyTest {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args){
        long checked=1800000000000L,day=AuthorizationPolicy.INTERVAL_MS;
        String token="fixture-login-token",key=AuthorizationPolicy.tokenKey(token);
        check(day==86400000L,"One day means 24 hours");
        check(key.length()==64&&!key.contains(token),"Only fingerprint stored alongside check time");
        for(long age:new long[]{0,15000,35000,3600000,day-1}){
            check(AuthorizationPolicy.remaining(checked,checked+age,key,token)==day-age,"Reuse fixed deadline across task restarts age="+age);
        }
        check(AuthorizationPolicy.remaining(checked,checked+day,key,token)==0,"Revalidate exactly at 24 hours");
        check(AuthorizationPolicy.remaining(checked,checked+day+1,key,token)==0,"Expired after 24 hours");
        check(AuthorizationPolicy.remaining(checked,checked-1,key,token)==0,"Clock rollback must not revive a cache");
        check(AuthorizationPolicy.remaining(0,checked,key,token)==0,"Upgrade with no saved check must validate");
        check(AuthorizationPolicy.remaining(checked,checked,key,"other-session")==0,"New login token does not inherit another login's check");
        check(AuthorizationPolicy.remaining(checked,checked,key,"")==0,"Logout cannot authorize");
        check(AuthorizationPolicy.remaining(checked,checked,"",token)==0,"Missing cache fingerprint cannot authorize");
        System.out.println("PASS: 24-hour boundary, old 15/35-second intervals, stable deadline, restart reuse, clock rollback, upgrade, session replacement and logout");
    }
}
