package dy.lead.client;

import android.graphics.*;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;

/** Bundled on-device OCR: no screenshot upload and no Google Play Services download. */
final class ScreenText {
    interface Callback {void done(FilterPage page,String error);}
    final TextRecognizer recognizer=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
    void read(Bitmap bitmap,Callback callback){
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(text->{
            List<FilterPage.Item> items=new ArrayList<>();
            for(Text.TextBlock block:text.getTextBlocks())for(Text.Line line:block.getLines()){
                add(items,line.getText(),line.getBoundingBox(),bitmap);
                // ML Kit can merge several adjacent options into one line.
                if(line.getElements().size()>1)for(Text.Element element:line.getElements())add(items,element.getText(),element.getBoundingBox(),bitmap);
            }
            callback.done(new FilterPage(items,true),"");
        }).addOnFailureListener(e->callback.done(null,"截图文字识别失败："+e.getClass().getSimpleName())).addOnCompleteListener(t->bitmap.recycle());
    }
    static void add(List<FilterPage.Item> items,String text,Rect r,Bitmap b){
        if(r==null||r.isEmpty())return;
        int red=0,dark=0;
        for(int y=Math.max(0,r.top);y<Math.min(b.getHeight(),r.bottom);y++)for(int x=Math.max(0,r.left);x<Math.min(b.getWidth(),r.right);x++){
            int c=b.getPixel(x,y),rr=Color.red(c),g=Color.green(c),bb=Color.blue(c);
            if(rr>160&&rr-g>65&&rr-bb>30)red++;
            if(Math.max(rr,Math.max(g,bb))<190)dark++;
        }
        boolean selected=red>=8&&red>dark*0.65;
        items.add(new FilterPage.Item(text,r.left,r.top,r.right,r.bottom,selected));
    }
    void close(){recognizer.close();}
}
