package dy.lead.client;
import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.util.regex.*;

public final class MessageIdentityTest {
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static String read(String path,boolean profile,boolean legacy)throws Exception{
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        NodeList nodes=f.newDocumentBuilder().parse(new File(path)).getElementsByTagName("node");String result="";
        for(int i=0;i<nodes.getLength();i++){
            Element n=(Element)nodes.item(i);if(!n.getAttribute("visible").equals("true"))continue;
            String id=n.getAttribute("id"),text=n.getAttribute("text");
            if(legacy)id=id.replace("/tlq","/ttw").replace("/vw_","/vw+");
            Matcher m=Pattern.compile("-?\\d+").matcher(n.getAttribute("bounds"));int[] b=new int[4];for(int j=0;j<4&&m.find();j++)b[j]=Integer.parseInt(m.group());
            if(profile?MessageIdentity.profileName(id,text,n.getAttribute("desc")):MessageIdentity.chatTitle(id,b[1],b[3],2239))result=text;
        }return result;
    }
    public static void main(String[] args)throws Exception{
        for(boolean legacy:new boolean[]{false,true}){
            String profile=read(args[0],true,legacy),chat=read(args[1],false,legacy);
            check(profile.equals("Stewie Griffin"),"Real profile XML nickname");check(MessageIdentity.same(true,profile,chat),"New and legacy XML title must match");
            check(!MessageIdentity.same(false,profile,chat),"Account must first be verified");
            check(!MessageIdentity.same(true,profile,"Someone Else"),"Never send to a different title");
        }
        check(!MessageIdentity.chatTitle("pkg:id/content_layout",120,180,2239),"Message text is not identity proof");
        check(!MessageIdentity.chatTitle("pkg:id/vw_",1700,1800,2239),"Ignore same text outside header");
        check(MessageIdentity.chatTitle("pkg:id/vw3",144,192,2239),"Generated vw suffix is a valid chat header alias");
        check(!MessageIdentity.same(true,"",""),"Empty titles never authorize sending");
        for(int i=2;i+1<args.length;i+=2){
            String profile=read(args[i],true,false),chat=read(args[i+1],false,false);
            check(!profile.isEmpty(),"Diagnostic profile nickname must be readable: "+args[i]);
            check(MessageIdentity.same(true,profile,chat),"Diagnostic chat must match verified profile: "+args[i+1]);
        }
        System.out.println("PASS: actual Huawei profile/chat XML, legacy aliases, account verification, wrong/empty/body title rejection");
    }
}
