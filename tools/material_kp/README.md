# 从资料识别知识点

> 模块一“资料处理”的一部分：老师上传的课件和视频 → 文字 → 大模型识别本章知识点 → 知识点草稿。
> 设计见 `docs/模块说明/设计方案-费曼式AI对话.md` 第三、四节。

## 已接进平台（2026-11-24）

```
老师上传视频 / PDF（原来的上传接口）
  → 后端马上排队（material_job），在后台线程调本机的资料处理服务 service.py（常驻，模型只加载一次）
      PDF：本机取文字 + DeepSeek 看图；视频：本机语音识别；txt / md：后端直接读
  → 文字存进 material_text（课件一页、一张图、视频 30～60 秒一段）
老师点“上传完毕”（chapter_kp_state.upload_done）
  → 这一章的资料都处理好，就自动识别知识点（后端 KpRecognizer，用平台的 AI 客户端，费用记在 AI 调用记录里）
  → 草稿存进 chapter_kp，出处编号程序核对过（D1-P4 = 第 1 份文档第 4 页，V2-03 = 第 2 个视频第 3 段）
  → 老师在“知识点”里修改、确认 → 同步到 knowledge_point；学生在章节介绍里看到知识点地图
```

- 启动处理服务：`python tools/material_kp/service.py --port 8091`（`.claude/launch.json` 里是 `se-material`）。后端地址配置 `platform.material.service-url`，默认 `http://127.0.0.1:8091`。服务没启动时，资料会标“失败：资料处理服务没有启动”。
- 后端代码：`backend/src/main/java/com/se/platform/material/`；测试 `MaterialKpTest`（假的处理服务和假的 AI，不联网）。
- 提示词：`backend/src/main/resources/prompts/kp_extract_v1.md`（由下面原型的 `kp_prompt_v2.md` 改编号而来）。

## 原型脚本（验证用）

| 文件 | 作用 |
|---|---|
| `pdf_to_text.py` | 第 1 步：课件 PDF → 按页的文字（用 PyMuPDF） |
| `../video_to_text/video_to_text.py` | 第 2 步：视频 → 带时间点的文字（沿用原有原型） |
| `asr_term_check.py` | 第 2 步的准确率：用课件里的专业词检查视频转文字 |
| `extract_kps.py` | 第 3 步：交给大模型识别知识点，自动核对出处、跑几次看稳定性，每一步存档 |
| `kp_prompt_v1.md` | 第 3 步的提示词 |
| `compare_with_human.py` | 第 3 步的准确率：和人工列的知识点对比，算查准率、查全率 |
| `验证结果/` | 每次运行的完整记录；第 6 章的汇总见 `验证结果/第6章-记录.md` |

## 怎么跑（以第 6 章为例）

```bash
python pdf_to_text.py "6 智能测试生成与优化.pdf" --out 验证结果/第6章-课件文字.json
python ../video_to_text/video_to_text.py "6-1 自动化测试.mp4" --chapter 6 --lang zh --out 6-1.json   # 每个视频一次
python asr_term_check.py --terms 验证结果/第6章-专业词.txt --video-json 6-*.json --out 专业词核对.json [--confirmed 人工确认的识别错误.txt]
python extract_kps.py --chapter 6 --title 智能测试生成与优化 --pdf-json 验证结果/第6章-课件文字.json --video-json 6-*.json --runs 3 --out-dir 验证结果/第6章-课件加视频
python compare_with_human.py --human 人工知识点.txt --draft 验证结果/第6章-课件加视频/04_知识点草稿_第1次.json --out 人工对比.json
```

- 需要：Python 3.12，PyMuPDF（读 PDF），视频转文字的依赖见 `../video_to_text/README.md`。
- API key：读环境变量 `LLM_API_KEY`，没有就读 `backend/src/main/resources/application-local.yml`。脚本不会打印 key。
- 每次运行存到**新的**文件夹，不要覆盖旧记录。
