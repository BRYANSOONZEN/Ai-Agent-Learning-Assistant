# -*- coding: utf-8 -*-
"""
把 record.mjs 录下的帧合成 MP4（1920×1080，30 帧/秒，H.264），并生成配音稿的时间点。
- 快进区间（d.fast）按倍数压缩时间，画面上显示“快进 ×N”；
- 每段开头几秒在顶部中间显示这一段的小标题（d.caption）；
- 每个 d.label 换算成成片里的时间，写进 时间点.json，配音稿按它分段。
用法：python tools/demo/encode.py 帧目录 输出.mp4 段名 [段名 ...]
"""
import bisect
import json
import os
import sys

import av
from PIL import Image, ImageDraw, ImageFont

FPS = 30
W, H = 1920, 1080
FONT = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 30)
FONT_S = ImageFont.truetype('C:/Windows/Fonts/msyhbd.ttc', 26)
HOLD_END = 0.6  # 每段结尾多停一会儿
CAPTION_SEC = 5  # 小标题只在每段开头显示几秒（不挡住页面顶部的提示消息）


def pill(draw, xy_center_top, text, font, fill, fg='#ffffff'):
    l, t, r, b = draw.textbbox((0, 0), text, font=font)
    tw, th = r - l, b - t
    cx, top = xy_center_top
    pad_x, pad_y = 26, 12
    box = (cx - tw / 2 - pad_x, top, cx + tw / 2 + pad_x, top + th + pad_y * 2)
    draw.rounded_rectangle(box, radius=(th + pad_y * 2) / 2, fill=fill)
    draw.text((cx - tw / 2 - l, top + pad_y - t), text, font=font, fill=fg)
    return box


def overlay(img, caption, speed):
    if not caption and speed == 1:
        return img
    img = img.convert('RGBA')
    layer = Image.new('RGBA', img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    y = 18
    if caption:
        box = pill(d, (W / 2, y), caption, FONT, (17, 30, 54, 215))
        y = box[3] + 10
    if speed != 1:
        pill(d, (W / 2, y), '▶▶ 快进 ×%g' % speed, FONT_S, (230, 120, 20, 230))
    return Image.alpha_composite(img, layer).convert('RGB')


def segment_times(tl):
    """真实时间 → 成片时间的换算（按快进区间分段线性）"""
    t0, t1 = tl['t0'], tl['t1']
    speeds = [(t0, 1.0)] + [(m['t'], float(m['factor'])) for m in tl['marks'] if m['kind'] == 'speed']
    knots = []  # (真实时间, 成片时间, 倍速)
    vt = 0.0
    for i, (t, s) in enumerate(speeds):
        if knots:
            pt, pv, ps = knots[-1]
            vt = pv + (t - pt) / ps
        knots.append((t, vt, s))
    rt = [k[0] for k in knots]

    def v(t):
        i = max(0, bisect.bisect_right(rt, t) - 1)
        kt, kv, ks = knots[i]
        return kv + (max(t, t0) - kt) / ks

    def speed_at(t):
        i = max(0, bisect.bisect_right(rt, t) - 1)
        return knots[i][2]
    return v, speed_at, v(t1)


def main(src, out, names):
    container = av.open(out, mode='w')
    stream = container.add_stream('libx264', rate=FPS)
    stream.width, stream.height, stream.pix_fmt = W, H, 'yuv420p'
    stream.options = {'crf': '20', 'preset': 'veryfast'}
    offset = 0.0
    points = []
    n_out = 0
    for name in names:
        d = os.path.join(src, name)
        tl = json.load(open(os.path.join(d, 'timeline.json'), encoding='utf-8'))
        frames = tl['frames']
        if not frames:
            print('跳过（没有帧）', name)
            continue
        v, speed_at, total = segment_times(tl)
        total += HOLD_END
        fv = [v(f['t']) for f in frames]
        caps = [(m['t'], m['text']) for m in tl['marks'] if m['kind'] == 'caption']
        cap_t = [v(t) for t, _ in caps]
        for m in tl['marks']:
            if m['kind'] == 'label':
                points.append({'t': round(offset + v(m['t']), 1), 'segment': name, 'text': m['text']})
        # 成片时间 → 真实时间的倍速，用来显示快进角标
        speed_marks = [(v(m['t']), float(m['factor'])) for m in tl['marks'] if m['kind'] == 'speed']
        cache_key, cache_frame = None, None
        k = 0
        nframes = int(total * FPS)
        for i in range(nframes):
            vt = i / FPS
            j = max(0, bisect.bisect_right(fv, vt) - 1)
            ci = bisect.bisect_right(cap_t, vt) - 1
            caption = caps[ci][1] if ci >= 0 and vt - cap_t[ci] < CAPTION_SEC else ''
            sp = 1.0
            for t_, s_ in speed_marks:
                if t_ <= vt:
                    sp = s_
            key = (j, caption, sp)
            if key != cache_key:
                img = Image.open(os.path.join(d, frames[j]['f'])).convert('RGB')
                if img.size != (W, H):
                    img = img.resize((W, H), Image.LANCZOS)
                img = overlay(img, caption, sp)
                cache_frame = av.VideoFrame.from_image(img)
                cache_key = key
            cache_frame.pts = None
            for p in stream.encode(cache_frame):
                container.mux(p)
            n_out += 1
        print('%s：%.1f 秒（原始 %.1f 秒，%d 帧）' % (name, total, tl['t1'] - tl['t0'], len(frames)))
        offset += total
    for p in stream.encode():
        container.mux(p)
    container.close()
    json.dump({'total': round(offset, 1), 'points': points}, open(os.path.splitext(out)[0] + '-时间点.json', 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    print('成片 %.1f 秒 → %s' % (offset, out))


if __name__ == '__main__':
    sys.stdout.reconfigure(encoding='utf-8')
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
