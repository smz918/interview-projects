package dy.lead.client;

import java.util.Random;

/** Shared by queue, UI and tests: attempted never means successfully sent. */
final class MessagePolicy {
    static String account(String value){return value==null?"":value.trim();}
    static boolean validAccount(String value){return account(value).matches("[A-Za-z0-9_.-]{1,64}");}
    static boolean canSelect(String account,String state,boolean retry){
        return validAccount(account)&&(state.isEmpty()||state.equals("未尝试")||(retry&&state.equals("失败")));
    }
    static int color(String state){
        switch(state){case "成功":return 0xffe0f2e5;case "失败":return 0xffffe3e3;case "待核实":return 0xffffecd1;
            case "查找中":case "发送中":return 0xffe0ebff;default:return 0xffffffff;}
    }
    static int between(Random random,int min,int max){
        if(min<0||max<min||max>86400)throw new IllegalArgumentException("间隔范围无效");
        return min+random.nextInt(max-min+1);
    }
    static void validate(int min,int max,int batchMin,int batchMax,int restMin,int restMax,int daily){
        if(min<2||max<min||max>3600||batchMin<1||batchMax<batchMin||batchMax>1000||restMin<2||restMax<restMin||restMax>86400||daily<1||daily>1000)
            throw new IllegalArgumentException("请检查最小/最大间隔、休息次数和每日上限");
    }
}
