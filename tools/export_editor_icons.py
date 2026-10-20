from pathlib import Path
import re, html
source=Path('app/src/main/java/com/vividhyeok/igstoryplus/EditorIcons.kt').read_text(encoding='utf-8')
labels=['close','deselect','undo','redo','text','sticker','music','more','layer-more','add-photo','film-style','story-plus','arrange','center-horizontal','center-vertical','level','lock','text-background','heart','download','play','pause','restart','crop','rotate','flip','replace','duplicate','delete','edit','align','palette','font','waveform','music-card','volume']
items=re.findall(r'^\s*"([^"]+)" to "([^"]+)"',source,re.M)
assert len(items)==len(labels),(len(items),len(labels))
root=Path('design/icons');root.mkdir(parents=True,exist_ok=True)
for (name,path),label in zip(items,labels):
    (root/(label+'.svg')).write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" role="img"><title>{html.escape(name)}</title><path d="{path}"/></svg>\n',encoding='utf-8')
print(f'Exported {len(items)} original SVG icons')
