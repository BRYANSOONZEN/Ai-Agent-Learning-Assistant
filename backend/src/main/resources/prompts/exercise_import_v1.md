下面是老师已经准备好的一些题目（从文件里读出来的文字，格式可能有点乱）。请把它们整理成结构化的题目。

**不要改题目的内容**：题干、选项、答案按原文抄，只整理格式；原文没有的答案不要编，看不出答案的题 confident 写 false。

这一章的知识点：
{{kps}}

给每道题标：
- kp_no：属于上面第几个知识点；
- assess_type：concept 概念辨析 / scenario 情境应用 / data 数据分析 / process 流程步骤；
- why：为什么这样标，一句话；
- confident：知识点、考查类型、答案都有把握写 true，否则 false。

题型和写法：
- single_choice 单选：options、answer 一个字母。
- multiple 多选：answer 几个字母，如 "AC"。
- judge 判断：options 写 ["正确","错误"]，answer "A" 表示正确、"B" 表示错误。
- two_tier 两段式（原文有“选答案再选理由”的才用）：再加 reasons、reason_answer。
- fill 填空：accepts 写原文的答案，原文有几个说法就写几个。
- short_answer 简答：reference 写原文的参考答案；key_points 把参考答案拆成 2～4 条要点。
原文有解析就放进 analysis。

只输出 JSON：
{"questions": [{"type": "single_choice", "kp_no": 1, "assess_type": "concept", "why": "...", "confident": true, "stem": "...", "options": ["..."], "answer": "A", "option_notes": [], "reasons": [], "reason_answer": "", "reason_notes": [], "accepts": [], "reference": "", "key_points": [], "analysis": "", "difficulty": 2}]}

【老师的题目原文】
{{text}}
