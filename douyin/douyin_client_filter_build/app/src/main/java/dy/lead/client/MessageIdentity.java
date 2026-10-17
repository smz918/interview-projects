package dy.lead.client;

/** Versioned UI aliases, with semantic profile fallback; never match chat message bodies. */
final class MessageIdentity {
    static String normalize(String name){return name==null?"":name.replace('\u00a0',' ').replace('\u2006',' ').trim();}
    static boolean profileName(String id,String text,String desc){
        String name=normalize(text);if(name.isEmpty())return false;
        return id.endsWith("/ttw")||id.endsWith("/tlq")||desc.equals(text+"，复制名字和修改备注");
    }
    static boolean chatTitle(String id,int top,int bottom,int screenHeight){
        // Douyin changes the generated suffix by build/device (vw+, vw_, vw3,
        // etc.).  Keep the semantic prefix and the header-band constraint so
        // message-bubble ids are never mistaken for the recipient title.
        int slash=id.lastIndexOf('/');
        String local=slash>=0?id.substring(slash+1):id;
        boolean headerAlias=local.startsWith("vw")||local.equals("tto");
        return headerAlias&&top>=0&&bottom>top&&bottom<screenHeight*.25f;
    }
    static boolean same(boolean verified,String profile,String chat){
        return verified&&!normalize(profile).isEmpty()&&normalize(profile).equals(normalize(chat));
    }
}
