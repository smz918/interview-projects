package dy.lead.client;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;

/** System consent for Android 7–10 screen verification. Never persisted or uploaded. */
public final class LegacyCaptureActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if(state==null)startActivityForResult(((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).createScreenCaptureIntent(),1);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request!=1)return;
        if(result==RESULT_OK&&data!=null){
            Intent service=new Intent(this,LegacyCaptureService.class).putExtra("consent",data).putExtra("result",result);
            if(android.os.Build.VERSION.SDK_INT>=26)startForegroundService(service);else startService(service);
        }else if(AutomationService.instance!=null)AutomationService.instance.captureDenied();
        finish();
    }
}
