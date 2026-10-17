package dy.lead.client;

/** Platform notices, separate from message bodies and historical failure icons. */
final class MessageSignals {
    static String restriction(String text){
        for(String key:new String[]{"身份安全验证","请完成身份验证","操作环境存在风险","操作频繁","操作太频繁","请完成验证","拖动滑块","私信功能已被限制","私信功能被限制","今日私信次数","发送过于频繁"})
            if(text.contains(key))return key;
        return "";
    }
    static String recipientBlock(String text){
        if(text.contains("仅和他互关的人可发消息"))return "对方仅允许互关的人发消息，当前账号不在范围内";
        if(text.contains("仅他关注的人可发消息"))return "对方仅允许其关注的人发消息，当前账号不在范围内";
        // “只能发送一条消息” is the normal first-message hint on some
        // Douyin builds, not a restriction.  Treat only explicit refusal or
        // reply-gated notices as a block.
        for(String term:new String[]{"对方回复后可发","对方回复后才能","对方回复前","无法向对方发送","对方拒收","被对方拉黑","暂时无法发送"})if(text.contains(term))return term;
        return "";
    }
    static boolean sendButton(String text){return text.equals("发送")||text.equals("发送，按钮")||text.equals("发送按钮");}
}
