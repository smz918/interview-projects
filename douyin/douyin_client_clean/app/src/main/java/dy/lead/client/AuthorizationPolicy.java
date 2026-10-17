package dy.lead.client;

/** A successful check lasts 24 hours; reusing it never renews the deadline. */
final class AuthorizationPolicy {
    static final long INTERVAL_MS=24L*60*60*1000;
    static long remaining(long checkedAt,long now,String checkedTokenKey,String token){
        if(token==null||token.isEmpty()||checkedAt<=0||now<checkedAt||!tokenKey(token).equals(checkedTokenKey))return 0;
        long age=now-checkedAt;
        return age>=INTERVAL_MS?0:INTERVAL_MS-age;
    }
    static String tokenKey(String token){
        try {
            byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();
        }catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
