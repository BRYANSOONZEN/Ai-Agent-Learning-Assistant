"""
AI 怎么“学”课件：检索，不是训练（原型）。设计见 docs/计划-个人学习助手.md 第 8 节。

做什么：
  1. 读 video_to_text.py 输出的分段文字（每段带时间点）
  2. 选内容：一章文字不长就全部交给模型；很长时按字面重合度检索最相关的 3 段（第一版不用向量检索）
  3. 把选中的内容和问题交给大模型，要求只根据这些内容回答，并标出视频时间点
  4. 内容里没有的，回答“课件里没有讲到”

用法：
  python ask_video.py 结果.json "问题" ["问题" ...]

API key：读环境变量 LLM_API_KEY；没有就读 backend 的 application-local.yml。脚本不会打印 key。
"""
import json
import math
import os
import re
import sys
import time
import urllib.request

BASE_URL = 'https://api.deepseek.com'
MODEL = 'deepseek-flash'   # 和 application.yml 一致：最便宜的型号
LOCAL_YML = os.path.join(os.path.dirname(__file__), '..', '..', 'backend', 'src', 'main', 'resources', 'application-local.yml')
TOP_K = 3                  # 取 2 段时，同分的段会被挤掉（实测“怎么预防”漏了 02:07 那段）
MAX_CONTEXT_CHARS = 3000   # 一章的文字不超过这么多，就全部交给模型，不检索
KP_BONUS = 2.0             # 问题提到某个知识点时，标了这个知识点的段加这么多分（约等于一个少见词的分量）
NL = '\n'

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))   # 用 python -I 运行时，也能找到同目录的 text_utils
from text_utils import kp_match, shared_tokens, tokens  # noqa: E402


def api_key():
    key = os.environ.get('LLM_API_KEY')
    if not key and os.path.exists(LOCAL_YML):
        m = re.search(r'^\s*api-key:\s*(\S+)', open(LOCAL_YML, encoding='utf-8').read(), re.M)
        key = m.group(1).strip('"\'') if m else None   # YAML 里的值可能带引号
    if not key:
        sys.exit('没有 API key：设置环境变量 LLM_API_KEY，或在 application-local.yml 里配置 llm.api-key')
    return key


def full_text(c, use_overlap):
    """检索和交给模型的文字：带上前一段的最后一句（重叠），答案跨在两段交界上时也能拿全"""
    ov = c.get('overlap') if use_overlap else None
    return (ov['text'] + ' ' if ov else '') + c['text']


def retrieve(question, chunks, k=TOP_K, use_overlap=True, use_kp=True):
    """
    选哪些段交给模型：
      - 这一章的文字总共不长（≤ MAX_CONTEXT_CHARS）：全部交给模型，按时间顺序。
        关键词检索对不上同义词（实测：问“怎么预防”，视频里说的是“降低……发生率”，那一段一直检索不到）。
      - 文字很长时才检索：按词重合打分，每个词按“出现在几段里”加权，到处都有的词分量轻；
        问题提到了某个知识点时，标了这个知识点的段加分。
    返回 (选中的段, 方式)
    """
    total = sum(len(c['text']) for c in chunks)
    if total <= MAX_CONTEXT_CHARS:
        return list(chunks), f'全部 {len(chunks)} 段（共 {total} 字）'
    grams = [tokens(full_text(c, use_overlap)) for c in chunks]
    df = {}
    for g in grams:
        for w in g:
            df[w] = df.get(w, 0) + 1
    q = tokens(question)
    n = len(chunks)
    all_kps = {kp for c in chunks for kp in c.get('knowledge_points', [])}
    common = shared_tokens(all_kps)
    asked = {kp for kp in all_kps if kp_match(kp, question, common)} if use_kp else set()

    def score(g, c):
        s = sum(math.log((n + 1) / df[w]) for w in q & g)
        return s + (KP_BONUS if asked & set(c.get('knowledge_points', [])) else 0)

    scored = sorted(((score(g, c), c) for g, c in zip(grams, chunks)), key=lambda x: -x[0])
    picked = sorted([c for sc, c in scored[:k] if sc > 0], key=lambda c: c['start'])
    return picked, f'检索前 {k} 段' + (f'（问题提到知识点：{"、".join(sorted(asked))}）' if asked else '')


def call_model(prompt, key):
    # max_tokens 要给够：这个模型的“思考”也算在输出里，给 300 时实测回答是空的
    body = json.dumps({'model': MODEL, 'messages': [{'role': 'user', 'content': prompt}],
                       'temperature': 0.2, 'max_tokens': 4096}).encode()
    req = urllib.request.Request(BASE_URL + '/chat/completions', data=body, method='POST',
                                 headers={'Content-Type': 'application/json', 'Authorization': 'Bearer ' + key})
    with urllib.request.urlopen(req, timeout=60) as r:
        out = json.loads(r.read())
    return out['choices'][0]['message']['content'].strip(), out.get('usage', {})


def expand_query(question, lang, key):
    """
    检索前让模型把问题改写成视频所用语言的关键词（含同义说法）。解决两个实测问题：
      - 中文问英文课：关键词一个都对不上，什么都检索不到
      - 说法不同：问“预防”，视频里说“降低……发生率”
    只用来检索，不改学生的问题本身。
    """
    name = {'zh': '中文', 'en': '英文'}.get(lang, '中文')
    prompt = NL.join([
        f'下面是学生对一段课程视频的提问。视频是{name}讲的。',
        f'请给出在视频文字里检索用的关键词：用{name}，包括同义说法和相关术语，最多 10 个，用逗号分隔，只输出关键词。',
        '学生的问题：' + question,
    ])
    words, _ = call_model(prompt, key)
    return words


def ask(question, chunks, key, lang='zh'):
    t0 = time.time()   # 从提问开始算：包括改写检索词那一次调用
    picked, how = retrieve(question, chunks)
    if not how.startswith('全部'):   # 走检索时才需要改写
        words = expand_query(question, lang, key)
        picked, how = retrieve(question + ' ' + words, chunks)
        how += f'；检索词：{words}'
    whole = how.startswith('全部')   # 全部给的时候不用重叠，否则同一句会出现两次

    def line(c):
        ov = c.get('overlap')
        prefix = f"（接上一段 {ov['at']}：{ov['text']}）" if ov and not whole else ''
        return f"[{c['at']}] {prefix}{c['text']}"

    context = NL.join(line(c) for c in picked) or '（没有找到相关内容）'
    prompt = NL.join([
        '你是课程学习助手。只根据下面的视频文字回答学生的问题，不要用你自己的知识补充。',
        '回答不超过 80 字，句末用括号标出依据的时间点，例如（视频 01:35）。',
        '视频文字是语音识别的结果，可能有同音错字，按上下文理解。',
        '如果视频文字里没有答案，只回答：课件里没有讲到这个问题。',
        '',
        '视频文字：',
        context,
        '',
        '学生的问题：' + question,
    ])
    answer, usage = call_model(prompt, key)
    if not answer:   # 偶尔会返回空内容：重试一次
        answer, usage = call_model(prompt, key)
    return {'question': question, 'context': how, 'picked': [c['at'] for c in picked],
            'answer': answer or '（模型没有返回内容）', 'latency_ms': int((time.time() - t0) * 1000), 'tokens': usage}


def main():
    data = json.load(open(sys.argv[1], encoding='utf-8'))
    key = api_key()
    for q in sys.argv[2:]:
        print(json.dumps(ask(q, data['chunks'], key, data.get('language', 'zh')), ensure_ascii=False))


if __name__ == '__main__':
    main()
