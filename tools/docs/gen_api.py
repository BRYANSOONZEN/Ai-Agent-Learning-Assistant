# -*- coding: utf-8 -*-
"""从后端 Controller 源码生成接口清单（Markdown），保证《接口设计》和代码一致。
每个接口：方法、路径、谁能调（按 AccessRules 同样的规则算）、说明（取方法上的 Javadoc）。
用法：python tools/docs/gen_api.py > 输出.md"""
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend", "src", "main", "java", "com", "se", "platform")

# 和 AccessRules.RULES 同顺序（第一条匹配的生效）
RULES = [
    ("/api/auth/login", "不用登录"), ("/api/auth/register", "不用登录"), ("/api/auth/**", "登录即可"),
    ("/api/hello/**", "登录即可"), ("/api/admin/**", "管理员"), ("/api/ai/logs/**", "管理员"),
    ("/api/learn/**", "学生"), ("/api/dialogue/**", "学生"), ("/api/recommendations/**", "学生"),
    ("/api/assistant/**", "学生"), ("/api/diagnosis/me", "学生"), ("/api/profiles/me", "学生"),
    ("/api/profiles/me/**", "学生"), ("/api/interventions/mine", "学生"), ("/api/materials/**", "学生、老师"),
    ("/api/courses/**", "老师"), ("/api/chapters/**", "老师"), ("/api/chapter-items/**", "老师"),
    ("/api/chapter-kps/**", "老师"), ("/api/knowledge-points/**", "老师"), ("/api/exercises/**", "老师"),
    ("/api/teacher/**", "老师"), ("/api/diagnosis/**", "老师"), ("/api/profiles/**", "老师"),
    ("/api/interventions/**", "老师"), ("/api/class-analysis/**", "老师"), ("/api/class-issues/**", "老师"),
    ("/api/class-pushes/**", "老师"), ("/api/alerts/**", "老师"),
]


def who(path):
    p = re.sub(r"\{[^}]+\}", "x", path)
    for pat, role in RULES:
        if pat.endswith("/**"):
            base = pat[:-3]
            if p == base or p.startswith(base + "/"):
                return role
        elif p == pat:
            return role
    return "（无规则，拒绝）"


MAP = re.compile(r'@(Get|Post|Put|Delete|Patch)Mapping(?:\((?:value\s*=\s*)?"([^"]*)"[^)]*\)|\(\)|)')
CLASS_MAP = re.compile(r'@RequestMapping\("([^"]*)"\)')


def javadoc_before(lines, i):
    """第 i 行（注解）上面的 Javadoc / 行注释，合成一行"""
    j = i - 1
    while j >= 0 and lines[j].strip().startswith("@"):
        j -= 1
    if j >= 0 and lines[j].strip().endswith("*/"):
        k = j
        while k >= 0 and "/**" not in lines[k]:
            k -= 1
        text = " ".join(re.sub(r"^\s*(/\*\*|\*/|\*)\s?", "", l).replace("*/", "").strip() for l in lines[k:j + 1])
        return re.sub(r"\s+", " ", text).strip()
    return ""


def clean(desc, method, path):
    # 去掉注释开头重复的 “GET /api/xxx”
    d = re.sub(r"^(GET|POST|PUT|DELETE|PATCH)\s+/api[\w/{}?=&.,\-]*\s*", "", desc)
    d = re.sub(r"<[^>]+>", "", d)
    return d.replace("|", "\\|").strip("：: ") or "—"


def controllers():
    out = []
    for dp, _, fs in os.walk(ROOT):
        for f in fs:
            if not f.endswith(".java"):
                continue
            src = open(os.path.join(dp, f), encoding="utf-8").read()
            if "@RestController" not in src or f in ("PlatformApplication.java", "GlobalExceptionHandler.java"):
                continue
            out.append((os.path.relpath(os.path.join(dp, f), ROOT).replace("\\", "/"), src))
    return sorted(out)


# 代码里没写注释的接口：按方法和路径补一句说明
FALLBACK = {
    ("GET", "/api/ai/logs"): "AI 调用记录列表和合计（耗时、token、费用），?limit=100",
    ("GET", "/api/admin/users"): "用户列表（分页，可按角色、关键字筛选）",
    ("GET", "/api/courses"): "老师自己的课程列表（分页、搜索）",
    ("POST", "/api/courses"): "新建课程",
    ("GET", "/api/courses/{id}"): "课程详情（只能看自己的课）",
    ("PUT", "/api/courses/{id}"): "修改课程信息",
    ("DELETE", "/api/courses/{id}"): "删除课程",
    ("POST", "/api/chapters"): "新建章节",
    ("GET", "/api/chapters/{id}"): "章节详情",
    ("PUT", "/api/chapters/{id}"): "修改章节（标题、介绍）",
    ("DELETE", "/api/chapters/{id}"): "删除章节",
    ("GET", "/api/chapters/{id}/knowledge-points"): "这一章的知识点列表",
    ("PUT", "/api/chapter-items/{id}"): "修改内容项（标题、说明、截止时间、是否允许补交）",
    ("DELETE", "/api/chapter-items/{id}/file"): "删掉视频 / 文档项的文件",
    ("POST", "/api/knowledge-points"): "新建知识点",
    ("PUT", "/api/knowledge-points/{id}"): "修改知识点",
    ("DELETE", "/api/knowledge-points/{id}"): "删除知识点",
    ("POST", "/api/exercises"): "新建题目（老师手动出题）",
    ("PUT", "/api/exercises/{id}"): "修改题目",
    ("DELETE", "/api/exercises/{id}"): "删除题目",
    ("POST", "/api/chapters/{id}/kps"): "老师手动加一个识别结果里的知识点",
    ("PUT", "/api/chapter-kps/{id}"): "修改识别出的知识点（名称、要点、误解、重点 / 一般）",
    ("DELETE", "/api/chapter-kps/{id}"): "删除识别出的知识点",
    ("POST", "/api/chapter-items/{id}/feynman/confirm"): "老师确认 AI 对话草稿，确认后学生才能用",
    ("GET", "/api/learn/feynman/{sessionId}"): "取一次 AI 对话（费曼式）的当前状态和消息",
    ("GET", "/api/learn/deadlines"): "学生自己快到截止 / 已过截止、还没完成的项",
    ("GET", "/api/teacher/deadlines"): "老师：每个有截止时间的项，谁做完了、谁没做、谁迟交",
    ("GET", "/api/class-pushes/{id}"): "一条全班推送的详情：补充讲解、新题、推给哪些学生、推送前正确率、效果",
}


def main():
    total = 0
    sections = []
    for rel, src in controllers():
        lines = src.split("\n")
        m = CLASS_MAP.search(src)
        prefix = m.group(1) if m else ""
        cls_line = next(i for i, l in enumerate(lines) if re.search(r"public class \w+", l))
        cdoc = javadoc_before(lines, next(i for i in range(cls_line, -1, -1) if lines[i].strip().startswith("@RestController")))
        rows = []
        for i, l in enumerate(lines):
            mm = MAP.search(l.strip())
            if not mm or not l.strip().startswith("@") or "RequestMapping" in l:
                continue
            path = prefix + (mm.group(2) or "")
            desc = clean(javadoc_before(lines, i), mm.group(1).upper(), path)
            if desc == "—":
                desc = FALLBACK.get((mm.group(1).upper(), path), "—")
            rows.append("| %s | `%s` | %s | %s |" % (mm.group(1).upper(), path, who(path), desc))
        if not rows:
            continue
        total += len(rows)
        name = rel.split("/")[-1][:-5]
        sections.append("### %s（%d 个）\n\n%s\n\n源码：`%s`\n\n| 方法 | 路径 | 谁能调 | 说明 |\n|---|---|---|---|\n%s\n"
                        % (name, len(rows), re.sub(r"<[^>]+>", "", cdoc) or "—", rel, "\n".join(rows)))
    print("共 %d 个接口，%d 个 Controller。\n" % (total, len(sections)))
    print("\n".join(sections))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
