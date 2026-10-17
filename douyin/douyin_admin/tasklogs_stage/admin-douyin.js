/* Add a Douyin view to the existing central console, preserving XHS handlers. */
(() => {
  const el = id => document.getElementById(id);
  const escape = v => String(v ?? '').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  let rows=[],current=null,opened=false;
  const states={active:'启用',pending:'待授权',frozen:'已停用',expired:'已到期'};
  const button=document.createElement('button');button.textContent='♙ 抖音用户管理';button.id='dyNav';
  const old=document.querySelector('[data-view="users"]');old.textContent='♙ 小红书用户管理';old.parentNode.appendChild(button);
  document.querySelector('[data-view="activity"]').textContent='☷ 小红书行为动作';
  const view=document.createElement('section');view.id='dyView';view.className='hidden';
  view.innerHTML=`<div class="stats"><div class="stat"><span>抖音总用户</span><strong id="dyTotal">0</strong></div><div class="stat"><span>已授权</span><strong id="dyActive">0</strong></div><div class="stat"><span>待授权</span><strong id="dyPending">0</strong></div><div class="stat"><span>停用 / 到期</span><strong id="dyOff">0</strong></div></div>
  <div class="panel"><div class="panel-title">抖音 · 用户与授权管理<button id="dyAdd" class="primary">添加抖音用户</button></div>
  <div class="filters"><label for="dyQuery">账号/备注/推荐人：</label><input id="dyQuery" placeholder="模糊匹配"><select id="dyStatus"><option value="">全部状态</option><option value="pending">待授权</option><option value="active">启用</option><option value="frozen">已停用</option><option value="expired">已到期</option></select><button id="dyRefresh" class="primary">查询</button><button id="dyClear">重置</button><span id="dyError" style="color:#d4380d"></span></div>
  <div style="overflow:auto"><table style="min-width:1500px"><thead><tr><th>序号</th><th>用户名</th><th>密码</th><th>备注</th><th>推荐人</th><th>来源</th><th>状态</th><th>使用时间</th><th>注册时间</th><th>最近登录</th><th>最近使用</th><th>设备 / 版本</th><th style="width:255px">操作</th></tr></thead><tbody id="dyRows"></tbody></table></div>
  <p style="padding:12px;color:#66758a">新注册账号待授权。停止权限后，新版抖音客户端会在下一次授权检查时停止任务（通常15秒内）。小红书权限独立管理。</p></div>`;
  el('usersView').parentNode.appendChild(view);
  const drawer=document.createElement('div');drawer.className='drawer';drawer.id='dyDrawer';
  drawer.innerHTML=`<form id="dyForm" class="dialog"><h2 id="dyTitle">抖音用户</h2><div class="field"><label>手机号</label><input id="dyPhone" required pattern="1[3-9][0-9]{9}"></div><div class="field" id="dyPasswordField"><label>初始密码（至少6位，管理员可查看）</label><input id="dyPassword" type="password" minlength="6" maxlength="128" autocomplete="new-password"></div><div class="field"><label>备注</label><input id="dyNote" maxlength="500"></div><div class="field"><label>推荐人</label><input id="dyReferrer" maxlength="100" placeholder="选填"></div><div class="field" id="dyStateField"><label>授权状态</label><select id="dyEditStatus"><option value="active">启用</option><option value="pending">待授权</option><option value="frozen">停用</option></select></div><div class="field" id="dyExpiryField"><label>到期时间（当前浏览器时区；留空长期）</label><input id="dyExpires" type="datetime-local"></div><div id="dyFormError" class="error"></div><div class="dialog-actions"><button type="button" id="dyCancel">取消</button><button class="primary">保存</button></div></form>`;
  document.body.appendChild(drawer);
  async function api(path,opt={}){
    const r=await fetch('/douyin/api/v1/management'+path,{...opt,headers:{'Content-Type':'application/json',Authorization:'Bearer '+(localStorage.getItem('central_auth_token')||'')}});
    const body=await r.json();if(!r.ok)throw new Error(typeof body.detail==='string'?body.detail:'请求失败');return body;
  }
  const time=v=>v?new Date(v.endsWith('Z')||/[+-]\d\d:\d\d$/.test(v)?v:v+'Z').toLocaleString('zh-CN'):'—';
  async function load(){
    try {el('dyError').textContent='';const incoming=await api('/users');if(!opened)return;rows=incoming;el('dyTotal').textContent=rows.length;
      for(const [id,match] of [['dyActive',['active']],['dyPending',['pending']],['dyOff',['frozen','expired']]])el(id).textContent=rows.filter(r=>match.includes(r.status)).length;
      const q=el('dyQuery').value.trim(),status=el('dyStatus').value;
      el('dyRows').innerHTML=rows.filter(r=>(!q||(r.phone+' '+r.note+' '+(r.referrer||'')).toLowerCase().includes(q.toLowerCase()))&&(!status||r.status===status)).map((r,index)=>{
        const d=[...r.devices].sort((a,b)=>String(b.last_seen||'').localeCompare(String(a.last_seen||'')))[0];
        return `<tr><td>${index+1}</td><td>${escape(r.phone)}</td><td style="overflow-wrap:anywhere;max-width:150px">${r.password?escape(r.password):'<span style="color:#8893a3">需重置后查看</span>'}</td><td title="${escape(r.note)}">${escape(r.note)}</td><td>${escape(r.referrer)}</td><td><span class="badge role">抖音</span></td><td><span class="badge ${r.status==='active'?'ok':'off'}">${states[r.status]||escape(r.status)}</span></td><td>${r.expires_at?escape(time(r.expires_at)):'长期'}</td><td>${escape(time(r.created_at))}</td><td>${escape(time(r.last_login))}</td><td>${escape(time(r.last_used))}</td><td title="${escape(d?.device_id||'')}">${r.devices.length} 台 · ${escape(d?.app_version||'—')}</td><td><div class="actions"><button class="link" data-dy-tasklogs="${r.id}">任务日志</button><button class="link" data-dy-diagnostics="${r.id}">诊断包</button><button class="link" data-dy-logs="${r.id}">查看日志</button><button class="link ${r.status==='active'?'danger':''}" data-dy-toggle="${r.id}">${r.status==='active'?'停止权限':'启用权限'}</button><button class="link" data-dy-edit="${r.id}">编辑资料</button><button class="link" data-dy-reset="${r.id}">重置密码</button></div></td></tr>`;
      }).join('')||'<tr><td colspan="13">暂无匹配的抖音用户</td></tr>';
    }catch(e){el('dyError').textContent=e.message;}
  }
  button.onclick=()=>{opened=true;document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));button.classList.add('active');el('usersView').classList.add('hidden');el('activityView').classList.add('hidden');view.classList.remove('hidden');el('crumbTitle').textContent='抖音用户管理';load();};
  document.addEventListener('click',async e=>{
    if(e.target.closest('[data-view]')||e.target.id==='logoutBtn'){opened=false;view.classList.add('hidden');button.classList.remove('active');drawer.classList.remove('open');if(e.target.id==='logoutBtn'){rows=[];el('dyRows').replaceChildren();el('dyPassword').value='';}}
    const id=e.target.dataset.dyToggle||e.target.dataset.dyEdit||e.target.dataset.dyReset||e.target.dataset.dyLogs||e.target.dataset.dyTasklogs||e.target.dataset.dyDiagnostics;
    if(!id)return;const r=rows.find(v=>v.id===Number(id));if(!r)return;
    try {
      if(e.target.dataset.dyEdit)return edit(r);
      if(e.target.dataset.dyTasklogs){
        const tasks=await api('/users/'+id+'/tasks');if(!tasks.length){alert('该用户还没有上传任务日志');return;}
        const choices=tasks.map((t,i)=>`${i+1}. ${t.task_id} · ${time(t.updated_at)} · ${(t.size/1024).toFixed(1)} KB`).join('\n');
        const answer=prompt('输入要下载的任务序号：\n'+choices,'1');if(answer===null)return;const index=Number(answer)-1;if(!Number.isInteger(index)||index<0||index>=tasks.length)throw new Error('任务序号无效');
        const task=tasks[index],response=await fetch('/douyin/api/v1/management/users/'+id+'/tasks/'+encodeURIComponent(task.task_id)+'/logs',{headers:{Authorization:'Bearer '+(localStorage.getItem('central_auth_token')||'')}});if(!response.ok)throw new Error('下载失败（HTTP '+response.status+'）');
        const link=document.createElement('a'),url=URL.createObjectURL(await response.blob());link.href=url;link.download=r.phone+'_'+task.task_id+'.log';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);return;
      }
      if(e.target.dataset.dyDiagnostics){
        const tasks=await api('/users/'+id+'/diagnostics');if(!tasks.length){alert('该用户还没有上传诊断包');return;}
        const choices=tasks.map((t,i)=>`${i+1}. ${t.task_id} · ${time(t.updated_at)} · ${(t.size/1024).toFixed(1)} KB`).join('\n');
        const answer=prompt('输入要下载的诊断包序号：\n'+choices,'1');if(answer===null)return;const index=Number(answer)-1;if(!Number.isInteger(index)||index<0||index>=tasks.length)throw new Error('任务序号无效');
        const task=tasks[index],response=await fetch('/douyin/api/v1/management/users/'+id+'/tasks/'+encodeURIComponent(task.task_id)+'/diagnostics',{headers:{Authorization:'Bearer '+(localStorage.getItem('central_auth_token')||'')}});if(!response.ok)throw new Error('下载失败（HTTP '+response.status+'）');
        const link=document.createElement('a'),url=URL.createObjectURL(await response.blob());link.href=url;link.download=r.phone+'_'+task.task_id+'_诊断.zip';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000);return;
      }
      if(e.target.dataset.dyToggle){const status=r.status==='active'?'frozen':'active';if(!confirm(`${status==='active'?'启用':'停用'}抖音账号 ${r.phone}？`))return;
        if(status==='active'&&r.status==='expired'){edit(r);el('dyFormError').textContent='请设置新的到期时间，或清空改为长期';return;}
        await api('/users/'+id,{method:'PUT',body:JSON.stringify({status,note:r.note,expires_at:r.expires_at})});}
      if(e.target.dataset.dyReset){const password=prompt('输入新密码（至少6位），原登录将失效');if(password===null)return;if(password.length<6)throw new Error('密码至少6位');await api('/users/'+id+'/password',{method:'POST',body:JSON.stringify({password})});}
      if(e.target.dataset.dyLogs){const logs=await api('/logs?phone='+encodeURIComponent(r.phone));el('detailText').value=logs.map(l=>`${time(l.created_at)} · ${l.action}\n${l.detail}`).join('\n\n')||'暂无日志';el('detailDrawer').classList.add('open');return;}
      await load();
    }catch(err){el('dyError').textContent=err.message;}
  });
  function edit(r){current=r;el('dyForm').reset();el('dyTitle').textContent=r?'编辑抖音授权':'添加抖音用户';el('dyPhone').value=r?.phone||'';el('dyPhone').disabled=!!r;el('dyNote').value=r?.note||'';el('dyReferrer').value=r?.referrer||'';el('dyPasswordField').classList.toggle('hidden',!!r);el('dyPassword').required=!r;
    el('dyStateField').classList.toggle('hidden',!r);el('dyExpiryField').classList.toggle('hidden',!r);el('dyEditStatus').value=r?.status==='expired'?'active':r?.status||'active';
    if(r?.expires_at){const d=new Date(r.expires_at);el('dyExpires').value=new Date(d-d.getTimezoneOffset()*60000).toISOString().slice(0,16);}el('dyFormError').textContent='';drawer.classList.add('open');}
  el('dyAdd').onclick=()=>edit(null);el('dyCancel').onclick=()=>drawer.classList.remove('open');el('dyRefresh').onclick=load;
  el('dyClear').onclick=()=>{el('dyQuery').value='';el('dyStatus').value='';load();};
  el('dyForm').onsubmit=async e=>{e.preventDefault();try{const payload=current?{status:el('dyEditStatus').value,note:el('dyNote').value,expires_at:el('dyExpires').value?new Date(el('dyExpires').value).toISOString():''}:{phone:el('dyPhone').value.trim(),password:el('dyPassword').value,note:el('dyNote').value};payload.referrer=el('dyReferrer').value.trim();await api('/users'+(current?'/'+current.id:''),{method:current?'PUT':'POST',body:JSON.stringify(payload)});drawer.classList.remove('open');el('dyPassword').value='';load();}catch(err){el('dyFormError').textContent=err.message;}};
  setInterval(()=>{if(opened&&!document.hidden)load();},15000);
})();
