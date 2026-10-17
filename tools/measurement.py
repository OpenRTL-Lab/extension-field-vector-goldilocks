# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks Extension-Field Vector Hardware Accelerator
# Chisel hardware for vector arithmetic over the quadratic extension defined by u squared = 7.
# Repository: https://github.com/OpenRTL-Lab/extension-field-vector-goldilocks
# File: tools/measurement.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks extension-field vector contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""独立Python域模型互校及Chisel有效事件周期计量。"""
import re
P=18446744069414584321

def reference_check(vectors):
    def times(a,b):return ((a[0]*b[0]+7*a[1]*b[1])%P,(a[0]*b[1]+a[1]*b[0])%P)
    assert pow(7,(P-1)//2,P)==P-1 and times((0,1),(0,1))==(7,0)
    ins=[int(x,16) for x in (vectors/'input.hex').read_text().splitlines()]
    exp=[int(x,16) for x in (vectors/'expected.hex').read_text().splitlines()]; checked=0
    for line in (vectors/'plans.tsv').read_text().splitlines():
        mode,n,base,tag,io,oo,ol,status,zeros=map(int,line.split());dot=(0,0)
        if status==1:continue
        for i in range(n):
            word=ins[io+i];values=[(word>>(64*k))&((1<<64)-1) for k in range(6)]
            a,b,c=tuple(values[:2]),tuple(values[2:4]),tuple(values[4:6])
            if any(x>=P for x in values) or base and b[1]:a=b=c=(0,0)
            if mode==0:r=((a[0]+b[0])%P,(a[1]+b[1])%P)
            elif mode==1:r=((a[0]-b[0])%P,(a[1]-b[1])%P)
            elif mode==3:r=times(a,a)
            elif mode==4:
                norm=(a[0]*a[0]-7*a[1]*a[1])%P;t=pow(norm,P-2,P)
                r=(a[0]*t%P,-a[1]*t%P)
                if a!=(0,0):assert times(a,r)==(1,0)
            else:
                r=times(a,b)
                if mode==5:r=((r[0]+c[0])%P,(r[1]+c[1])%P)
            if mode==6:dot=((dot[0]+r[0])%P,(dot[1]+r[1])%P)
            else:
                encoded=r[0]+(r[1]<<64)+(int(mode==4 and a==(0,0))<<128)
                assert exp[oo+i]==encoded,(mode,i);checked+=1
        if mode==6:assert exp[oo]==dot[0]+(dot[1]<<64);checked+=1
    return dict(p=P,nonresidue=7,euler=P-1,checked=checked,status=0,independent_inverse='Python范数逆元互校C扩域指数逆元')
def metrics(log,plans):
    events={};work={0:[],1:[],2:[]};groups={}
    for line in log.splitlines():
        m=re.match(r'EVENT ([AFOC]) job=\s*(\d+).*?cycle=\s*(\d+)',line)
        if m:events.setdefault(int(m[2]),{})[m[1]]=int(m[3])
        m=re.match(r'WORK unit=\s*(\d+) cycle=\s*(\d+) tag=\s*(\d+)',line)
        if m:work[int(m[1])].append((int(m[3]),int(m[2])))
        m=re.match(r'GROUP name=(\w+) cycle=\s*(\d+)',line)
        if m:groups.setdefault(m[1],[]).append(int(m[2]))
    rows=[]
    for job,plan in enumerate(plans):
        e=events[job];assert 'A' in e and 'C' in e
        row=dict(job=job,**plan,**e,latency_cycles=e['C']-e['A'],units=[])
        for k in range(3):
            issue=[c for t,c in work[k] if t==plan['tag']]
            # 工作事件在操作数入口寄存后，算术有效占用14拍；全接口反馈15拍。
            occupied=set()
            for c in issue:occupied.update(range(c,c+14))
            window=e['C']-e['A'];occ=sum(e['A']<=c<e['C'] for c in occupied)
            expected=0
            if plan['fault']!=3 and plan['fault']!=6 and plan['mode'] in (2,3,5,6):expected=plan['length']
            if plan['mode']==4 and k==0 and plan['fault'] not in (3,6):
                meta=plan['zeros'];expected=131*(plan['length']-meta)
            if plan['mode']==4 and k>0:expected=0
            if plan['mode'] in (0,1,7) or plan['length']==0 or plan['status']==1:expected=0
            if plan['fault'] not in (3,6):assert len(issue)==expected,(plan,k,len(issue),expected)
            span=(max(issue)-min(issue)+14) if issue else 0
            row['units'].append(dict(unit=k,issues=len(issue),occupied_cycles=occ,idle_cycles=window-occ,
                issue_busy_percent=100*len(issue)/window if window else 0,occupied_percent=100*occ/window if window else 0,
                compute_span_cycles=span,compute_issue_percent=100*len(issue)/span if span else 0))
        row['arithmetic_groups']=[]
        group_units=dict(pre_add=2,post1=2,post2=3,post3=1,post4=1,fma_add=2,bypass_add=2,bypass_sub=2,dot_add=2,
            dot_merge1=8,dot_merge2=4,dot_merge3=2,inv_norm_double=1,inv_norm_four_three=2,inv_norm_seven=1,inv_norm_sub=1,inv_neg=1)
        for name,units in group_units.items():
            cycles=[c for c in groups.get(name,[]) if e['A']<=c<e['C']]
            assert len(cycles)==len(set(cycles)),(name,job)
            span=max(cycles)-min(cycles)+1 if cycles else 0
            row['arithmetic_groups'].append(dict(name=name,logical_field_operations=units,busy_cycles=len(cycles),idle_cycles=row['latency_cycles']-len(cycles),
                busy_percent=100*len(cycles)/row['latency_cycles'],compute_span_cycles=span,compute_busy_percent=100*len(cycles)/span if span else 0))
        rows.append(row)
    batches=[]
    for mode in range(7):
        group=[r for r in rows if r['kind']=='steady' and r['mode']==mode]
        if len(group)>1:
            span=group[-1]['C']-group[0]['C'];gap=[b['C']-a['C'] for a,b in zip(group,group[1:])]
            batches.append(dict(mode=mode,tasks=len(group),length=group[0]['length'],completion_interval_min=min(gap),completion_interval_max=max(gap),
                measured_tasks_per_clock=(len(group)-1)/span,elements_per_clock=(len(group)-1)*group[0]['length']/span))
    return dict(tasks=rows,batches=batches,frequency_certified=False,task_overlap=False)
