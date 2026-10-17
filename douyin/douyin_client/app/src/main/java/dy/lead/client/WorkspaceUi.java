package dy.lead.client;

import android.app.Activity;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

/** Shared native layout, measured in dp, with equal-width fields on every phone. */
final class WorkspaceUi {
    static final int BG=0xfff5f6fa,INK=0xff202428,MUTED=0xff90959b,PINK=0xffff2855,LINE=0xffe3e5e9;
    final Activity a; TextView access,overlay;LinearLayout page;ScrollView scroll;
    WorkspaceUi(Activity a){this.a=a;}
    int dp(int n){return Math.round(a.getResources().getDisplayMetrics().density*n);}
    GradientDrawable shape(int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    LinearLayout column(){LinearLayout c=new LinearLayout(a);c.setOrientation(1);return c;}
    TextView text(String value,int size){TextView t=new TextView(a);t.setText(value);t.setTextSize(size);t.setTextColor(INK);t.setPadding(0,dp(5),0,dp(7));return t;}
    TextView label(LinearLayout p,String s){TextView t=text(s,15);t.setTextColor(0xff5f666d);p.addView(t);return t;}
    LinearLayout page(String title,String subtitle){
        a.getWindow().setStatusBarColor(0xff00988c);a.getWindow().setNavigationBarColor(0xffffffff);
        LinearLayout root=column();root.setBackgroundColor(BG);
        LinearLayout header=column();header.setPadding(dp(16),dp(12),dp(16),dp(12));header.setBackgroundColor(0xffffffff);
        LinearLayout heading=new LinearLayout(a);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name=text(title,22);name.setTypeface(null,Typeface.BOLD);heading.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        access=badge("无障碍");heading.addView(access);access.setOnClickListener(v->a.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        overlay=badge("悬浮窗");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-2,dp(34));bp.leftMargin=dp(6);heading.addView(overlay,bp);
        overlay.setOnClickListener(v->new android.app.AlertDialog.Builder(a).setMessage("任务悬浮窗由无障碍服务提供，开启服务后即可使用。").setPositiveButton("知道了",null).show());
        header.addView(heading);TextView sub=text(subtitle,12);sub.setTextColor(MUTED);header.addView(sub);
        LinearLayout account=new LinearLayout(a);account.setGravity(Gravity.CENTER_VERTICAL);
        TextView signed=text("抖音 · "+a.getSharedPreferences("workspace",0).getString("login_phone","")+" · 已登录",12);signed.setTextColor(MUTED);account.addView(signed,new LinearLayout.LayoutParams(0,-2,1));
        TextView logout=badge("退出登录");logout.setTextColor(PINK);logout.setBackground(shape(0xfffff0f3,0,12));account.addView(logout);logout.setOnClickListener(v->new android.app.AlertDialog.Builder(a).setMessage("退出登录会停止当前任务，已保存的客户记录保留。").setNegativeButton("取消",null).setPositiveButton("退出登录",(d,w)->MainActivity.logout(a)).show());header.addView(account);
        header.setElevation(dp(1));
        scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout content=column();content.setGravity(Gravity.CENTER_HORIZONTAL);scroll.addView(content);
        content.addView(header,new LinearLayout.LayoutParams(-1,-2));
        page=column();page.setPadding(dp(14),dp(12),dp(14),dp(24));
        int width=Math.min(a.getResources().getDisplayMetrics().widthPixels,dp(620));content.addView(page,new LinearLayout.LayoutParams(width,-2));
        a.setContentView(root);refreshPermissions();return page;
    }
    TextView badge(String s){TextView t=text(s,11);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);t.setMinHeight(dp(34));return t;}
    void refreshPermissions(){boolean on=AutomationService.instance!=null;for(TextView t:new TextView[]{access,overlay})if(t!=null){t.setText((t==access?"无障碍":"悬浮窗")+(on?" ✓":" ×"));t.setTextColor(on?0xff40916e:0xffb84965);t.setBackground(shape(on?0xffeef8f2:0xfffff0f3,on?0xffd5e8dc:0xffeed6df,16));}}
    LinearLayout card(LinearLayout p){LinearLayout c=column();c.setPadding(dp(14),dp(14),dp(14),dp(14));c.setBackground(shape(0xffffffff,0,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);p.addView(c,lp);return c;}
    LinearLayout[] pair(LinearLayout p){LinearLayout row=new LinearLayout(a);p.addView(row,new LinearLayout.LayoutParams(-1,-2));LinearLayout left=column(),right=column();LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(0,-2,1),r=new LinearLayout.LayoutParams(0,-2,1);l.rightMargin=dp(5);r.leftMargin=dp(5);row.addView(left,l);row.addView(right,r);return new LinearLayout[]{left,right};}
    Button button(LinearLayout p,String value,boolean primary){Button b=new Button(a);b.setAllCaps(false);b.setText(value);b.setTextSize(16);b.setTextColor(primary?0xffffffff:INK);b.setBackgroundTintList(null);b.setBackground(shape(primary?PINK:0xffffffff,primary?0:LINE,15));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(8),dp(6),dp(8),dp(6));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);b.setMinHeight(dp(48));p.addView(b,lp);return b;}
    EditText field(LinearLayout p,String title,String value,int type){label(p,title);EditText e=new EditText(a);e.setText(value);e.setTextSize(16);e.setTextColor(INK);e.setHintTextColor(MUTED);e.setInputType(type);e.setPadding(dp(12),dp(10),dp(12),dp(10));e.setBackground(shape(0xfffafbfc,LINE,14));e.setMinHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);p.addView(e,lp);return e;}
    void multiline(EditText e){e.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);e.setGravity(Gravity.TOP);e.setMinLines(3);}
    Spinner spinner(LinearLayout p,String title,String[] values,String current){
        label(p,title);RoundedSpinner s=new RoundedSpinner(values,title);
        for(int i=0;i<values.length;i++)if(values[i].equals(current)){s.setSelection(i);break;}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(52));lp.bottomMargin=dp(12);p.addView(s,lp);return s;
    }
    /** Keeps Spinner's selection API, but draws both the field and popup ourselves. */
    final class RoundedSpinner extends Spinner {
        final String[] values;PopupWindow popup;TextView arrow;
        RoundedSpinner(String[] values,String title){
            super(a,Spinner.MODE_DIALOG);this.values=values;setContentDescription(title);
            setBackgroundTintList(null);setBackground(shape(0xfffafbfc,LINE,14));setPadding(0,0,0,0);
            setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_item,values){
                @Override public View getView(int pos,View convert,ViewGroup parent){
                    LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView value=text(getItem(pos),16);value.setSingleLine(true);value.setEllipsize(android.text.TextUtils.TruncateAt.END);value.setGravity(Gravity.CENTER_VERTICAL);value.setPadding(dp(13),0,0,0);
                    row.addView(value,new LinearLayout.LayoutParams(0,-1,1));
                    arrow=text(popup!=null&&popup.isShowing()?"▴":"▾",16);arrow.setGravity(Gravity.CENTER);arrow.setTextColor(MUTED);arrow.setPadding(0,0,0,0);
                    row.addView(arrow,new LinearLayout.LayoutParams(dp(32),-1));return row;
                }
            });
        }
        @Override public boolean performClick(){
            if(popup!=null&&popup.isShowing()){popup.dismiss();return true;}
            android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(keyboard!=null)keyboard.hideSoftInputFromWindow(getWindowToken(),0);
            LinearLayout menu=column();menu.setBackground(shape(0xffffffff,LINE,15));menu.setClipToOutline(true);
            ScrollView options=new ScrollView(a);options.setFillViewport(true);options.setVerticalScrollBarEnabled(false);options.setBackground(shape(0xffffffff,0,15));options.setClipToOutline(true);options.addView(menu);
            android.graphics.Rect screen=new android.graphics.Rect();getWindowVisibleDisplayFrame(screen);int[] location=new int[2];getLocationOnScreen(location);
            int below=screen.bottom-location[1]-getHeight()-dp(8),above=location[1]-screen.top-dp(8);
            int rowHeight=dp(Math.max(52,Math.round(36*a.getResources().getConfiguration().fontScale)));
            int wanted=rowHeight*values.length;boolean up=below<wanted&&above>below;
            int height=Math.min(wanted,Math.max(rowHeight,up?above:below));
            popup=new PopupWindow(options,getWidth(),height,true);popup.setBackgroundDrawable(shape(0xffffffff,LINE,15));popup.setOutsideTouchable(true);popup.setElevation(dp(10));popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
            for(int i=0;i<values.length;i++){
                final int pos=i;TextView option=text(values[i],16);option.setGravity(Gravity.CENTER_VERTICAL);option.setPadding(dp(14),0,dp(12),0);option.setTextColor(pos==getSelectedItemPosition()?0xffcf486a:INK);
                option.setBackgroundColor(pos==getSelectedItemPosition()?0xfffff2f6:0xffffffff);option.setSelected(pos==getSelectedItemPosition());
                option.setOnClickListener(v->{setSelection(pos);popup.dismiss();});menu.addView(option,new LinearLayout.LayoutParams(-1,rowHeight));
            }
            setBackground(shape(0xffffffff,0xffcf486a,14));if(arrow!=null)arrow.setText("▴");
            popup.setOnDismissListener(()->{setBackground(shape(0xfffafbfc,LINE,14));if(arrow!=null)arrow.setText("▾");});
            popup.showAsDropDown(this,0,up?-getHeight()-height-dp(6):dp(6));
            options.post(()->options.scrollTo(0,Math.max(0,getSelectedItemPosition()*rowHeight-(height-rowHeight)/2)));return true;
        }
        @Override protected void onDetachedFromWindow(){if(popup!=null)popup.dismiss();super.onDetachedFromWindow();}
    }
    CheckBox check(LinearLayout p,String title){CheckBox c=new CheckBox(a);c.setText(title);c.setTextColor(INK);c.setTextSize(13);c.setButtonTintList(ColorStateList.valueOf(PINK));c.setMinHeight(dp(42));p.addView(c);return c;}
    TextView status(LinearLayout p){TextView t=text("空闲 · 等待任务",14);t.setGravity(Gravity.CENTER);t.setTextColor(0xff40916e);t.setPadding(dp(12),dp(16),dp(12),dp(16));t.setBackground(shape(0xffeef8f2,0,10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);lp.bottomMargin=dp(4);p.addView(t,lp);return t;}
    void logStyle(TextView t){t.setTextColor(0xffb2dfe9);t.setTypeface(Typeface.MONOSPACE);t.setTextSize(12);t.setPadding(dp(12),dp(14),dp(12),dp(14));t.setBackground(shape(0xff13161d,0,12));t.setTextIsSelectable(true);t.setMaxHeight(dp(360));t.setMovementMethod(new android.text.method.ScrollingMovementMethod());t.setVerticalScrollBarEnabled(true);}
}
