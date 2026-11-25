from pathlib import Path
from PIL import Image
root = Path(__file__).resolve().parents[1]
source = Image.open(root / 'assets/branding/musik-logo.png').convert('RGB')
res = root / 'app/src/main/res'
for density, size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    folder = res / ('mipmap-' + density)
    for name in ['ic_launcher','ic_launcher_round']:
        source.resize((size,size), Image.Resampling.LANCZOS).save(folder / (name+'.webp'), lossless=True)
    # Keep the complete logo inside the adaptive mask's safe area.
    canvas_size = round(size * 108 / 48)
    logo_size = round(canvas_size * 0.60)
    canvas = Image.new('RGB',(canvas_size,canvas_size),(0,0,0))
    canvas.paste(source.resize((logo_size,logo_size),Image.Resampling.LANCZOS),((canvas_size-logo_size)//2,)*2)
    canvas.save(folder / 'ic_launcher_foreground.webp',lossless=True)
source.resize((512,512),Image.Resampling.LANCZOS).save(root / 'app/src/main/ic_launcher-playstore.png')

(res / 'drawable-nodpi').mkdir(exist_ok=True)
source.resize((512,512),Image.Resampling.LANCZOS).save(res / 'drawable-nodpi/musik_logo.webp', lossless=True)
