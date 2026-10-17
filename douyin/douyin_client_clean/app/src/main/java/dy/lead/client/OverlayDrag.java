package dy.lead.client;

import android.content.SharedPreferences;
import android.view.*;

/** Drag only from the heading/status area, leaving action buttons clickable. */
final class OverlayDrag implements View.OnTouchListener {
    private final WindowManager manager;
    private final View window;
    private final WindowManager.LayoutParams params;
    private final SharedPreferences prefs;
    private final int slop;
    private float downX,downY;private int startX,startY;private boolean dragging;
    OverlayDrag(WindowManager manager,View window,WindowManager.LayoutParams params,SharedPreferences prefs){
        this.manager=manager;this.window=window;this.params=params;this.prefs=prefs;
        slop=ViewConfiguration.get(window.getContext()).getScaledTouchSlop();
    }
    private android.graphics.Point room(){
        android.graphics.Point size=new android.graphics.Point();manager.getDefaultDisplay().getSize(size);
        size.x=Math.max(0,size.x-window.getWidth());size.y=Math.max(0,size.y-window.getHeight());return size;
    }
    void restore(){
        android.graphics.Point max=room();
        move(Math.round(prefs.getFloat("overlay_x_fraction",0f)*max.x),Math.round(prefs.getFloat("overlay_y_fraction",1f)*max.y));
    }
    private void move(int x,int y){
        android.graphics.Point max=room();params.x=Math.max(0,Math.min(max.x,x));params.y=Math.max(0,Math.min(max.y,y));
        if(window.isAttachedToWindow())manager.updateViewLayout(window,params);
    }
    public boolean onTouch(View handle,android.view.MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=event.getRawX();downY=event.getRawY();startX=params.x;startY=params.y;dragging=false;return true;
            case MotionEvent.ACTION_MOVE:
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                if(!dragging&&Math.hypot(dx,dy)>slop)dragging=true;
                if(dragging)move(startX+Math.round(dx),startY+Math.round(dy));return true;
            case MotionEvent.ACTION_UP:
                if(dragging){android.graphics.Point max=room();prefs.edit().putFloat("overlay_x_fraction",max.x==0?0f:(float)params.x/max.x).putFloat("overlay_y_fraction",max.y==0?0f:(float)params.y/max.y).apply();}
                else handle.performClick();return true;
            case MotionEvent.ACTION_CANCEL:return true;
            default:return false;
        }
    }
}
