package dy.lead.client;

import java.util.*;
import java.util.regex.*;

/** Exact public ID matching; never repair OCR digits or accept prefixes. */
final class AccountMatch {
    static String labeled(String text){
        Matcher m=Pattern.compile("^抖音号\\s*[：:]?\\s*([A-Za-z0-9_.-]{1,64})\\s*$").matcher(text==null?"":text.trim());
        return m.matches()?m.group(1):"";
    }
    static FilterPage.Item visual(FilterPage page,String target,int top,int bottom){
        FilterPage.Item found=null;
        for(FilterPage.Item item:page.items){
            if(item.top<=top||item.bottom>=bottom)continue;
            boolean exact=target.equals(labeled(item.text));
            if(!exact&&target.equals(item.text)){
                for(FilterPage.Item label:page.items)if(label.text.matches("抖音号[：:]?")&&label.right<=item.left+8&&item.left-label.right<item.bottom-item.top+30&&Math.abs(label.y()-item.y())<Math.max(8,(item.bottom-item.top)/2)){exact=true;break;}
            }
            if(!exact)continue;
            if(found!=null&&Math.abs(found.y()-item.y())>Math.max(found.bottom-found.top,item.bottom-item.top))return null;
            if(found==null||item.right-item.left<found.right-found.left)found=item;
        }
        return found;
    }
    // A highlighted, ellipsized label is only a navigation candidate, never identity proof.
    // The full account must still match on the profile before any message is written.
    static FilterPage.Item truncatedCandidate(FilterPage page,int top,int bottom){
        FilterPage.Item found=null;
        for(FilterPage.Item item:page.items){
            if(item.top<=top||item.bottom>=bottom||!item.selected)continue;
            // Huawei OCR can collapse the displayed ellipsis to a single dot.
            // Only a unique red account label is eligible; profile ID remains mandatory.
            if(!item.text.matches("^抖音号[：:][A-Za-z0-9_.-]+(?:…+|\\.+)$"))continue;
            if(found!=null&&Math.abs(found.y()-item.y())>Math.max(found.bottom-found.top,item.bottom-item.top))return null;
            if(found==null)found=item;
        }
        return found;
    }
}
