package dy.lead.client;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.hardware.display.*;
import android.media.*;
import android.media.projection.*;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** MediaProjection fallback for pre-Android-11 accessibility screenshots. */
public final class LegacyCaptureService extends Service {
    static LegacyCaptureService instance;
    interface Callback { void done(Bitmap image,String error); }
    private final Handler main=new Handler(Looper.getMainLooper());
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Callback pending;
    private int width,height,rotation;
    private final Runnable timeout=()->complete(null,"屏幕捕获等待超时，请重新运行并允许屏幕捕获");
    @Override public IBinder onBind(Intent intent){return null;}
    static boolean ready(){return instance!=null&&instance.projection!=null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(android.os.Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel("screen_verify","筛选画面校验",NotificationManager.IMPORTANCE_LOW));
        }
        Notification.Builder builder=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"screen_verify"):new Notification.Builder(this);
        startForeground(518,builder.setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("518获客先锋 · 筛选画面校验").setContentText("仅在本机识别画面，任务结束后关闭").build());
        try{
            if(intent==null)throw new IllegalStateException("missing consent");
            projection=((MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE)).getMediaProjection(intent.getIntExtra("result",0),intent.getParcelableExtra("consent"));
            if(projection==null)throw new IllegalStateException("missing projection");
            projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){
                projection=null;complete(null,"屏幕捕获授权已结束，请重新运行并允许屏幕捕获");stopSelf();
            }},main);
            instance=this;
            if(AutomationService.instance!=null)AutomationService.instance.captureReady();else stopSelf();
        }catch(Exception e){if(AutomationService.instance!=null)AutomationService.instance.captureDenied();stopSelf();}
        return START_NOT_STICKY;
    }
    void capture(Callback callback){
        if(pending!=null){callback.done(null,"已有屏幕捕获请求正在处理");return;}
        pending=callback;
        try{
            // A static filter page may not emit another frame. Recreate the old-API
            // display for every request rather than waiting for a screen change.
            closeDisplay();
            DisplayMetrics metrics=new DisplayMetrics();android.view.Display screen=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();screen.getRealMetrics(metrics);
            if(reader==null||width!=metrics.widthPixels||height!=metrics.heightPixels||rotation!=screen.getRotation()){
                closeDisplay();width=metrics.widthPixels;height=metrics.heightPixels;rotation=screen.getRotation();
                reader=ImageReader.newInstance(width,height,PixelFormat.RGBA_8888,2);
                reader.setOnImageAvailableListener(r->{
                    try(Image image=r.acquireLatestImage()){
                        if(image==null||pending==null)return;
                        Image.Plane plane=image.getPlanes()[0];int stride=plane.getPixelStride();
                        if(stride!=4)throw new IllegalStateException("unexpected pixel stride");
                        Bitmap padded=Bitmap.createBitmap(plane.getRowStride()/stride,height,Bitmap.Config.ARGB_8888);
                        padded.copyPixelsFromBuffer(plane.getBuffer());
                        Bitmap bitmap=Bitmap.createBitmap(padded,0,0,width,height);if(bitmap!=padded)padded.recycle();
                        complete(bitmap,"");
                    }catch(Exception e){complete(null,"屏幕捕获失败："+e.getClass().getSimpleName());}
                },main);
                display=projection.createVirtualDisplay("filter-verification",width,height,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,main);
            }
            main.postDelayed(timeout,6000);
        }catch(Exception e){complete(null,"屏幕捕获启动失败："+e.getClass().getSimpleName());}
    }
    private void complete(Bitmap bitmap,String error){
        main.removeCallbacks(timeout);Callback callback=pending;pending=null;
        if(callback!=null)callback.done(bitmap,error);else if(bitmap!=null)bitmap.recycle();
    }
    private void closeDisplay(){if(display!=null){display.release();display=null;}if(reader!=null){reader.close();reader=null;}}
    @Override public void onDestroy(){
        if(instance==this)instance=null;complete(null,"屏幕捕获服务已关闭");closeDisplay();
        MediaProjection old=projection;projection=null;if(old!=null)old.stop();stopForeground(true);super.onDestroy();
    }
}
