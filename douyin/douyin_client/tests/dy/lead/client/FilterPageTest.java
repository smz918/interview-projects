package dy.lead.client;
import java.util.*;
/** Run with javac/java; verifies group isolation and resolution/font independence. */
public class FilterPageTest {
    static void require(boolean condition,String msg){if(!condition)throw new AssertionError(msg);}
    static FilterPage.Item item(String text,int y,int height,double scale,boolean selected){return new FilterPage.Item(text,(int)(20*scale),(int)(y*scale),(int)(150*scale),(int)((y+height)*scale),selected);}
    public static void main(String[] args) throws Exception {
        for(double scale:new double[]{0.75,1,1.5,2,3}){
            List<FilterPage.Item> items=new ArrayList<>();
            items.add(item("视频",80,30,scale,false)); // navigation tab must never be selected as content form
            for(int group=0;group<6;group++){
                items.add(item(FilterPage.GROUPS[group],200+group*120,30,scale,false));
                items.add(item(group==0?"综合排序":"不限",240+group*120,35,scale,true));
                if(group==4)items.add(item("视频",280+group*120,30,scale,false));
            }
            items.add(item("查看结果",1000,40,scale,false));
            FilterPage page=new FilterPage(items,false);require(page.isPanel(),"panel scale "+scale);
            for(int g=1;g<6;g++)require(page.option(g,"不限").y()==item("",240+g*120,35,scale,true).y(),"wrong 不限 group "+g);
            require(page.option(4,"视频").top>700*scale,"selected navigation tab");
            require(page.option(0,"最多评论")==null,"unsupported option must fail");
            items.add(new FilterPage.Item("视频",(int)(200*scale),(int)(760*scale),(int)(300*scale),(int)(790*scale),false));
            require(page.option(4,"视频")==null,"ambiguous matches must fail");
        }
        require(!new FilterPage(Arrays.asList(item("查看结果",1000,40,1,false)),true).isPanel(),"footer alone is not panel");
        require(FilterPage.normalize("1 – 5 分钟").equals("1-5分钟"),"normalize duration");
        require(FilterPage.isDefaultUnfiltered(3,"不限"),"missing scope group is safe when default is unlimited");
        require(!FilterPage.isDefaultUnfiltered(3,"最近看过"),"requested scope must still be located");
        require(!FilterPage.isDefaultUnfiltered(0,"不限"),"default sort still maps to 综合排序");
        List<FilterPage.Item> ocr=new ArrayList<>();ocr.add(item("摸索范围",700,30,1,false));ocr.add(item("最近看过",770,30,1,true));ocr.add(item("内容形式",840,30,1,false));ocr.add(item("查看结果",1000,30,1,false));
        FilterPage visual=new FilterPage(ocr,true);require(visual.isPanel(),"OCR heading tolerance");require(visual.option(3,"最近看过").selected,"OCR selection after click");
        require(visual.option(3,"最近看错").selected,"one OCR option substitution is accepted");
        List<FilterPage.Item> ocrOption=new ArrayList<>();
        ocrOption.add(item("排序依据",200,30,1,false));
        ocrOption.add(item("最多点攒",240,30,1,true));
        ocrOption.add(item("发布时间",500,30,1,false));
        ocrOption.add(item("查看结果",1000,30,1,false));
        FilterPage optionPage=new FilterPage(ocrOption,true);
        require(optionPage.option(0,"最多点赞")!=null&&optionPage.option(0,"最多点赞").selected,
                "one OCR option substitution must be accepted");
        List<FilterPage.Item> ocrSeparator=new ArrayList<>();
        ocrSeparator.add(item("发布时间",500,30,1,false));
        ocrSeparator.add(item("|一周内",560,30,1,true));
        ocrSeparator.add(item("视频时长",640,30,1,false));
        ocrSeparator.add(item("查看结果",1000,30,1,false));
        FilterPage separatorPage=new FilterPage(ocrSeparator,true);
        require(separatorPage.option(1,"一周内")!=null&&separatorPage.option(1,"一周内").selected,
                "OCR row separator must not hide selected option");
        List<FilterPage.Item> dates=new ArrayList<>();
        dates.add(item("发布时间",500,30,1,false));dates.add(item("一天内",560,30,1,false));
        dates.add(new FilterPage.Item("一周內",300,560,450,590,true));
        dates.add(item("视频时长",640,30,1,false));dates.add(item("查看结果",1000,30,1,false));
        require(new FilterPage(dates,true).option(1,"一周内").selected,"Traditional OCR 内 must not collide with 一天内");
        dates.remove(2);require(new FilterPage(dates,true).option(1,"一周内")==null,"Known 一天内 must never substitute missing 一周内");
        List<FilterPage.Item> ocrHeading=new ArrayList<>();
        ocrHeading.add(item("发布时闻",500,30,1,false));
        ocrHeading.add(item("一周内",560,30,1,true));
        ocrHeading.add(item("查看结果",1000,30,1,false));
        FilterPage headingPage=new FilterPage(ocrHeading,true);
        require(headingPage.option(1,"一周内")!=null&&headingPage.option(1,"一周内").selected,
                "one OCR heading substitution must remain matchable");
        for(String path:args){
            javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            org.w3c.dom.NodeList entries=factory.newDocumentBuilder().parse(new java.io.File(path)).getElementsByTagName("text");List<FilterPage.Item> real=new ArrayList<>();
            for(int i=0;i<entries.getLength();i++){org.w3c.dom.Element e=(org.w3c.dom.Element)entries.item(i);java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]").matcher(e.getAttribute("bounds"));if(m.matches())real.add(new FilterPage.Item(e.getAttribute("value"),Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),Integer.parseInt(m.group(3)),Integer.parseInt(m.group(4)),Boolean.parseBoolean(e.getAttribute("selectedColor"))));}
            FilterPage captured=new FilterPage(real,true);require(captured.isPanel(),"real panel "+path);
            if(path.replace('\\','/').contains("filter_latest")||path.contains("1789868308420")||path.contains("1789868432116")){
                require(captured.option(1,"一周内")!=null&&captured.option(1,"一周内").selected,"latest OCR date regression "+path);
            }else{
                require(captured.option(3,"最近看过")!=null&&captured.option(3,"最近看过").selected,"real OCR regression "+path);
            }
        }
        System.out.println("PASS: 5 screen scales, OCR separator/heading tolerance, duplicate labels, wrong tab, ambiguity, missing options, panel gate");
    }
}
