package com.fc.v2.model.custom;

import java.util.Collections;
import java.util.List;

import com.fc.v2.model.auto.TItemSuperviseRecord;

/**
 * 单办件一回判定的落账结果。
 * <ul>
 *   <li>{@link #REJECTED}：逾期率越界/要件缺失，不落账，退回监察员重录；</li>
 *   <li>{@link #NO_RULE}：受理日翻不出在档线，已落"无从依凭"一笔，不硬给结论；</li>
 *   <li>{@link #GRADED}：评定笔已钉进台账；等级回落时先前的警报另开销警笔，各笔各留各的代号与日子。</li>
 * </ul>
 *
 * @author fuce
 * @date 2026-09-30
 */
public class JudgeResult {

    public static final int REJECTED = 0;
    public static final int NO_RULE = 1;
    public static final int GRADED = 2;

    private final int status;
    private final String message;
    private final List<TItemSuperviseRecord> records;

    private JudgeResult(int status, String message, List<TItemSuperviseRecord> records) {
        this.status = status;
        this.message = message;
        this.records = records == null
                ? Collections.<TItemSuperviseRecord>emptyList() : records;
    }

    public static JudgeResult rejected(String message) {
        return new JudgeResult(REJECTED, message, Collections.<TItemSuperviseRecord>emptyList());
    }

    public static JudgeResult noRule(TItemSuperviseRecord note) {
        return new JudgeResult(NO_RULE, "受理当日无在档可用线，已记无从依凭",
                Collections.singletonList(note));
    }

    public static JudgeResult graded(List<TItemSuperviseRecord> records, String message) {
        return new JudgeResult(GRADED, message, records);
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public List<TItemSuperviseRecord> getRecords() {
        return records;
    }
}
