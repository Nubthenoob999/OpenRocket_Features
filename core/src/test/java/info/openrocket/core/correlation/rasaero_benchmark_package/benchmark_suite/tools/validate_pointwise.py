#!/usr/bin/env python3
"""Compare solver output JSON against pointwise benchmark cases.

Solver output format:
{
  "predictions": [
    {"case_id":"A53D02:fig11_faired_zero_lift_cd:0", "outputs":{"CD_zero_lift":0.36}},
    ...
  ]
}
"""
import argparse, json, math
from pathlib import Path

def tol_abs(t):
    if not t: return 0.0
    for k in ('abs','coefficient_abs'):
        if k in t: return float(t[k])
    return 0.0

ap=argparse.ArgumentParser()
ap.add_argument('solver_output')
ap.add_argument('--cases',default=str(Path(__file__).resolve().parents[1]/'data'/'pointwise_test_cases.json'))
args=ap.parse_args()
cases=json.load(open(args.cases))['cases']
preds=json.load(open(args.solver_output)).get('predictions',[])
byid={p['case_id']:p for p in preds}
passed=failed=missing=0
rows=[]
for c in cases:
    cid=c['case_id']
    p=byid.get(cid)
    if not p:
        missing+=1; rows.append((cid,'MISSING','')); continue
    ok=True; details=[]
    for key,exp in c['expected'].items():
        got=p.get('outputs',{}).get(key)
        if got is None or not isinstance(got,(int,float)) or not math.isfinite(got):
            ok=False; details.append(f'{key}=missing/nonfinite'); continue
        ta=tol_abs(c.get('tolerance',{}))
        err=abs(got-exp)
        if err>ta: ok=False
        details.append(f'{key}: got={got:.6g} exp={exp:.6g} err={err:.3g} tol={ta:.3g}')
    if ok: passed+=1; status='PASS'
    else: failed+=1; status='FAIL'
    rows.append((cid,status,'; '.join(details)))
for cid,status,detail in rows:
    print(f'{status:7s} {cid} {detail}')
print(f'\nSummary: PASS={passed} FAIL={failed} MISSING={missing} TOTAL={len(cases)}')
raise SystemExit(1 if failed or missing else 0)
