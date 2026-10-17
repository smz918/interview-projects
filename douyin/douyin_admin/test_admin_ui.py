import json
from pathlib import Path
from playwright.sync_api import sync_playwright

root=Path(__file__).parent
html=(root/'central_snapshot/admin.html').read_text(encoding='utf-8').replace('</body>','<script src="/douyin/admin-douyin.js"></script></body>')
script=(root/'api/admin-douyin.js').read_text(encoding='utf-8')
users=[dict(id=1,phone='13900009999',source='抖音',status='pending',note='<img src=x onerror=alert(1)>',password='YOUR_PASSWORD_HERE',referrer='推荐人甲',created_at='2026-09-20T06:00:00Z',last_login='2026-09-20T07:00:00Z',last_used='2026-09-20T08:00:00Z',expires_at='',devices=[])]
with sync_playwright() as p:
    browser=p.chromium.launch(channel='msedge',headless=True)
    page=browser.new_page(viewport={'width':1440,'height':1000},accept_downloads=True);errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    def handle(route):
        path=route.request.url.split('http://fixture.test',1)[-1]
        if path=='/':return route.fulfill(body=html,content_type='text/html')
        if path.startswith('/douyin/admin-douyin.js'):return route.fulfill(body=script,content_type='application/javascript')
        if path=='/api/auth/verify':data={'is_admin':True}
        elif path=='/api/auth/summary':data={'users_total':20,'users_active':18,'login_today':2,'activity_today':0}
        elif path=='/douyin/api/v1/management/users':data=users
        elif path=='/douyin/api/v1/management/users/1':
            users[0].update(route.request.post_data_json);data={'ok':True}
        elif path=='/douyin/api/v1/management/users/1/tasks':data=[{'task_id':'1791842400000','size':24,'updated_at':'2026-10-12T04:00:00Z'}]
        elif path=='/douyin/api/v1/management/users/1/tasks/1791842400000/logs':return route.fulfill(body='fixture task log',content_type='text/plain')
        elif path=='/douyin/api/v1/management/users/1/diagnostics':data=[{'task_id':'1791842400000','size':512,'updated_at':'2026-10-12T04:00:00Z'}]
        elif path=='/douyin/api/v1/management/users/1/tasks/1791842400000/diagnostics':return route.fulfill(body=b'PK\\x03\\x04fixture',content_type='application/zip')
        else:data=[]
        route.fulfill(json=data)
    page.route('**/*',handle)
    page.add_init_script("localStorage.setItem('central_auth_token','fixture-token')")
    page.goto('http://fixture.test/');page.locator('#dyNav').click();page.wait_for_function("document.querySelector('#dyPending').textContent==='1'")
    assert page.locator('#usersView').is_hidden()
    assert page.locator('#dyRows img').count()==0
    assert 'test<&>password' in page.locator('#dyRows').inner_text()
    page.once('dialog',lambda d:d.accept('1'))
    with page.expect_download() as download:
        page.locator('[data-dy-tasklogs]').click()
    assert download.value.suggested_filename=='13900009999_1791842400000.log'
    page.once('dialog',lambda d:d.accept('1'))
    with page.expect_download() as diagnostic_download:
        page.locator('[data-dy-diagnostics]').click()
    assert diagnostic_download.value.suggested_filename=='13900009999_1791842400000_诊断.zip'
    assert page.locator('#dyView th').all_text_contents()==['序号','用户名','密码','备注','推荐人','来源','状态','使用时间','注册时间','最近登录','最近使用','设备 / 版本','操作']
    page.on('dialog',lambda d:d.accept())
    page.locator('[data-dy-toggle]').click();page.wait_for_function("document.querySelector('#dyActive').textContent==='1'")
    page.locator('[data-dy-edit]').click();assert page.locator('#dyDrawer').is_visible();page.locator('#dyNote').fill('回归用户');page.locator('#dyReferrer').fill('推荐人乙');page.locator('#dyForm button.primary').click()
    page.wait_for_function("document.querySelector('#dyRows').textContent.includes('回归用户')")
    assert users[0]['referrer']=='推荐人乙'
    page.locator('#dyQuery').fill('推荐人乙');page.locator('#dyRefresh').click();page.wait_for_function("document.querySelector('#dyRows').textContent.includes('13900009999')")
    page.locator('#dyQuery').fill('无人匹配');page.locator('#dyRefresh').click();page.wait_for_function("document.querySelector('#dyRows').textContent.includes('暂无匹配')")
    page.locator('#dyClear').click();page.wait_for_function("document.querySelector('#dyRows').textContent.includes('13900009999')")
    page.screenshot(path=str(root/'admin-preview.png'),full_page=True)
    page.locator('[data-view=users]').click();assert page.locator('#dyView').is_hidden();assert page.locator('#usersView').is_visible()
    page.locator('#dyNav').click();page.locator('#logoutBtn').click();assert page.locator('#login').is_visible();assert page.locator('#dyView').is_hidden()
    assert page.locator('#dyRows').inner_text()==''
    assert not errors,errors
    browser.close()
print('PASS: shared admin session, product switching, pending/enable/edit/logout, escaped customer text, no JS errors')
