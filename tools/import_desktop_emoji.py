from pathlib import Path
from PIL import Image,ImageDraw,ImageFont
import hashlib,json
root=Path('C:/Users/user/Desktop/emoji'); out=Path('app/src/main/assets/emoji')
items=json.loads((out/'index.json').read_text(encoding='utf-8-sig')); byname={i['name']:i for i in items}
ids={'내셔 남작':'baron-nashor','넥서스':'nexus','레디언트 랭크':'radiant-rank','레이나 눈':'reyna-eye','레이즈 수류탄':'raze-grenade','롤 우승컵':'lol-trophy','미니언':'minion','발로란트':'valorant','세이지 힐':'sage-heal','스파이크':'spike','오멘':'omen','와드':'ward','제트 단검':'jett-knives','챌린저':'challenger','킬조이 포탑':'killjoy-turret','포로':'poro','피닉스 화염':'phoenix-fire','화염드래곤':'infernal-dragon','XLAH':'xlah'}
lol={'내셔 남작','넥서스','롤 우승컵','미니언','와드','챌린저','포로','화염드래곤','XLAH'}
audit=[]; previews=[]
font=ImageFont.truetype('C:/Windows/Fonts/malgun.ttf',18)
for f in sorted(root.glob('*.png')):
    raw=f.read_bytes(); im=Image.open(f).convert('RGBA');im.load();bbox=im.getchannel('A').getbbox();assert bbox, f
    name=f.stem
    if name not in byname:
        ident=ids[name];visible=im.crop(bbox);visible.thumbnail((448,448),Image.Resampling.LANCZOS)
        canvas=Image.new('RGBA',(512,512));canvas.alpha_composite(visible,((512-visible.width)//2,(512-visible.height)//2));canvas.save(out/(ident+'.png'),optimize=True)
        item={'id':ident,'name':name,'category':'리그 오브 레전드' if name in lol else '패션' if name=='XLAH' else '발로란트','keywords':name,'uri':f'file:///android_asset/emoji/{ident}.png'};items.append(item);byname[name]=item
    item=byname[name];target=out/(item['id']+'.png')
    audit.append({'source':f.name,'sourceSha256':hashlib.sha256(raw).hexdigest(),'sourceSize':im.size,'alpha':im.getchannel('A').getextrema(),'id':item['id'],'outputSha256':hashlib.sha256(target.read_bytes()).hexdigest()})
    tile=Image.new('RGB',(150,180),'#24242b');thumb=Image.open(target).convert('RGBA');thumb.thumbnail((130,140));tile.paste(thumb,((150-thumb.width)//2,0),thumb);ImageDraw.Draw(tile).text((75,153),name,font=font,fill='white',anchor='mt');previews.append(tile)
assert len(items)==len(audit)==29
(out/'index.json').write_text(json.dumps(items,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
Path('docs/emoji-audit-v051.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
sheet=Image.new('RGB',(900,900),'#17171c')
for i,tile in enumerate(previews):sheet.paste(tile,((i%6)*150,(i//6)*180))
sheet.save('validation/release-v051/emoji-29.png')
print('29 originals read and hashed; existing 10 paths retained, 19 bundled assets added.')
