package dy.lead.client;

import android.os.*;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.util.*;

/** Bounded, serial queue. No automatic retry after a send may have been dispatched. */
final class DirectMessageRunner {
    final AutomationService s;final Records db;final JSONObject options;final List<String> accounts=new ArrayList<>();final Random random=new Random();
    final boolean dry,retry;final String body;int index,success,failed,unknown,skipped,batchSize,batchDone;
    long attempt=-1,waitUntil,evidenceAt;String account="",verifiedName="";boolean dispatched,identityVerified;int priorBubbles,backs;
    int visualReads,inputCorrections,submitRetries,inputAttempts;long lastSubmitAt;Rect candidate;boolean usersClicked;
    DirectMessageRunner(AutomationService service,JSONObject options){
        this.s=service;this.db=service.db;this.options=options;dry=options.optBoolean("dryRun",true);retry=options.optBoolean("retry");body=options.optString("body").trim();
        MessagePolicy.validate(options.optInt("min"),options.optInt("max"),options.optInt("batchMin"),options.optInt("batchMax"),options.optInt("restMin"),options.optInt("restMax"),options.optInt("daily"));
        if(!dry&&(body.isEmpty()||body.length()>1000))throw new IllegalArgumentException("私信文案无效");
        JSONArray input=options.optJSONArray("accounts");if(input==null||input.length()==0)throw new IllegalArgumentException("没有选择用户");
        Set<String> unique=new LinkedHashSet<>();for(int i=0;i<input.length();i++){String id=MessagePolicy.account(input.optString(i));if(MessagePolicy.validAccount(id))unique.add(id);}accounts.addAll(unique);if(accounts.isEmpty())throw new IllegalArgumentException("没有有效抖音号");
        batchSize=range("batchMin","batchMax");
    }
    int range(String a,String b){return MessagePolicy.between(random,options.optInt(a),options.optInt(b));}
    String summary(){return (dry?"用户检查":"私信")+" · 总 "+accounts.size()+" / 已处理 "+index+" / "+(dry?"可到达 ":"成功 ")+success+" / 失败 "+failed+" / 待核实 "+unknown+" / 跳过 "+skipped;}
    void start(){db.log("[dm] "+summary()+"；"+(dry?"只检查，不填写、不发送":"串行搜索抖音号"));next();}
    void phase(String phase){s.setState("DM_"+phase);s.status(summary()+" · "+phase);}
    boolean waiting(){
        if(!s.state.equals("DM_WAIT"))return false;
        long remaining=waitUntil-SystemClock.elapsedRealtime();if(remaining>0){s.status(summary()+" · 等待 "+((remaining+999)/1000)+" 秒");return true;}next();return true;
    }
    void next(){
        attempt=-1;dispatched=false;identityVerified=false;verifiedName="";evidenceAt=0;backs=0;visualReads=0;inputCorrections=0;submitRetries=0;inputAttempts=0;lastSubmitAt=0;candidate=null;usersClicked=false;s.visualPage=null;
        while(index<accounts.size()){
            account=accounts.get(index);
            if(!dry&&!MessagePolicy.canSelect(account,db.messageState(account),retry)){skipped++;index++;db.log("[dm] 跳过历史成功、待核实或未允许重试的账号");continue;}
            if(!dry&&db.sendsToday()>=options.optInt("daily")){s.finish("达到每日发送尝试上限；"+summary());return;}
            if(!dry){attempt=db.beginMessage(s.taskId,account,body,retry);if(attempt<0){skipped++;index++;continue;}}
            db.log("[dm] 开始第 "+(index+1)+"/"+accounts.size()+" 人 · 抖音号 "+account);
            phase("NAVIGATE");s.relaunchDouyin();return;
        }
        s.finish((dry?"所选用户检查完成；":"私信队列完成；")+summary());
    }
    void result(String state,String reason){
        s.snapshotAll("DM_"+index+"_"+(state.equals("成功")?"SUCCESS":state.equals("待核实")?"UNKNOWN":"FAILED"));
        if(!dry&&attempt>=0)db.messageResult(attempt,account,state,reason);
        if(state.equals("成功"))success++;else if(state.equals("待核实"))unknown++;else failed++;
        db.log("[dm] 第 "+(index+1)+" 人 "+(dry?"检查：":"")+state+" · "+reason);
        attempt=-1;dispatched=false;index++;batchDone++;
        if(index>=accounts.size()){next();return;}
        int seconds=range("min","max");if(batchDone>=batchSize){seconds=Math.max(seconds,range("restMin","restMax"));batchDone=0;batchSize=range("batchMin","batchMax");db.log("[dm] 休息 "+seconds+" 秒");}
        db.log("[dm] 当前用户处理结束；"+seconds+" 秒后继续第 "+(index+1)+"/"+accounts.size()+" 人");
        waitUntil=SystemClock.elapsedRealtime()+seconds*1000L;phase("WAIT");
    }
    void stop(String reason){
        if(!dry&&attempt>=0){db.messageResult(attempt,account,dispatched?"待核实":"失败",dispatched?"已请求发送但任务中断，可能已发送："+reason:"未点击发送："+reason);attempt=-1;}
    }
    String blocked(){
        // After this attempt was dispatched, a real failure icon takes precedence
        // over the generic 'wait for reply' placeholder shown beside it.
        if(dispatched)for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&!isMessageContent(n)&&
            (AutomationService.str(n.getText())+AutomationService.str(n.getContentDescription())).contains("发送失败")){
            for(AccessibilityNodeInfo notice:s.nodes)if(notice.isVisibleToUser()&&!isMessageContent(notice)){
                String detail=MessageSignals.recipientBlock(AutomationService.str(notice.getText()));if(!detail.isEmpty())return detail;
            }
            return "抖音显示本次消息发送失败";
        }
        AccessibilityNodeInfo input=editor();
        if(input!=null&&Build.VERSION.SDK_INT>=26&&input.isShowingHintText()){
            String reason=MessageSignals.recipientBlock(AutomationService.str(input.getText())+AutomationService.str(input.getHintText()));
            if(!reason.isEmpty())return reason;
        }
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&!isMessageContent(n)){
            String reason=MessageSignals.recipientBlock(AutomationService.str(n.getText())+" "+AutomationService.str(n.getContentDescription()));
            if(!reason.isEmpty())return reason;
        }
        // A historical red retry icon may remain in the chat. It is not evidence
        // about this run; only inspect it after this run actually dispatched text.
        if(dispatched)for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&!isMessageContent(n)){
            String text=AutomationService.str(n.getText())+" "+AutomationService.str(n.getContentDescription());
            if(text.contains("发送失败"))return "抖音显示本次消息发送失败";
        }
        return "";
    }
    String restriction(){for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&!isMessageContent(n)){
        String reason=MessageSignals.restriction(AutomationService.str(n.getText())+" "+AutomationService.str(n.getContentDescription()));if(!reason.isEmpty())return reason;
    }return "";}
    void tick(){
        String restriction=restriction();
        if(!restriction.isEmpty()){s.snapshotAll("DM_PLATFORM_RESTRICTION");s.finish("抖音提示“"+restriction+"”，队列停止；请在抖音手动处理后再开始，未处理用户不会自动重发");return;}
        long phaseTimeout=s.state.equals("DM_RESULTS")?45000:s.state.equals("DM_PROFILE")?35000:s.state.equals("DM_CHAT")||s.state.equals("DM_READY")?40000:25000;
        if(SystemClock.elapsedRealtime()-s.stateAt>phaseTimeout){db.log("[dm] 阶段超时："+s.state+"；已等待="+((SystemClock.elapsedRealtime()-s.stateAt)/1000)+"秒；账号="+account+"；主页已核验="+identityVerified+"；发送已派发="+dispatched);result(dispatched?"待核实":"失败",timeoutReason());return;}
        switch(s.state){
            case "DM_NAVIGATE":navigate();break;
            case "DM_INPUT":input();break;
            case "DM_SUBMIT":submit();break;
            case "DM_RESULTS":results();break;
            case "DM_PROFILE":profile();break;
            case "DM_CHAT":chat();break;
            case "DM_READY":ready();break;
            case "DM_VERIFY":verify();break;
        }
    }
    String timeoutReason(){switch(s.state){case "DM_RESULTS":return "没有搜索到抖音号完全一致的用户";case "DM_PROFILE":return "主页抖音号未能核验";case "DM_CHAT":return "没有进入可识别的私信页面";case "DM_VERIFY":return "已点击发送，未能确认本次发送结果；请手动核实，禁止自动重发";default:return "页面等待超时："+s.state+"，未发送";}}
    void navigate(){
        if(s.searchInput()!=null){phase("INPUT");return;}
        // Return out of chats/profiles before using the global search, not in-chat search.
        if(s.findId("msg_et")!=null||s.findId("ttw")!=null||profileAccount()!=null){back();return;}
        AccessibilityNodeInfo search=s.findDescPrefix("搜索");if(search==null)search=s.findText("搜索");
        if(search!=null&&s.rect(search).top<s.screenHeight()*.25f){if(s.click(search))phase("INPUT");return;}back();
    }
    void back(){if(++backs>8){result("失败","无法返回抖音全局搜索页");return;}s.workerBack();s.settle(2200);}
    // Constant kept here to avoid coupling to an Activity back handler.
    static class AccessibilityServiceBack {static final int BACK=android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK;}
    void input(){AccessibilityNodeInfo input=s.searchInput();if(input==null)return;if(!input.isFocused()){s.click(input);return;}Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,account);if(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){phase("SUBMIT");s.settle(2000);}}
    void submit(){
        AccessibilityNodeInfo input=s.searchInput();if(input==null)return;
        if(!account.equals(AutomationService.str(input.getText()))){
            if(inputCorrections++>=2){result("失败","搜索框内容反复变化，已重新填写两次仍不符，未发送");return;}
            db.log("[dm] 搜索框内容与目标账号不符；重新填写 "+inputCorrections+"/2");phase("INPUT");return;
        }
        AccessibilityNodeInfo submit=searchButton();
        if(submit!=null&&clickSearch(submit)){lastSubmitAt=SystemClock.elapsedRealtime();db.log("[dm] 搜索框账号已核验，提交搜索；按钮="+AutomationService.str(submit.getViewIdResourceName())+"；位置="+s.rect(submit)+"；等待用户结果页确认");phase("RESULTS");s.settle(3200);return;}
        if(submit==null&&SystemClock.elapsedRealtime()-lastSubmitAt>1800){
            Rect r=s.rect(input);int width=s.getResources().getDisplayMetrics().widthPixels;
            if(r.right>width*.45f&&r.top<s.screenHeight()*.28f){int x=Math.min(width-8,r.right+Math.max(24,Math.round(width*.08f)));int y=r.centerY();db.log("[dm] 搜索按钮无可读控件，使用搜索框同排右侧坐标兜底；输入框="+r+"；点击="+x+","+y);if(s.tap(x,y)){lastSubmitAt=SystemClock.elapsedRealtime();phase("RESULTS");s.settle(3200);return;}}
        }
        db.log("[dm] 搜索提交等待：输入框="+s.rect(input)+"；按钮="+(submit==null?"未识别":s.rect(submit).toString())+"；重试="+submitRetries);
    }
    AccessibilityNodeInfo searchButton(){
        for(AccessibilityNodeInfo n:s.nodes){Rect r=s.rect(n);if(n.isVisibleToUser()&&!r.isEmpty()&&r.top<s.screenHeight()*.2f&&r.centerX()>s.getResources().getDisplayMetrics().widthPixels*.7f&&
            (AutomationService.str(n.getText()).equals("搜索")||AutomationService.str(n.getContentDescription()).equals("搜索")))return n;}return null;
    }
    boolean clickSearch(AccessibilityNodeInfo button){
        // This Huawei version exposes a non-clickable label. Its clickable ancestor
        // may only focus the search field; tap the verified label itself instead.
        if(button.isClickable())return s.click(button);Rect r=s.rect(button);return s.tap(r.centerX(),r.centerY());
    }
    AccessibilityNodeInfo profileAccount(){for(AccessibilityNodeInfo n:s.nodes){String text=AutomationService.str(n.getText());if(n.isVisibleToUser()&&!AccountMatch.labeled(text).isEmpty())return n;}return null;}
    boolean matchesAccount(AccessibilityNodeInfo node){return account.equals(AccountMatch.labeled(AutomationService.str(node.getText())));}
    void results(){
        if(s.searchInput()==null||!account.equals(AutomationService.str(s.searchInput().getText())))return;
        AccessibilityNodeInfo users=null;for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&AutomationService.str(n.getText()).equals("用户")&&s.rect(n).top<s.screenHeight()*.25f){users=n;break;}
        if(users==null&&SystemClock.elapsedRealtime()-lastSubmitAt>=6000&&submitRetries<2){
            AccessibilityNodeInfo button=searchButton();if(button!=null){Rect r=s.rect(button);if(s.tap(r.centerX(),r.centerY())){submitRetries++;lastSubmitAt=SystemClock.elapsedRealtime();db.log("[dm] 搜索未进入用户结果页；点击已定位的搜索按钮重试 "+submitRetries+"/2，未点击搜索建议");s.settle(3200);}return;}
        }
        if(users!=null&&!selected(users)&&!usersClicked){if(s.click(users)){usersClicked=true;s.settle(2500);}return;}
        AccessibilityNodeInfo match=null;for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&AutomationService.str(n.getText()).startsWith("抖音号")&&matchesAccount(n)){if(match!=null&&!s.rect(match).equals(s.rect(n))){result("失败","多个同号搜索结果，无法唯一定位");return;}match=n;}
        if(match==null){db.log("[dm] 结果页未找到完整账号文本；用户页签="+(users!=null)+"；OCR读取="+visualReads+"；不点击头像/关注按钮");visualResults(users);return;}
        Rect accountBounds=s.rect(match);
        // Select the name/avatar within the same small search card, never the follow button.
        AccessibilityNodeInfo p=match.getParent();int depth=0;boolean clicked=false;
        while(p!=null&&depth++<4){
            List<AccessibilityNodeInfo> children=new ArrayList<>();AutomationService.walk(AccessibilityNodeInfo.obtain(p),children);
            for(AccessibilityNodeInfo n:children){String id=AutomationService.str(n.getViewIdResourceName()),t=AutomationService.str(n.getText());Rect r=s.rect(n);
                if(n.isVisibleToUser()&&r.bottom<=accountBounds.bottom+8&&r.top>=accountBounds.top-s.screenHeight()*.16f&&(id.endsWith("/avatar")||id.endsWith("/user_avatar")||id.endsWith("/user_name")||id.endsWith("/nickname")||id.endsWith("/title"))&&!t.equals("关注")&&!r.isEmpty()){clicked=s.click(n);if(clicked)break;}}
            for(AccessibilityNodeInfo n:children)n.recycle();AccessibilityNodeInfo next=clicked?null:p.getParent();p.recycle();p=next;if(clicked)break;
        }if(p!=null)p.recycle();
        if(!clicked){ // ID-free fallback: click the verified account's small clickable row, then verify again on profile.
            p=match.getParent();for(int i=0;p!=null&&i<3;i++){AccessibilityNodeInfo next=p.getParent();Rect r=s.rect(p);if(p.isClickable()&&r.height()<s.screenHeight()*.25f&&r.width()>s.getResources().getDisplayMetrics().widthPixels*.5f)clicked=s.click(p);p.recycle();p=next;if(clicked)break;}if(p!=null)p.recycle();
        }
        if(!clicked){db.log("[dm] 结果卡片控件不可点击，坐标点击核验账号文本；位置="+accountBounds);clicked=s.tap(accountBounds.centerX(),accountBounds.centerY());}
        if(clicked){s.highlightMatch(accountBounds);db.log("[dm] 搜索结果抖音号完整匹配，进入主页二次核验；点击位置="+accountBounds.centerX()+","+accountBounds.centerY());phase("PROFILE");s.settle(2600);}
        else db.log("[dm] 搜索结果完整账号已找到，但所有控件/坐标点击均未接受；位置="+accountBounds+"；父级点击次数="+depth);
    }
    void visualResults(AccessibilityNodeInfo users){
        if(users==null)return;
        if(s.visualPage==null||SystemClock.elapsedRealtime()-s.visualAt>3000){s.visualPage=null;s.requestScreen();return;}
        FilterPage page=s.visualPage;s.visualPage=null;visualReads++;
        StringBuilder readable=new StringBuilder();for(FilterPage.Item item:page.items)if(item.text.startsWith("抖音号")&&readable.length()<500)readable.append(item.text).append(" | ");
        db.log("[dm] 搜索识别帧="+visualReads+"；目标="+account+"；可读账号="+(readable.length()==0?"无":readable.toString()));
        FilterPage.Item match=AccountMatch.visual(page,account,s.rect(users).bottom,s.screenHeight());
        boolean truncated=false;
        if(match==null){match=AccountMatch.truncatedCandidate(page,s.rect(users).bottom,s.screenHeight());truncated=match!=null;}
        if(match==null){candidate=null;if(visualReads>=3&&SystemClock.elapsedRealtime()-s.stateAt>=7000)result("失败","当前用户搜索结果未识别到完整匹配的抖音号，未进入主页、未发送");else s.settle(1800);return;}
        Rect box=new Rect(match.left,match.top,match.right,match.bottom);
        if(candidate==null||Math.abs(candidate.centerY()-box.centerY())>12){candidate=box;s.settle(1800);return;}
        // Two fresh frames agree. Tap only the account text, never the follow button.
        s.highlightMatch(box);db.log(truncated?"[dm] 唯一标红账号被省略，进入候选主页核对完整抖音号；尚未确认身份":"[dm] 本机文字识别连续两次匹配完整抖音号；进入主页二次核验");
        if(s.tap(box.centerX(),box.centerY())){phase("PROFILE");s.settle(2800);}
    }
    boolean selected(AccessibilityNodeInfo n){boolean selected=n.isSelected()||n.isChecked();AccessibilityNodeInfo p=n.getParent();if(p!=null){selected|=p.isSelected()||p.isChecked();p.recycle();}return selected;}
    void profile(){
        if(s.searchInput()!=null){db.log("[dm] DM_PROFILE 仍处于搜索页，等待主页跳转；搜索框仍存在");return;}AccessibilityNodeInfo id=profileAccount();if(id==null){db.log("[dm] DM_PROFILE 未读取到抖音号文本；可见身份控件="+chatTitleSummary());return;}
        if(!matchesAccount(id)){result("失败","主页抖音号与目标不完全一致，已拦截，未发送");return;}
        db.log("[dm] 主页抖音号二次核验通过");
        identityVerified=true;AccessibilityNodeInfo name=profileName();
        if(name==null){db.log("[dm] 主页账号核验通过，但昵称控件尚不可读；继续等待，不进入聊天");return;}
        verifiedName=MessageIdentity.normalize(AutomationService.str(name.getText()));
        db.log("[dm] 主页昵称="+verifiedName+"；控件="+name.getViewIdResourceName());
        AccessibilityNodeInfo dm=dmEntry();
        if(dm!=null&&s.click(dm)){db.log("[dm] 已点击主页私信入口；控件="+AutomationService.str(dm.getViewIdResourceName())+"；文字="+AutomationService.str(dm.getText())+"；描述="+AutomationService.str(dm.getContentDescription())+"；位置="+s.rect(dm)+"；等待聊天输入框");phase("CHAT");s.settle(2800);}
        else db.log("[dm] 主页核验通过但未找到私信入口；已扫描可见控件="+visibleActionSummary());
    }
    AccessibilityNodeInfo dmEntry(){
        AccessibilityNodeInfo exact=s.findText("私信");if(exact==null)exact=s.findDescPrefix("私信");if(exact!=null)return exact;
        AccessibilityNodeInfo best=null;int bestRank=-1;int h=s.screenHeight();
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&!s.rect(n).isEmpty()){
            String text=AutomationService.str(n.getText()),desc=AutomationService.str(n.getContentDescription()),all=text+" "+desc;
            if(!(all.contains("私信")||all.contains("发消息")||all.equals("消息")))continue;
            Rect r=s.rect(n);if(r.top<h*.28f)continue;int rank=(n.isClickable()?100:0)+(all.contains("私信")?30:10);if(rank>bestRank){best=n;bestRank=rank;}
        }
        return best;
    }
    String visibleActionSummary(){StringBuilder b=new StringBuilder();for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&s.rect(n).top>s.screenHeight()*.25f&&s.rect(n).top<s.screenHeight()*.9f){String t=AutomationService.str(n.getText()),d=AutomationService.str(n.getContentDescription());if((t+d).contains("私")||(t+d).contains("消息"))b.append('[').append(t).append('|').append(d).append('|').append(s.rect(n)).append(']');}return b.length()==0?"无":b.toString();}
    String visibleInputSummary(){StringBuilder b=new StringBuilder();for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()){String id=AutomationService.str(n.getViewIdResourceName()),t=AutomationService.str(n.getText()),d=AutomationService.str(n.getContentDescription());if(id.contains("msg")||t.contains("消息")||d.contains("消息"))b.append('[').append(id).append('|').append(t).append('|').append(d).append('|').append(s.rect(n)).append(']');}return b.length()==0?"无":b.toString();}
    String visibleSendSummary(){StringBuilder b=new StringBuilder();for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()){String t=AutomationService.str(n.getText()),d=AutomationService.str(n.getContentDescription());if((t+d).contains("发送"))b.append('[').append(t).append('|').append(d).append('|').append(s.rect(n)).append(']');}return b.length()==0?"无":b.toString();}
    String chatTitleSummary(){StringBuilder b=new StringBuilder();for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&s.rect(n).top<s.screenHeight()*.25f&&AutomationService.str(n.getClassName()).endsWith("TextView")){b.append('[').append(AutomationService.str(n.getText())).append('|').append(AutomationService.str(n.getViewIdResourceName())).append('|').append(s.rect(n)).append(']');}return b.length()==0?"无":b.toString();}
    AccessibilityNodeInfo editor(){return s.findId("msg_et");}
    String draft(AccessibilityNodeInfo input){
        if(input==null)return "";
        // Android may expose an empty editor's placeholder as getText().
        if(Build.VERSION.SDK_INT>=26&&input.isShowingHintText())return "";
        return AutomationService.str(input.getText());
    }
    AccessibilityNodeInfo profileName(){
        AccessibilityNodeInfo found=null;
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&MessageIdentity.profileName(AutomationService.str(n.getViewIdResourceName()),AutomationService.str(n.getText()),AutomationService.str(n.getContentDescription()))){
            if(found!=null&&!MessageIdentity.normalize(AutomationService.str(found.getText())).equals(MessageIdentity.normalize(AutomationService.str(n.getText()))))return null;found=n;
        }
        return found;
    }
    AccessibilityNodeInfo chatTitle(){
        AccessibilityNodeInfo found=null;
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&MessageIdentity.chatTitle(AutomationService.str(n.getViewIdResourceName()),s.rect(n).top,s.rect(n).bottom,s.screenHeight())){
            if(found!=null&&!MessageIdentity.normalize(AutomationService.str(found.getText())).equals(MessageIdentity.normalize(AutomationService.str(n.getText()))))return null;found=n;
        }
        if(found!=null)return found;
        // Some Compose builds remove vw_/tto IDs. Use only a single short header
        // TextView as fallback; message bubbles are below the header band.
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&s.rect(n).top>=0&&s.rect(n).bottom<s.screenHeight()*.25f&&AutomationService.str(n.getClassName()).endsWith("TextView")){
            String text=AutomationService.str(n.getText());if(text.trim().isEmpty()||text.equals("关注")||text.equals("更多")||text.equals("消息")||text.matches(".*未读.*"))continue;
            if(found!=null&&!MessageIdentity.normalize(AutomationService.str(found.getText())).equals(MessageIdentity.normalize(text)))return null;found=n;
        }
        return found;
    }
    boolean sameChat(){AccessibilityNodeInfo title=chatTitle();return title!=null&&MessageIdentity.same(identityVerified,verifiedName,AutomationService.str(title.getText()));}
    void chat(){
        if(!identityVerified)return;
        String blocked=blocked();if(!blocked.isEmpty()){result("失败",blocked+"，未发送");return;}
        AccessibilityNodeInfo input=editor();if(input==null){db.log("[dm] 聊天页暂未找到 msg_et 输入框；可见输入相关控件="+visibleInputSummary());return;}
        if(!sameChat()){
            AccessibilityNodeInfo title=chatTitle();
            if(title==null&&SystemClock.elapsedRealtime()-s.stateAt<8000){s.settle(800);return;}
            db.log("[dm] 聊天身份未匹配；主页昵称="+verifiedName+"；聊天标题="+(title==null?"未识别":AutomationService.str(title.getText()))+"；候选="+chatTitleSummary());
            result("失败","聊天身份核验失败；主页昵称="+verifiedName+"；聊天标题="+(title==null?"未识别":AutomationService.str(title.getText()))+"；未发送");return;
        }
        db.log("[dm] 聊天标题与已核验主页昵称一致；控件="+chatTitle().getViewIdResourceName());
        if(dry){result("成功","抖音号与主页核对一致，已到聊天页；未填写、未发送");return;}
        if(!input.isEnabled()||!input.isEditable()){result("失败","聊天输入框不可编辑，可能需对方回复");return;}
        if(!input.isFocused()){db.log("[dm] 点击聊天输入框，等待焦点与键盘；控件="+AutomationService.str(input.getViewIdResourceName())+"；可编辑="+input.isEditable()+"；启用="+input.isEnabled()+"；位置="+s.rect(input));s.click(input);return;}
        String existing=draft(input);if(!existing.isEmpty()){result("失败","聊天输入框有原有草稿，请先手动处理，未覆盖");return;}
        priorBubbles=outgoingCount(body);if(priorBubbles<0){result("失败","无法核验历史消息，未发送");return;}
        Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,body);
        inputAttempts++;if(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)){db.log("[dm] 已填写私信文案（"+body.length()+" 字），等待发送按钮；输入尝试="+inputAttempts+"；输入后文本长度="+AutomationService.str(input.getText()).length());phase("READY");s.settle(2200);}
        else {db.log("[dm] 输入框未接受文字；尝试="+inputAttempts+"；可编辑="+input.isEditable()+"；启用="+input.isEnabled()+"；焦点="+input.isFocused()+"；重新获取焦点");s.click(input);if(inputAttempts>=3)result("失败","聊天输入框连续3次拒绝写入，未发送；控件="+AutomationService.str(input.getViewIdResourceName()));}
    }
    void ready(){
        String blocked=blocked();if(!blocked.isEmpty()){result("失败",blocked+"，未发送");return;}
        if(!sameChat()){db.log("[dm] 发送前聊天身份未确认；主页="+verifiedName+"；聊天候选="+chatTitleSummary());result("失败","发送前聊天对象发生变化或无法确认，未发送");return;}
        AccessibilityNodeInfo input=editor();if(input==null){db.log("[dm] READY 阶段输入框消失；可见输入相关控件="+visibleInputSummary());return;}
        if(!body.equals(AutomationService.str(input.getText()))){db.log("[dm] READY 阶段输入框文本尚未稳定；期望长度="+body.length()+"；实际长度="+AutomationService.str(input.getText()).length());return;}
        AccessibilityNodeInfo button=null;for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&n.isEnabled()&&(MessageSignals.sendButton(AutomationService.str(n.getContentDescription()))||MessageSignals.sendButton(AutomationService.str(n.getText())))){button=n;break;}
        if(button==null){db.log("[dm] 文案已在输入框，但未找到发送按钮；输入框="+s.rect(input)+"；可见发送相关控件="+visibleSendSummary());if(Build.VERSION.SDK_INT>=30&&SystemClock.elapsedRealtime()-s.stateAt>7000){db.log("[dm] 发送按钮仍不可读，尝试一次输入法回车动作（Android 11+），不重复派发");if(input.performAction(0x02000000)){if(!db.reserveSend(attempt,account,options.optInt("daily"))){s.finish("达到今日发送尝试上限或账号状态已变化，未发送，队列停止");return;}dispatched=true;phase("VERIFY");s.settle(2800);}}return;}
        // Mark before dispatch, including crash window, to avoid duplicate sends after restart.
        if(!db.reserveSend(attempt,account,options.optInt("daily"))){s.finish("达到今日发送尝试上限或账号状态已变化，未发送，队列停止");return;}
        dispatched=true;
        db.log("[dm] 已唤起发送按钮，执行一次点击");
        if(!s.click(button)){dispatched=false;result("失败","发送按钮未接受点击，未发送");return;}
        phase("VERIFY");s.settle(2800);
    }
    boolean isMessageContent(AccessibilityNodeInfo n){AccessibilityNodeInfo p=AccessibilityNodeInfo.obtain(n);try{for(int i=0;p!=null&&i<8;i++){String id=AutomationService.str(p.getViewIdResourceName());if(id.endsWith("/content_layout")||id.endsWith("/msg_et"))return true;AccessibilityNodeInfo next=p.getParent();p.recycle();p=next;}return false;}finally{if(p!=null)p.recycle();}}
    int outgoingCount(String expected){
        int count=0;Set<String> boxes=new HashSet<>();int width=s.getResources().getDisplayMetrics().widthPixels;
        for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&AutomationService.str(n.getViewIdResourceName()).endsWith("/content_layout")){
            Rect r=s.rect(n);AccessibilityNodeInfo parent=n.getParent();boolean right=r.centerX()>width*.5f;
            if(parent!=null)parent.recycle(); // right-aligned message container; exclude the editable draft.
            if(!right)continue;List<AccessibilityNodeInfo> descendants=new ArrayList<>();AutomationService.walk(AccessibilityNodeInfo.obtain(n),descendants);boolean same=false;
            for(AccessibilityNodeInfo child:descendants){if(expected.equals(AutomationService.str(child.getText())))same=true;child.recycle();}
            if(same&&boxes.add(r.toShortString()))count++;
        }return count;
    }
    void verify(){
        String error=blocked();if(!error.isEmpty()&&!error.contains("回复")){result("失败",error);return;}
        if(!sameChat()){result("待核实","已点击发送后聊天页变化，无法确认结果，请手动核实");return;}
        AccessibilityNodeInfo input=editor();boolean draftEmpty=input!=null&&draft(input).isEmpty();
        boolean newBubble=outgoingCount(body)>priorBubbles;
        boolean sending=false;for(AccessibilityNodeInfo n:s.nodes)if(n.isVisibleToUser()&&(AutomationService.str(n.getClassName()).endsWith("ProgressBar")||AutomationService.str(n.getContentDescription()).contains("发送中")))sending=true;
        if(newBubble&&draftEmpty&&!sending){if(evidenceAt==0)evidenceAt=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-evidenceAt>=8000){result("成功","聊天页出现本次新发消息、输入框清空，持续8秒无发送中或失败提示；不表示对方已读");}}
        else evidenceAt=0;
        if(!error.isEmpty()&&error.contains("回复")&&!newBubble){result("待核实","对方回复后可发，但未能区分本次与历史消息，请手动核实");}
    }
}
