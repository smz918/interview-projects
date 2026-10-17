package dy.lead.client;

/** Only a completed positive classification authorizes a profile lookup. */
final class IntentPolicy {
    static boolean fetchProfile(boolean enabled,String status,String account){
        return enabled && "意向".equals(status) && (account==null||account.trim().isEmpty());
    }
    static boolean pending(String status){return "待判断".equals(status)||"判断中".equals(status);}
}
