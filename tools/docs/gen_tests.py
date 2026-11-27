# -*- coding: utf-8 -*-
"""从 backend/src/test 生成测试清单（Markdown）：每个测试类的说明（类上的 Javadoc）和每个 @Test 方法
（方法名 + 方法体里第一行注释）。和最近一次 mvnw test 的结果（target/surefire-reports）对上数字。
用法：python tools/docs/gen_tests.py"""
import glob
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "backend")
TEST = os.path.join(ROOT, "src", "test", "java", "com", "se", "platform")


def class_doc(src):
    m = re.search(r"/\*\*(.*?)\*/\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:public\s+)?(?:final\s+)?class", src, re.S)
    if not m:
        return ""
    t = re.sub(r"^\s*\*\s?", "", m.group(1), flags=re.M)
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>|\{@\w+ ([^}]*)\}", r"\1", t)).strip()


def tests(src):
    out = []
    lines = src.split("\n")
    for i, l in enumerate(lines):
        if l.strip() != "@Test":
            continue
        j = i + 1
        while not re.search(r"void\s+\w+\s*\(", lines[j]):
            j += 1
        name = re.search(r"void\s+(\w+)\s*\(", lines[j]).group(1)
        note = ""
        # 方法上面的 /** */ 或方法体里第一行 // 注释
        k = i - 1
        if lines[k].strip().endswith("*/"):
            s = k
            while "/**" not in lines[s]:
                s -= 1
            note = " ".join(re.sub(r"^\s*(/\*\*|\*/|\*)\s?", "", x).replace("*/", "") for x in lines[s:k + 1]).strip()
        if not note:
            end = next((q for q in range(j + 1, len(lines)) if lines[q].strip() == "@Test"), len(lines))
            for x in lines[j + 1:end]:
                c = x.strip()[2:].strip() if x.strip().startswith("//") else ""
                if c and not c.startswith("---"):
                    note = c
                    break
        out.append((name, re.sub(r"\s+", " ", note)))
    return out


def surefire():
    total = fail = 0
    for f in glob.glob(os.path.join(ROOT, "target", "surefire-reports", "*.txt")):
        m = re.search(r"Tests run: (\d+), Failures: (\d+), Errors: (\d+)", open(f, encoding="utf-8").read())
        if m:
            total += int(m.group(1))
            fail += int(m.group(2)) + int(m.group(3))
    return total, fail


# 没有注释的测试：按方法名写一句中文说明（方法名本身就是“情况_结果”）
NAMES = {
    "missingBrokenOrTamperedTokenIs401": "没带令牌、令牌坏了、被改过（例如把角色改成 teacher）：401",
    "expiredTokenIs401WithAClearMessage": "令牌过期：401，并提示重新登录",
    "currentUserComesFromTheToken": "当前用户来自令牌：不同老师看到的是各自的课",
    "everyEndpointHasAnAccessRule": "每个接口在权限表里都有规则",
    "studentsCanRegisterButNeverAsTeacher": "学生可以注册，但不能注册成老师",
    "fiveWrongPasswordsLockTheAccountUntilAdminResets": "连续输错 5 次锁定，锁住后密码对了也不行，管理员重置后恢复",
    "noStartedChapter_emptySummary": "还没开始学任何章节：小助手总结是空的",
    "twoRoundsWrong_escalatedToTeacher": "补练两轮都没补回来：不再出题，建议回到 AI 对话再讲一次",
    "rightPasswordReturnsUserWithoutHash": "密码正确：返回用户信息，不带密码哈希",
    "wrongPasswordAndUnknownUserGetTheSameMessage": "密码错和用户不存在返回同一句提示（不泄露用户名是否存在）",
    "blankFieldsAreRejected": "用户名或密码为空被拒",
    "loginGivesATokenThatIdentifiesTheUser": "登录得到的令牌能识别出是谁",
    "eachRoleOnlyReachesItsOwnEndpoints": "每种角色只能调自己的接口，其他返回 403",
    "disablingAnAccountCutsOffItsTokenImmediately": "禁用账号后旧令牌立即失效",
    "adminCreatesTeachersButCannotTouchAdmins": "管理员能开通老师，但不能禁用或改管理员",
    "seededChapterHasContent": "演示数据的章节内容完整（内容项、知识点、题目）",
    "importPastedQuestions": "老师粘贴的题目文字能识别成题目草稿",
    "hello_returnsUnifiedResult": "接口返回统一格式 {code, message, data}",
    "businessException_becomesCode404": "业务异常转成 code 404",
    "wrongParamType_becomesCode400": "参数类型错误转成 code 400",
    "unknownPath_becomesCode404": "不存在的路径返回 404",
    "modelSaysCorrect_withOnePoint_doesNotFinish": "（旧版对话）模型说对了，但只答到一条要点：不结束",
    "transferNotCompleteTwice_isRevealed_showsReferenceAndSummary": "（旧版对话）变式题两次不完整：给出讲解，显示参考答案和总结",
    "transferRefused_threeTimes_endsWithoutAnswer": "（旧版对话）变式题拒答三次：结束，不给答案",
    "neverAttempted_askingThreeTimes_skipsWithoutAnswer": "（旧版对话）从没尝试、要答案三次：跳过，不给答案",
    "struggling_afterAttempt_raisesHintLevel": "（旧版对话）尝试过但卡住：提示升一级",
    "irrelevant_usesModelReply_twiceSkipsWithoutAnswer": "（旧版对话）无关的话：用模型的回复；两次就跳过，不给答案",
    "answerInsideChat_countsAsAnswer_notIrrelevant": "（旧版对话）闲聊里带着答案：算作回答，不算无关",
    "promptOnlyHasCurrentTopic": "（旧版对话）发给模型的提示词里只有当前话题",
    "topicWithoutTransfer_answerShownOnlyAfterOwnWords": "（旧版对话）没有变式题的话题：学生先说了自己的话才显示答案",
    "wholeDialogue_summaryShowsNewResults": "（旧版对话）完整走一遍：总结显示新的结果",
    "correct_firstTry_isSelfCorrect": "（旧版规则）第一次就对：记为自己答出",
    "correct_afterWrong_isHintCorrect": "（旧版规则）错了之后答对：记为提示后答出",
    "correct_afterStuck_isHintCorrect": "（旧版规则）卡住之后答对：记为提示后答出",
    "newPoint_keepsAsking_withoutRaisingLevel": "（旧版规则）答到新要点：继续追问，不升提示级别",
    "noProgress_goesL1L2L3_thenGivesPoint": "（旧版规则）一直没进展：L1 → L2 → L3，然后给出要点",
    "lastMissingPointAnswered_afterGiven_startsTransfer": "（旧版规则）最后缺的要点也答出：进入变式题",
    "wantAnswer_afterAttempt_raisesLevel_neverEnds": "（旧版规则）尝试过再要答案：升一级提示，不结束",
    "wantAnswer_neverAttempted_thirdTimeSkips_withoutAnswer": "（旧版规则）从没尝试、第三次要答案：跳过，不给答案",
    "struggling_neverAttempted_raisesHintToL3_noPoints_noSkip": "（旧版规则）从没尝试但卡住：提示升到 L3，不给要点、不跳过",
    "struggling_neverAttempted_skipsOnlyAtTurnLimit": "（旧版规则）从没尝试：到轮数上限才跳过",
    "struggling_afterAttempt_escalatesToPoints": "（旧版规则）尝试过仍卡住：升级到给出要点",
    "transfer_struggling_keepsHinting_doesNotCountAsRefusal": "（旧版规则）变式题卡住：继续提示，不算拒答",
    "irrelevantOnce_continues_twice_skips": "（旧版规则）无关一次继续，两次跳过",
    "offTopicAndClarify_continue_withoutRaisingLevel": "（旧版规则）跑题、追问澄清：继续，不升级",
    "turnLimit_attempted_goesToTransfer": "（旧版规则）到轮数上限且尝试过：进入变式题",
    "turnLimit_neverAttempted_skips": "（旧版规则）到轮数上限且从没尝试：跳过",
    "transfer_complete_isRevealedApplied": "（旧版规则）变式题答完整：记为讲解后会用",
    "transfer_notCompleteTwice_isRevealed": "（旧版规则）变式题两次不完整：记为讲解后仍不会",
    "transfer_refusingThreeTimes_endsWithoutAnswer": "（旧版规则）变式题拒答三次：结束，不给答案",
    "transfer_turnLimit_withoutAttempt_endsWithoutAnswer": "（旧版规则）变式题到上限且没尝试：结束，不给答案",
    "noTransferQuestion_oneAttemptEnds_asRevealed": "（旧版规则）没有变式题：尝试一次就结束，记为讲解后",
    "pauseAfterTwoSkippedTopics": "（旧版规则）连续跳过两个话题：暂停",
    "draft_acceptsJsonWrappedInCodeFence": "（旧版话题）模型把 JSON 包在代码块里也能解析",
    "draft_badJson_returns503": "（旧版话题）模型返回的 JSON 坏了：返回 503",
    "draft_missingAnswer_returns400": "（旧版话题）没填参考答案就起草：返回 400",
    "seededTopics_areListedInOrder": "（旧版话题）演示数据的话题按顺序列出",
    "saveThenDelete": "（旧版话题）保存后删除",
    "speakerPrefixStripped": "AI 对话草稿里句子开头的“小柯说：”被去掉",
    "beforeDeadline_waiting": "还没到截止：全班分析显示等待",
    "ignoreCard": "老师对一张问题卡片选“先不处理”",
    "voidAnalysis_remindMissing_extendDeadline": "交卷不到 80%：分析作废、提醒没交的学生、可以延期",
    "revealedApplied_doesNotRaiseB": "（个人干预）讲解后会用：不产生 B 类预警",
    "revealedStillWrong_raisesB": "（个人干预）讲解后仍不会：产生 B 类预警",
    "skippingWithoutEarlierAnswers_raisesB_andEvidenceSaysSkipped": "（个人干预）没答过就跳过：产生 B 类预警，依据写“跳过”",
    "skippingAfterGoodAnswers_keepsEarlierResult_noAlert": "（个人干预）答得好之后跳过：保留之前的结果，不预警",
    "reviewB_redoEndsRevealedApplied_isEffective": "（个人干预）老师审核 B 类后重做，讲解后会用：干预有效",
    "exercise_needsDialogue_evenIfMaterialsDone": "资料都学完了，练习仍要等 AI 对话完成",
    "exercise_withoutDialogue_needsMaterials": "没有 AI 对话的章节：资料学完练习就解锁",
}


def main():
    files = sorted(glob.glob(os.path.join(TEST, "**", "*Test.java"), recursive=True))
    rows, n = [], 0
    for f in files:
        src = open(f, encoding="utf-8").read()
        ts = tests(src)
        if not ts:
            continue
        n += len(ts)
        rel = os.path.relpath(f, TEST).replace("\\", "/")
        rows.append("### %s（%d 个）\n\n%s\n\n源码：`backend/src/test/java/com/se/platform/%s`\n\n| 测试方法 | 测什么 |\n|---|---|\n%s\n"
                    % (os.path.basename(f)[:-5], len(ts), class_doc(src) or "—", rel,
                       "\n".join("| `%s` | %s |" % (a, (NAMES.get(a) or b or "见方法名").replace("|", "\\|")) for a, b in ts)))
    run, bad = surefire()
    print("共 %d 个测试类、%d 个测试方法。最近一次 `mvnw test`：运行 %d 个，失败 %d 个。\n" % (len(rows), n, run, bad))
    print("\n".join(rows))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
