package dy.lead.client;

public class MessageSignalsTest {
    static void check(boolean b){if(!b)throw new AssertionError();}
    public static void main(String[] args){
        check(MessageSignals.restriction("身份安全验证").equals("身份安全验证"));
        check(!MessageSignals.restriction("系统识别你的操作环境存在风险，为保证账号安全，请完成身份验证").isEmpty());
        check(MessageSignals.restriction("为保障用户沟通安全，未互相关注的陌生人违规消息可能会被处理，请遵守法律法规和社区规范").isEmpty());
        check(MessageSignals.recipientBlock("对方设置了仅和他互关的人可发消息，由于你不在范围内，暂无法给对方发送消息").contains("互关"));
        check(MessageSignals.recipientBlock("对方设置了仅他关注的人可发消息").contains("关注"));
        check(!MessageSignals.recipientBlock("\u2006对方回复后可发").isEmpty());
        check(MessageSignals.recipientBlock("\u2006只能发送一条消息").isEmpty());
        check(MessageSignals.sendButton("发送"));check(MessageSignals.sendButton("发送，按钮"));
        check(!MessageSignals.sendButton("发送失败"));check(!MessageSignals.sendButton("发送中"));
        System.out.println("PASS: real XML verification notices, recipient permissions, editable hints and exact send action");
    }
}
