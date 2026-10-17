package dy.lead.client;

import android.content.SharedPreferences;
import org.json.*;

public final class AiJudge {
    static final String DEFAULT_MODEL="doubao-seed-2-0-mini-260428";
    public static boolean configured(SharedPreferences p) {return !MainActivity.API.isEmpty()&&(!p.getString("token","").isEmpty()||!BuildConfig.AI_PROXY_TOKEN.isEmpty());}
    public static JSONObject judge(android.content.Context context,SharedPreferences p,String comment,String who,String target) throws Exception {
        return judge(context,p,comment,who,target,p.getString("ai_mode","均衡"));
    }
    public static JSONObject judge(android.content.Context context,SharedPreferences p,String comment,String who,String target,String mode) throws Exception {
        JSONObject body=new JSONObject().put("comment",comment).put("who",who).put("target",target).put("mode",mode);
        String token=p.getString("token","");
        return MainActivity.callApi("/api/v1/ai/judge",body,token,MainActivity.deviceId(context),BuildConfig.AI_PROXY_TOKEN);
    }
}
