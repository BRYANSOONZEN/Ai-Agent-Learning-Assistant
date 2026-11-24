"""
资料处理服务（常驻）：后端每收到老师上传的课件或视频，就发给这里转成文字。

  python service.py --port 8091

为什么常驻：语音识别模型加载一次要几秒到几十秒，常驻后只在第一次用到时加载，之后一直留在内存里。
一次只处理一份资料（加锁排队），多个老师同时上传也不会把电脑拖垮。只用 Python 自带的 http.server，不用额外的库。

接口（只给本机的后端用，只监听 127.0.0.1）：
  GET  /health                         → {"ok": true, "model_loaded": false}
  POST /extract  {"path": "...", "kind": "pdf" | "video", "lang": "zh", "images": true}
       PDF  → {"segments": [{"key": "P4", "kind": "pdf", "page": 4, "text": "..."},
                            {"key": "P13-图1", "kind": "image", "page": 13, "text": "..."}], "meta": {...}}
       视频 → {"segments": [{"key": "01", "kind": "video", "start": 0.0, "end": 41.3, "text": "..."}], "meta": {...}}
  出错时返回 {"error": "原因"}，HTTP 400 / 500。

每一步用的就是 tools/material_kp 和 tools/video_to_text 里验证过的脚本（见 验证结果/第6章-记录.md）。
"""
import argparse
import hashlib
import json
import os
import sys
import threading
import time
import traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, '..', 'video_to_text'))

import pymupdf  # noqa: E402

import image_to_text  # noqa: E402
import pdf_to_text  # noqa: E402
import video_to_text  # noqa: E402

LOCK = threading.Lock()        # 一次只处理一份
# 缓存：同一个文件（按内容算 SHA-256）处理过一次就把结果存下来，以后直接用，不再花时间、不再花钱看图。
# 数据库丢了（开发时删库重来、服务器出问题重新导入）也能马上恢复。删掉这个文件夹就会重新处理。
CACHE_DIR = os.path.join(HERE, '.cache')
STATE = {'model_loaded': False, 'busy': None}


def file_hash(path):
    h = hashlib.sha256()
    with open(path, 'rb') as f:
        for block in iter(lambda: f.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def cached(path, kind, images, work):
    """有缓存就直接返回（meta.cached = true），没有就处理并存下来"""
    os.makedirs(CACHE_DIR, exist_ok=True)
    key = f"{file_hash(path)}-{kind}{'-img' if kind == 'pdf' and images else ''}"
    cache = os.path.join(CACHE_DIR, key + '.json')
    if os.path.exists(cache):
        data = json.load(open(cache, encoding='utf-8'))
        data['meta'] = {**data.get('meta', {}), 'cached': True}
        return data['segments'], data['meta']
    segs, meta = work()
    tmp = cache + '.tmp'
    json.dump({'segments': segs, 'meta': meta, 'file': os.path.basename(path)}, open(tmp, 'w', encoding='utf-8'),
              ensure_ascii=False)
    os.replace(tmp, cache)   # 先写临时文件再改名：写到一半出错不会留下坏缓存
    return segs, meta


def extract_pdf(path, with_images):
    t0 = time.time()
    pages, skipped, total = pdf_to_text.pdf_pages(path)
    segs = [{'key': p['id'], 'kind': 'pdf', 'page': p['page'], 'text': p['text']} for p in pages]
    meta = {'pages': total, 'skipped_pages': skipped, 'text_sec': round(time.time() - t0, 2)}
    if with_images:
        t1 = time.time()
        doc = pymupdf.open(path)
        key = image_to_text.api_key()
        found, failed, tokens = image_to_text.find_images(doc), [], [0, 0]
        for im in found:
            page = doc[im['page'] - 1]
            png = image_to_text.crop_png(page, im['bbox'])
            prompt = image_to_text.PROMPT.format(page=im['page'], page_text=' '.join(page.get_text().split())[:800])
            try:
                text, usage, _, finish = image_to_text.look(png, prompt, key, image_to_text.MODEL)
                if finish == 'length':
                    text, usage, _, finish = image_to_text.look(png, prompt, key, image_to_text.MODEL)
            except Exception as e:  # 一张图失败不影响其他
                failed.append(f"{im['id']}：{e}")
                continue
            tokens[0] += usage.get('prompt_tokens') or 0
            tokens[1] += usage.get('completion_tokens') or 0
            segs.append({'key': im['id'], 'kind': 'image', 'page': im['page'], 'text': ' '.join(text.split())})
        meta.update({'images': len(found), 'images_failed': failed, 'image_sec': round(time.time() - t1, 1),
                     'image_input_tokens': tokens[0], 'image_output_tokens': tokens[1]})
    return segs, meta


MODELS = {}


def models():
    """语音识别模型只加载一次，之后一直留在内存里（这就是“常驻”省下的时间）"""
    if not MODELS:
        from funasr import AutoModel
        t0 = time.time()
        MODELS['vad'] = AutoModel(model='funasr/fsmn-vad', hub='hf', disable_update=True, device='cpu',
                                  max_single_segment_time=video_to_text.SV_MAX_SEGMENT_MS)
        MODELS['asr'] = AutoModel(model='FunAudioLLM/SenseVoiceSmall', hub='hf', disable_update=True, device='cpu')
        STATE['model_loaded'] = True
        STATE['model_load_sec'] = round(time.time() - t0, 1)
    return MODELS['vad'], MODELS['asr']


def transcribe(audio, lang):
    """和 video_to_text.transcribe_sensevoice 一样的识别步骤，只是模型用缓存的"""
    import re
    rate = video_to_text.RATE
    vad, asr = models()
    t1 = time.time()
    spans = vad.generate(input=audio, fs=rate)[0]['value']
    pieces = [audio[int(s / 1000 * rate):int(e / 1000 * rate)] for s, e in spans]
    res = asr.generate(input=pieces, fs=rate, language=lang, use_itn=True, batch_size=16) if pieces else []
    segments = []
    for (s, e), r in zip(spans, res):
        text = video_to_text.clean_spoken(re.sub(r'<\|[^|]*\|>', '', r['text']))
        if text:
            segments.append({'start': round(s / 1000, 1), 'end': round(e / 1000, 1), 'text': text})
    return segments, {'transcribe_sec': round(time.time() - t1, 1)}


def extract_video(path, lang):
    problem = video_to_text.check(path)
    if problem:
        raise ValueError(problem)
    t0 = time.time()
    _, seconds, _ = video_to_text.probe(path)
    audio = video_to_text.load_audio(path)
    segments, meta = transcribe(audio, lang)
    chunks = video_to_text.build_chunks(segments)
    segs = [{'key': f"{c['id']:02d}", 'kind': 'video', 'start': round(c['start'], 1), 'end': round(c['end'], 1),
             'text': c['text']} for c in chunks]
    return segs, {'duration_sec': round(seconds, 1), 'sentences': len(segments),
                  'transcribe_sec': meta.get('transcribe_sec'), 'total_sec': round(time.time() - t0, 1)}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, data):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == '/health':
            self._send(200, {'ok': True, **STATE})
        else:
            self._send(404, {'error': 'not found'})

    def do_POST(self):
        if self.path != '/extract':
            self._send(404, {'error': 'not found'})
            return
        try:
            req = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))) or b'{}')
            path, kind = req.get('path'), req.get('kind')
            if not path or not os.path.isfile(path):
                self._send(400, {'error': '文件不存在'})
                return
            with LOCK:
                STATE['busy'] = os.path.basename(path)
                try:
                    images = req.get('images', True)
                    if kind == 'pdf':
                        segs, meta = cached(path, kind, images, lambda: extract_pdf(path, images))
                    elif kind == 'video':
                        segs, meta = cached(path, kind, False, lambda: extract_video(path, req.get('lang', 'zh')))
                    else:
                        self._send(400, {'error': f'不支持的类型：{kind}'})
                        return
                finally:
                    STATE['busy'] = None
            self._send(200, {'segments': segs, 'meta': meta})
        except ValueError as e:
            self._send(400, {'error': str(e)})
        except Exception as e:  # 记下来，返回原因，服务不退出
            traceback.print_exc()
            self._send(500, {'error': f'{type(e).__name__}: {e}'})

    def log_message(self, fmt, *args):
        sys.stderr.write(time.strftime('%H:%M:%S ') + (fmt % args) + '\n')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--port', type=int, default=8091)
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')
    print(f'资料处理服务已启动：http://127.0.0.1:{a.port}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', a.port), Handler).serve_forever()


if __name__ == '__main__':
    main()
