"""切词和知识点匹配：video_to_text.py（标知识点）和 ask_video.py（检索）共用。"""
import re

# 英文里太常见、不能说明内容的词，不参与匹配
STOP = {'the', 'and', 'for', 'with', 'that', 'this', 'you', 'are', 'what', 'how', 'why', 'use', 'using'}
KP_MATCH_RATIO = 0.6   # 知识点名称里（有区分度的）词，至少有这么多出现在这段文字里，才算讲到了这个知识点


# 中文里的疑问词、虚词组合，说明不了讲的是什么
STOP_ZH = {'什么', '为什', '么要', '怎么', '如何', '是什', '么是', '一个', '我们', '可以', '这个', '就是'}


def tokens(text):
    """中文取相邻两个字，英文取整个单词（小写、去掉常见词）"""
    words = {w for w in re.findall(r'[a-z0-9]+', text.lower()) if len(w) > 2 and w not in STOP}
    t = re.sub(r'[^一-鿿]+', '', text)
    return words | ({t[i:i + 2] for i in range(len(t) - 1)} - STOP_ZH)


def shared_tokens(kp_names):
    """
    三分之一以上知识点名称里都有的词，比如“操作系统”这门课里很多知识点都带“操作系统”。
    这些词出现在哪段都说明不了讲的是哪个知识点，匹配时不算。
    实测：不去掉时，一句“什么是操作系统”被同时标上了“定义、为什么学、历史”三个知识点；
    门槛定在“一半以上”时，7 个知识点里有 3 个带“操作”，还是没去掉。
    """
    names = list(kp_names)
    if len(names) < 3:
        return set()
    count = {}
    for n in names:
        for t in tokens(n):
            count[t] = count.get(t, 0) + 1
    return {t for t, c in count.items() if c >= 2 and c * 3 >= len(names)}


def parts(kp_name):
    """名称由几部分组成时拆开：“课程安排和学术诚信”→ 课程安排、学术诚信；“logging and log levels”→ 两部分"""
    return [p for p in re.split(r'和|与|及|、|，|,|/| and ', kp_name) if p.strip()]


def kp_match(kp_name, text, ignore=frozenset()):
    """
    这段文字是不是讲到了这个知识点：名称原样出现；或者名称的某一部分，有区分度的词 60% 以上都出现了。
    实测：“课程安排和学术诚信”整体算，“学术诚信”全中也只有 4/8，拆开后才标得上。
    """
    if kp_name.lower() in text.lower():
        return True
    have = tokens(text)
    for p in parts(kp_name):
        need = tokens(p) - ignore
        # 只剩一个词的部分（如“程序和状态机”里的“程序”）太常见，不能单独算数。
        # 实测：不加这条时，讲学术诚信的三段因为提到“程序”被标成了“程序和状态机”
        if len(need) >= 2 and len(need & have) / len(need) >= KP_MATCH_RATIO:
            return True
    return False
