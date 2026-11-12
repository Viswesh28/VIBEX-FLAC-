#!/usr/bin/env python3
"""Summarize Android gfxinfo PROFILEDATA; do not confuse with Perfetto present-time data."""
import argparse, csv, json, math, statistics

def frame_times(text):
    active=False; header=None; rows=[]
    for line in text.splitlines():
        line=line.strip()
        if line=='---PROFILEDATA---':
            active=not active; header=None; continue
        if not active or not line: continue
        cols=next(csv.reader([line]))
        if cols and cols[0]=='Flags': header=[c.strip() for c in cols]; continue
        if not header or len(cols)<len(header): continue
        item=dict(zip(header, cols))
        try:
            if int(item['Flags']) != 0: continue
            start=int(item['IntendedVsync']); end=int(item['FrameCompleted'])
            delta=(end-start)/1_000_000
            if 0 < delta <= 10_000: rows.append(delta)
        except (KeyError, ValueError): continue
    return rows

def summarize(values,hz):
    if not values: raise ValueError('No usable FrameCompleted/IntendedVsync rows; inspect the original trace.')
    vals=sorted(values)
    return {'frames':len(vals),'median_ms':round(statistics.median(vals),3),
      'p95_ms':round(vals[math.ceil(len(vals)*.95)-1],3),
      'over_refresh_budget':sum(x > 1000/hz for x in vals),'refresh_hz':hz,
      'metric':'gfxinfo FrameCompleted minus IntendedVsync; not display present time'}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('file');p.add_argument('--hz',type=float,required=True)
    args=p.parse_args()
    if not 1 <= args.hz <= 1000: p.error('refresh rate must be between 1 and 1000 Hz')
    with open(args.file,encoding='utf-8',errors='replace') as f: text=f.read()
    try: print(json.dumps(summarize(frame_times(text),args.hz),indent=2))
    except ValueError as e: p.error(str(e))
