import json, re, urllib.request, urllib.parse, os, time, sys
UA={'User-Agent':'slayer-teleport-swap plugin research (https://github.com/Jetsmoke/slayer-teleport-swap)'}
def raw(title, depth=0):
    fn='wiki/'+re.sub(r'[^A-Za-z0-9_.-]','_',title)+'.txt'
    if os.path.exists(fn): return open(fn).read()
    url='https://oldschool.runescape.wiki/w/'+urllib.parse.quote(title.replace(' ','_'))+'?action=raw'
    try:
        txt=urllib.request.urlopen(urllib.request.Request(url,headers=UA),timeout=30).read().decode()
    except Exception as e:
        txt=''
    m=re.match(r'#REDIRECT\s*\[\[([^\]#|]+)',txt,re.I)
    if m and depth<3:
        txt=raw(m.group(1).strip(),depth+1)
    open(fn,'w').write(txt); time.sleep(0.2)
    return txt
if __name__=='__main__':
    for t in sys.argv[1:]: print(t, len(raw(t)))
