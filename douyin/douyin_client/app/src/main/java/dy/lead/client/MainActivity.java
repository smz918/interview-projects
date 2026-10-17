package dy.lead.client;
import android.app.*;
import android.os.*;
import android.content.*;
import android.text.InputType;
import android.widget.*;
import android.view.Gravity;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    static final String API="http://47.120.41.123:9000/douyin";
    EditText phone,password,confirm,referrer; TextView status; Button login,register,submit; boolean busy,signupMode;
    LinearLayout confirmBox; WorkspaceUi style;
    public void onCreate(Bundle b){
        super.onCreate(b);
        style=new WorkspaceUi(this);getWindow().setStatusBarColor(0xfff5f6fa);getWindow().setNavigationBarColor(0xfff5f6fa);
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(WorkspaceUi.BG);
        LinearLayout wrap=style.column();wrap.setGravity(Gravity.CENTER);wrap.setPadding(style.dp(20),style.dp(32),style.dp(20),style.dp(32));scroll.addView(wrap);
        LinearLayout box=style.column();box.setPadding(style.dp(24),style.dp(24),style.dp(24),style.dp(28));box.setBackground(style.shape(0xffffffff,WorkspaceUi.LINE,22));box.setElevation(style.dp(2));
        wrap.addView(box,new LinearLayout.LayoutParams(Math.min(getResources().getDisplayMetrics().widthPixels-style.dp(40),style.dp(480)),-2));
        TextView title=style.text("518获客先锋 · 抖音",26);title.setTypeface(null,android.graphics.Typeface.BOLD);box.addView(title);
        box.addView(style.text("登录控制台，管理你的获客任务",15));
        LinearLayout[] tabs=style.pair(box);login=style.button(tabs[0],"登录",true);register=style.button(tabs[1],"注册",false);
        phone=style.field(box,"手机号",getSharedPreferences("workspace",0).getString("login_phone",""),InputType.TYPE_CLASS_PHONE);phone.setHint("请输入手机号");phone.setSingleLine(true);
        password=style.field(box,"密码","",129);password.setHint("请输入至少6位密码");password.setSingleLine(true);
        confirmBox=style.column();box.addView(confirmBox);confirm=style.field(confirmBox,"确认密码","",129);confirm.setHint("请再次输入密码");confirm.setSingleLine(true);
        referrer=style.field(confirmBox,"推荐人（选填）","",InputType.TYPE_CLASS_TEXT);referrer.setHint("推荐人姓名或手机号");referrer.setSingleLine(true);referrer.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(100)});
        submit=style.button(box,"登录",true);status=style.text("没有账号？点击上方注册，开通后即可使用。",13);box.addView(status);
        login.setOnClickListener(v->mode(false));register.setOnClickListener(v->mode(true));submit.setOnClickListener(v->auth(signupMode));mode(false);setContentView(scroll);
    }
    void mode(boolean signup){if(busy)return;signupMode=signup;confirmBox.setVisibility(signup?android.view.View.VISIBLE:android.view.View.GONE);submit.setText(signup?"注册账号":"登录");login.setTextColor(signup?WorkspaceUi.INK:0xffffffff);register.setTextColor(signup?0xffffffff:WorkspaceUi.INK);login.setBackground(style.shape(signup?0xffffffff:WorkspaceUi.PINK,WorkspaceUi.LINE,15));register.setBackground(style.shape(signup?WorkspaceUi.PINK:0xffffffff,WorkspaceUi.LINE,15));status.setText(signup?"注册后请联系管理员开通抖音权限。":"请输入手机号和密码");}
    void auth(boolean signup){
        if(busy)return;String p=phone.getText().toString().trim(),pw=password.getText().toString(),referredBy=referrer.getText().toString().trim();
        if(!p.matches("1[3-9][0-9]{9}")||pw.length()<6||pw.length()>128){status.setText("请填写11位手机号和6至128位密码");return;}
        if(signup&&!pw.equals(confirm.getText().toString())){status.setText("两次输入的密码不一致");return;}
        busy=true;login.setEnabled(false);register.setEnabled(false);submit.setEnabled(false);status.setText("正在连接...");
        new Thread(()->{try{
            String id=deviceId(this);
            JSONObject body=new JSONObject().put("phone",p).put("password",pw).put("device_id",id);
            if(signup)body.put("referrer",referredBy);
            JSONObject r=callApi(signup?"/api/v1/auth/register":"/api/v1/auth/login",body,"");
            if(signup){runOnUiThread(()->status.setText("注册成功，请联系管理员开通抖音权限，再点击登录。"));return;}
            String token=r.getString("token");if(token.isEmpty())throw new IOException("未收到设备令牌");
            // Verify authorization independently of login: older servers might return a token for a frozen device.
            JSONObject h=callApi("/api/v1/device/"+URLEncoder.encode(id,"UTF-8")+"/heartbeat",new JSONObject().put("app_version",BuildConfig.VERSION_NAME),token);
            if(!"active".equals(h.optString("status")))throw new IOException("设备授权已停用");
            getSharedPreferences("workspace",0).edit().putString("token",token).putString("login_phone",p).putString("auth_status","已授权").apply();
            AuthorizationCache.save(getSharedPreferences("workspace",0),token);
            runOnUiThread(()->{startActivity(new Intent(this,AutomationActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));finish();});
        }catch(Exception e){runOnUiThread(()->status.setText((signup?"注册失败：":"登录失败：")+e.getMessage()));}
        finally{runOnUiThread(()->{busy=false;login.setEnabled(true);register.setEnabled(true);submit.setEnabled(true);});}}).start();
    }
    static boolean requireLogin(Activity activity){
        if(!activity.getSharedPreferences("workspace",0).getString("token","").isEmpty())return true;
        activity.startActivity(new Intent(activity,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity.finish();return false;
    }
    static void logout(Activity activity){
        android.content.SharedPreferences p=activity.getSharedPreferences("workspace",0);String token=p.getString("token","");
        if(AutomationService.instance!=null){AutomationService.instance.finish("用户退出登录，任务已停止");AutomationService.instance.dismissOverlay();}
        SharedPreferences.Editor edit=p.edit().remove("token").putString("auth_status","未登录");AuthorizationCache.clear(edit);edit.apply();
        new Thread(()->{try{callApi("/api/v1/auth/logout",new JSONObject(),token);}catch(Exception ignored){}}).start();
        requireLogin(activity);
    }
    static String deviceId(Context context){
        return android.os.Build.MANUFACTURER+"-"+android.os.Build.MODEL+"-"+android.provider.Settings.Secure.getString(context.getContentResolver(),android.provider.Settings.Secure.ANDROID_ID);
    }
    static JSONObject callApi(String path,JSONObject body,String token)throws Exception{return callApi(path,body,token,"","");}
    static JSONObject callApi(String path,JSONObject body,String token,String deviceId)throws Exception{return callApi(path,body,token,deviceId,"");}
    static JSONObject callApi(String path,JSONObject body,String token,String deviceId,String proxyToken)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(10000);c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
        if(!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        if(deviceId!=null&&!deviceId.isEmpty())c.setRequestProperty("X-Device-Id",deviceId);
        if(proxyToken!=null&&!proxyToken.isEmpty())c.setRequestProperty("X-AI-Proxy-Token",proxyToken);
        try{
            try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();if(in==null)throw new IOException("HTTP "+code);
            StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}
            JSONObject result=new JSONObject(s.toString());if(code>=400)throw new IOException(result.optString("detail","HTTP "+code));return result;
        }finally{c.disconnect();}
    }
    static JSONObject callApiGet(String path,String token)throws Exception{return callApiMethod("GET",path,null,token);}
    static JSONObject callApiDelete(String path,String token)throws Exception{return callApiMethod("DELETE",path,null,token);}
    static JSONObject callApiMethod(String method,String path,JSONObject body,String token)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(20000);c.setRequestMethod(method);c.setDoInput(true);
        if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");}
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            if(body!=null)try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}
            int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();if(in==null)throw new IOException("HTTP "+code);
            StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}
            JSONObject result=s.length()==0?new JSONObject():new JSONObject(s.toString());if(code>=400)throw new IOException(result.optString("detail","HTTP "+code));return result;
        }finally{c.disconnect();}
    }
    static JSONObject callApiBytes(String path,byte[] body,String token,String contentType)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(API+path).openConnection();
        c.setConnectTimeout(7000);c.setReadTimeout(30000);c.setRequestMethod("POST");c.setDoOutput(true);c.setFixedLengthStreamingMode(body.length);c.setRequestProperty("Content-Type",contentType);
        if(token!=null&&!token.isEmpty())c.setRequestProperty("Authorization","Bearer "+token);
        try{
            try(OutputStream o=c.getOutputStream()){o.write(body);}
            int code=c.getResponseCode();InputStream in=code<400?c.getInputStream():c.getErrorStream();if(in==null)throw new IOException("HTTP "+code);
            StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String l;while((l=r.readLine())!=null)s.append(l);}
            JSONObject result=new JSONObject(s.toString());if(code>=400)throw new IOException(result.optString("detail","HTTP "+code));return result;
        }finally{c.disconnect();}
    }
}
