from reportlab.lib.pagesizes import A4
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_RIGHT
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, KeepTogether
import os

OUT_DIR = os.path.join('output', 'pdf')
os.makedirs(OUT_DIR, exist_ok=True)
OUT = os.path.join(OUT_DIR, '\u5b59\u660e\u73e0-\u9500\u552e\u4e0e\u8bfe\u7a0b\u987e\u95ee\u7b80\u5386.pdf')
pdfmetrics.registerFont(TTFont('MicrosoftYaHei', r'C:\\Windows\\Fonts\\msyh.ttc', subfontIndex=0))
pdfmetrics.registerFont(TTFont('MicrosoftYaHei-Bold', r'C:\\Windows\\Fonts\\msyhbd.ttc', subfontIndex=0))

NAVY = colors.HexColor('#1F3B64')
BLUE = colors.HexColor('#2F6FB0')
TEXT = colors.HexColor('#252B33')
MUTED = colors.HexColor('#6B7280')
LINE = colors.HexColor('#D9E2EC')
LIGHT = colors.HexColor('#F4F7FA')

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name='Name', fontName='MicrosoftYaHei-Bold', fontSize=24, leading=28, textColor=NAVY, spaceAfter=2))
styles.add(ParagraphStyle(name='Target', fontName='MicrosoftYaHei', fontSize=10.5, leading=15, textColor=BLUE))
styles.add(ParagraphStyle(name='Contact', fontName='MicrosoftYaHei', fontSize=9.2, leading=14, textColor=TEXT, alignment=TA_RIGHT))
styles.add(ParagraphStyle(name='Section', fontName='MicrosoftYaHei-Bold', fontSize=12.5, leading=17, textColor=NAVY, spaceBefore=6, spaceAfter=3))
styles.add(ParagraphStyle(name='Edu', fontName='MicrosoftYaHei', fontSize=9.6, leading=14, textColor=TEXT, spaceAfter=1))
styles.add(ParagraphStyle(name='Role', fontName='MicrosoftYaHei-Bold', fontSize=10.2, leading=14, textColor=TEXT, spaceBefore=3, spaceAfter=1))
styles.add(ParagraphStyle(name='Date', fontName='MicrosoftYaHei', fontSize=8.8, leading=13, textColor=MUTED, alignment=TA_RIGHT))
styles.add(ParagraphStyle(name='Body', fontName='MicrosoftYaHei', fontSize=9.4, leading=14.2, textColor=TEXT, leftIndent=11, firstLineIndent=-8, spaceAfter=1.2))
styles.add(ParagraphStyle(name='SmallBody', fontName='MicrosoftYaHei', fontSize=9.1, leading=13.3, textColor=TEXT, leftIndent=11, firstLineIndent=-8, spaceAfter=1))
styles.add(ParagraphStyle(name='Footer', fontName='MicrosoftYaHei', fontSize=7.8, leading=10, textColor=MUTED, alignment=TA_RIGHT))

def P(text, style):
    return Paragraph(text, styles[style])

def section(title):
    return [Spacer(1, 2), P(title, 'Section'), Table([['']], colWidths=[170*mm], rowHeights=[0.7*mm], style=TableStyle([('BACKGROUND',(0,0),(-1,-1),BLUE)])), Spacer(1, 2)]

doc = SimpleDocTemplate(OUT, pagesize=A4, rightMargin=18*mm, leftMargin=18*mm, topMargin=14*mm, bottomMargin=12*mm)
story = []
header = Table([
    [P('孙明珠', 'Name'), P('合肥  |  17681082857<br/>sunxiaoming1015@163.com', 'Contact')],
    [P('销售顾问 / 课程顾问<br/><font color="#6B7280">硕士在读（2027届）</font>', 'Target'), '']
], colWidths=[98*mm, 72*mm])
header.setStyle(TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'), ('LEFTPADDING',(0,0),(-1,-1),0), ('RIGHTPADDING',(0,0),(-1,-1),0), ('TOPPADDING',(0,0),(-1,-1),0), ('BOTTOMPADDING',(0,0),(-1,-1),0), ('SPAN',(0,1),(1,1))]))
story += [header, Spacer(1,5)]

story += section('教育经历')
for school, degree, dates in [('安徽大学', '硕士  ·  人工智能', '2024.09 - 2027.06'), ('阜阳师范大学', '本科  ·  软件工程', '2019.09 - 2023.06')]:
    story.append(Table([[P(f'<b>{school}</b>  ·  {degree}', 'Edu'), P(dates, 'Date')]], colWidths=[128*mm,42*mm], style=TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),0),('RIGHTPADDING',(0,0),(-1,-1),0)])))

story += section('工作经历')
story += [KeepTogether([
    Table([[P('健康产品销售顾问  |  面向中老年客户', 'Role'), P('单位与时间：待补充', 'Date')]], colWidths=[128*mm,42*mm], style=TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),0),('RIGHTPADDING',(0,0),(-1,-1),0)])),
    P('•  面向中老年客户介绍营养保健产品，围绕日常健康需求、使用场景与预算进行耐心沟通，建立信任关系。', 'Body'),
    P('•  负责客户咨询、产品推荐、成交跟进、回访与售后维护，持续记录客户需求并挖掘复购机会。', 'Body'),
    P('•  擅长用通俗清晰的方式讲解产品特点，能够处理价格、效果、使用方式等常见异议，推动客户做出合适选择。', 'Body'),
]), KeepTogether([
    Table([[P('课程顾问 / 招生顾问  |  教育培训机构', 'Role'), P('单位与时间：待补充', 'Date')]], colWidths=[128*mm,42*mm], style=TableStyle([('VALIGN',(0,0),(-1,-1),'TOP'),('LEFTPADDING',(0,0),(-1,-1),0),('RIGHTPADDING',(0,0),(-1,-1),0)])),
    P('•  面向家长和学生介绍课程体系、学习规划与服务流程，快速识别需求并匹配合适的辅导方案。', 'Body'),
    P('•  负责电话、微信及面谈咨询，完成线索跟进、试听邀约、报名转化、到课提醒和续报沟通。', 'Body'),
    P('•  个人招生 / 报名业绩位居团队第一，具备较强的目标意识、沟通能力和持续跟进能力。', 'Body'),
])]

story += section('核心能力')
story += [P('•  客户沟通与需求挖掘：善于与中老年客户、家长及学生建立信任，能够耐心倾听并提炼真实需求。', 'SmallBody'), P('•  销售与转化：熟悉电话、微信、面谈等沟通方式，能够完成产品 / 课程介绍、异议处理、成交跟进与客户维护。', 'SmallBody'), P('•  办公与数据：熟练使用 Word、Excel、PPT，能够整理客户信息、跟踪跟进进度并进行基础数据统计。', 'SmallBody')]
story += section('荣誉证书')
story += [P('•  全国大学生数学建模比赛二等奖　　•  校级奖学金', 'SmallBody')]
story += section('自我评价')
story += [P('沟通耐心，亲和力强，能够适应以结果为导向的工作节奏；对客户需求敏感，重视服务体验和长期关系维护；学习能力强，愿意持续提升销售方法、课程咨询和客户运营能力。', 'SmallBody'), Spacer(1,5), P('注：原附件未提供具体单位名称与任职时间，已保留为可补充项。', 'Footer')]

def add_bg(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(LIGHT)
    canvas.rect(0, A4[1]-8*mm, A4[0], 8*mm, fill=1, stroke=0)
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.5)
    canvas.line(18*mm, 9*mm, A4[0]-18*mm, 9*mm)
    canvas.setFillColor(MUTED)
    canvas.setFont('MicrosoftYaHei', 7.5)
    canvas.drawString(18*mm, 5.4*mm, '个人简历')
    canvas.drawRightString(A4[0]-18*mm, 5.4*mm, '孙明珠')
    canvas.restoreState()

doc.build(story, onFirstPage=add_bg, onLaterPages=add_bg)
print(OUT)
