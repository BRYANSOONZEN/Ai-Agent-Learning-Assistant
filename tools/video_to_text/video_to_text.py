"""
视频转文字（课件处理的视频部分，原型）。设计见 docs/计划-个人学习助手.md 第 8 节。

做什么：
  1. 上传前检查：文件 ≤ 200 MB（和后端上传限制一致），时长 ≤ 60 分钟
  2. 在本机把声音识别成文字，每句带开始、结束时间（不把视频发给外部服务）
     按课程语言自动选：中文用 SenseVoice（快、中文专业词准），英文用 Whisper（英文更准）
  3. 把句子合并成 30～60 秒一段，带前一段最后一句（重叠），标上知识点
  4. 输出 JSON，记录识别耗时，供老师校对、再交给检索使用

用法：
  python video_to_text.py 视频文件 --chapter 1 --out 结果.json --lang zh [--engine auto|sensevoice|whisper]

依赖：
  SenseVoice（中文课）：pip install funasr torch torchaudio（PyTorch 用 CPU 版即可）；第一次运行下载模型约 940 MB
  Whisper（英文课）：pip install faster-whisper；第一次运行下载 small 模型约 480 MB
"""
import argparse
import json
import os
import re
import sys
import time

import av

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # 用 python -I 运行时，也能找到同目录的 text_utils
from text_utils import kp_match, shared_tokens  # noqa: E402

MAX_BYTES = 200 * 1024 * 1024   # 和 application.yml 的 max-file-size 一致
MAX_SECONDS = 60 * 60           # 更长的视频请老师分段上传：本机识别要花时间
CHUNK_MIN, CHUNK_MAX = 30, 60   # 每段 30～60 秒
RATE = 16000                    # 语音识别要 16 kHz 单声道
# SenseVoice：静音检测切出的每一小段最长 15 秒。
# 不限时平均 28 秒一段，最长 47 秒，回答里的时间点会偏差十几秒；限 10 秒切得太碎，句子容易被拦腰切断
SV_MAX_SEGMENT_MS = 15000
# Whisper：提示模型输出简体中文、保留专业词
PROMPT = {
    'zh': ('以下是一段课程讲解，使用简体中文和标点。', '本节会讲到：', '、', '。'),
    'en': ('The following is a course lecture, with punctuation.', 'Terms in this lecture: ', ', ', '.'),
}


def probe(path):
    """返回 (字节数, 时长秒)；读不出时长的视频按不合格处理"""
    size = os.path.getsize(path)
    with av.open(path) as c:
        seconds = c.duration / av.time_base if c.duration else None
        has_audio = any(s.type == 'audio' for s in c.streams)
    return size, seconds, has_audio


def check(path):
    """上传时的检查；不合格时返回原因，合格返回 None。
    先看大小、再读视频：超大或损坏的文件在读取时会直接报错，要先拦下来并说清楚原因。"""
    size = os.path.getsize(path)
    if size > MAX_BYTES:
        return f'文件 {size / 1024 / 1024:.0f} MB，超过 200 MB'
    try:
        size, seconds, has_audio = probe(path)
    except av.error.FFmpegError:
        return '文件无法读取，可能不是视频或已经损坏'
    if seconds is None:
        return '读不出视频时长，文件可能损坏'
    if seconds > MAX_SECONDS:
        return f'视频 {seconds / 60:.0f} 分钟，超过 60 分钟，请分段上传'
    if not has_audio:
        return '视频没有声音，取不出讲解内容（画面文字以后用截帧识别处理）'
    return None


def load_audio(path, rate=RATE):
    """取出音轨，转成 16 kHz 单声道（语音识别要的格式）。
    自己解码而不用 faster-whisper 自带的：新版 PyAV 去掉了它用的一个参数，直接调用会报错。"""
    import numpy as np
    resampler = av.AudioResampler(format='s16', layout='mono', rate=rate)
    parts = []
    with av.open(path) as c:
        for frame in c.decode(audio=0):
            for f in resampler.resample(frame):
                parts.append(f.to_ndarray().reshape(-1))
        for f in resampler.resample(None):   # 把重采样器里剩下的取出来
            parts.append(f.to_ndarray().reshape(-1))
    return np.concatenate(parts).astype(np.float32) / 32768.0


# ------------------------------------------------------------------ 识别引擎

# 按课程语言自动选引擎（实测，见 README 4.6 节）：
#   中文：SenseVoice 快 5 倍以上，专业词不用提示也准
#   英文：SenseVoice 认不出 printf、/var/log，句号少，问答错了一题；Whisper 更准
ENGINE_BY_LANG = {'zh': 'sensevoice', 'en': 'whisper'}


def pick_engine(engine, lang):
    """engine 为 auto 时按语言选；老师或管理员指定了就用指定的"""
    return ENGINE_BY_LANG.get(lang, 'sensevoice') if engine == 'auto' else engine


# 说话时的语气词和口吃式重复。SenseVoice 会一字不落地记下来（实测 10 分钟 77 个“嗯呃啊”），
# 它们对理解内容没有用，还会干扰检索。
FILLER = re.compile(r'[嗯呃]+[，,]?|(?<=[，。！？,.!?])\s*啊[，,]?|^啊[，,]?|\b(?:uh|um|erm)\b,?\s*', re.I)
# 只合并这些字的连续重复（“你你”“就就”“叫叫”）；“谢谢”“看看”“常常”这类正常叠词不动
STUTTER = re.compile(r'([你我他她它就叫是在有那这对])\1+')


def clean_spoken(text):
    t = FILLER.sub('', text)
    t = STUTTER.sub(r'\1', t)
    t = re.sub(r'[，,]{2,}', '，', t)
    return re.sub(r'^[，,。\s]+', '', t).strip()


def transcribe_sensevoice(audio, lang, terms):
    """
    SenseVoice（阿里 FunAudioLLM，开源）。和 Whisper 不同，它一次输出整句，不是一个字一个字生成，所以快。
    先用 fsmn-vad 找出有人说话的小段（每段带时间，最长 15 秒），再逐段识别。
    专业词提示（terms）这个引擎用不上：实测不给提示，中文专业词也和 Whisper 加提示后一样好。
    """
    from funasr import AutoModel
    t0 = time.time()
    vad = AutoModel(model='funasr/fsmn-vad', hub='hf', disable_update=True, device='cpu',
                    max_single_segment_time=SV_MAX_SEGMENT_MS)
    asr = AutoModel(model='FunAudioLLM/SenseVoiceSmall', hub='hf', disable_update=True, device='cpu')
    load = time.time() - t0
    t1 = time.time()
    spans = vad.generate(input=audio, fs=RATE)[0]['value']        # [[开始毫秒, 结束毫秒], ...]
    pieces = [audio[int(s / 1000 * RATE):int(e / 1000 * RATE)] for s, e in spans]
    res = asr.generate(input=pieces, fs=RATE, language=lang, use_itn=True, batch_size=16) if pieces else []
    segments = []
    for (s, e), r in zip(spans, res):
        text = clean_spoken(re.sub(r'<\|[^|]*\|>', '', r['text']))   # 去掉 <|zh|><|NEUTRAL|> 这类标记
        if text:
            segments.append({'start': round(s / 1000, 1), 'end': round(e / 1000, 1), 'text': text})
    return segments, {'engine': 'sensevoice', 'model': 'SenseVoiceSmall + fsmn-vad', 'language': lang,
                      'model_load_sec': round(load, 1), 'transcribe_sec': round(time.time() - t1, 1)}


def transcribe_whisper(audio, lang, terms, model_size='small'):
    """Whisper（备用）：一个字一个字生成，比 SenseVoice 慢 3～4 倍；可以用专业词提示"""
    from faster_whisper import WhisperModel
    t0 = time.time()
    model = WhisperModel(model_size, device='cpu', compute_type='int8')
    load = time.time() - t0
    t1 = time.time()
    base, lead, sep, end = PROMPT[lang]
    # 把专业词放进提示：识别时更倾向写成这些词，专业词错得少（见 README 的对比）
    prompt = base + (lead + sep.join(terms) + end if terms else '')
    it, info = model.transcribe(audio, language=lang, vad_filter=True, initial_prompt=prompt, beam_size=5)
    segments = [{'start': round(s.start, 1), 'end': round(s.end, 1), 'text': s.text.strip()} for s in it]
    return segments, {'engine': 'whisper', 'model': model_size, 'language': info.language,
                      'model_load_sec': round(load, 1), 'transcribe_sec': round(time.time() - t1, 1)}


# ------------------------------------------------------------------ 分段

def chunk(segments):
    """把识别出的句子按时间合并：攒够 30 秒且句子结束就成一段，最长 60 秒"""
    chunks, cur = [], None
    for s in segments:
        if cur is None:
            cur = {'start': s['start'], 'end': s['end'], 'text': s['text'], 'last': s}
        else:
            cur['end'] = s['end']
            cur['text'] = join(cur['text'], s['text'])
            cur['last'] = s
        length = cur['end'] - cur['start']
        if length >= CHUNK_MAX or (length >= CHUNK_MIN and cur['text'][-1:] in '。！？.!?'):
            chunks.append(cur)
            cur = None
    if cur:
        chunks.append(cur)
    return chunks


def join(a, b):
    """英文句子之间要空格，中文不要"""
    return a + (' ' if a[-1:].isascii() and b[:1].isascii() and a and b else '') + b


def build_chunks(segments, kps=()):
    """
    分段，再做两件事：
      - 重叠：每段带上前一段的最后一句（overlap），答案跨在两段交界上时检索也能拿全。
        重叠的句子单独存放，这一段自己的开始时间（at）不变，标出处时还是准的。
      - 标知识点：这一段自己的文字讲到了哪些知识点（名称来自这一章的知识点，见 text_utils.kp_match）
    """
    out = []
    raw = chunk(segments)
    common = shared_tokens(kps)   # 大多数知识点都有的词（如“操作系统”）不参与匹配
    for i, c in enumerate(raw):
        prev = raw[i - 1]['last'] if i > 0 else None
        out.append({
            'id': i + 1, 'start': c['start'], 'end': c['end'], 'at': mmss(c['start']), 'text': c['text'],
            'overlap': {'at': mmss(prev['start']), 'text': prev['text']} if prev else None,
            'knowledge_points': [k for k in kps if kp_match(k, c['text'], common)],
        })
    return out


def mmss(t):
    return f'{int(t // 60):02d}:{int(t % 60):02d}'


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('video')
    ap.add_argument('--chapter', type=int, required=True, help='属于哪一章（chapter.id）')
    ap.add_argument('--out', required=True)
    ap.add_argument('--engine', default='auto', choices=['auto', 'sensevoice', 'whisper'],
                    help='识别引擎：auto 按语言选（中文 SenseVoice，英文 Whisper）；也可以手动指定')
    ap.add_argument('--model', default='small', help='只对 Whisper 有用：tiny / base / small / medium')
    ap.add_argument('--terms', default='', help='专业词，逗号分隔（只对 Whisper 有用，即英文课）。系统里由章节标题和知识点名称自动生成')
    ap.add_argument('--lang', default='zh', choices=['zh', 'en'], help='讲解用的语言；系统里按课程设置')
    ap.add_argument('--kps', default='', help='这一章的知识点名称，分号分隔；系统里从 knowledge_point 表取')
    ap.add_argument('--from-json', action='store_true',
                    help='video 参数给的是已有的识别结果：不重新识别，只重新分段、标知识点')
    a = ap.parse_args()
    kps = [k.strip() for k in a.kps.replace('；', ';').split(';') if k.strip()]

    if a.from_json:
        result = json.load(open(a.video, encoding='utf-8'))
        result['knowledge_points'] = kps
        result['chunks'] = build_chunks(result['segments'], kps)
        with open(a.out, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        print_chunks(result['chunks'])
        return

    problem = check(a.video)
    if problem:
        print('不处理：' + problem)
        sys.exit(2)
    size, seconds, _ = probe(a.video)
    print(f'检查通过：{size / 1024 / 1024:.1f} MB，{mmss(seconds)}')

    terms = [t.strip() for t in a.terms.replace('，', ',').split(',') if t.strip()]
    audio = load_audio(a.video)
    engine = pick_engine(a.engine, a.lang)
    print(f'识别引擎：{engine}' + ('（按课程语言自动选）' if a.engine == 'auto' else '（手动指定）'))
    if engine == 'sensevoice':
        segments, meta = transcribe_sensevoice(audio, a.lang, terms)
    else:
        segments, meta = transcribe_whisper(audio, a.lang, terms, a.model)
    chunks = build_chunks(segments, kps)
    asr = meta['transcribe_sec']

    result = {
        'source': os.path.basename(a.video),
        'chapter_id': a.chapter,
        'duration_sec': round(seconds, 1),
        **meta,
        'terms': terms,
        'knowledge_points': kps,
        'speed': round(seconds / asr, 2) if asr else None,   # 几倍速：>1 表示比视频本身短
        'segments': segments,
        'chunks': chunks,
        'status': '待老师校对',
    }
    if not segments:
        # 静音检测把整段都过滤掉了：视频只有背景音乐或没人讲话。
        # 不能关掉过滤硬识别——Whisper 遇到纯音乐会编出“字幕by……”这类不存在的句子。
        result['status'] = '没有识别到讲解声音（可能只有背景音乐），需要截帧识别画面文字，或请老师补文字说明'
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    if not segments:
        print('没有识别到讲解声音：' + result['status'])
        return
    print(f"识别完成（{meta['engine']}）：{len(segments)} 句，合并成 {len(chunks)} 段；识别耗时 {asr:.0f} 秒"
          f"（{result['speed']} 倍速），模型加载 {meta['model_load_sec']:.0f} 秒")
    print_chunks(result['chunks'])


def print_chunks(chunks):
    for c in chunks:
        tags = ('  〔' + '、'.join(c['knowledge_points']) + '〕') if c.get('knowledge_points') else ''
        print(f"  [{c['at']}] {c['text'][:50]}{tags}")


if __name__ == '__main__':
    main()
