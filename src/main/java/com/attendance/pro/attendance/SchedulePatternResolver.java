package com.attendance.pro.attendance;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 반복 패턴 → 특정 날짜의 근무 스케줄 투영(#13).
 * weekIndex = floor((date - anchorMonday)/7) mod cycleWeeks, dow = 1..7(월..일).
 * 슬롯이 있으면 그 날의 {@link WorkSchedule}(휴무 or 근무)로 투영, 없으면 null(호출부에서 미설정=휴무 처리).
 * 모든 스케줄은 시작시간 기준(야간 교대는 crossesMidnight로 종업이 익일).
 */
public final class SchedulePatternResolver {

    private final int cycleWeeks;
    private final long anchorEpochDay;
    private final long userId;
    /** key = weekIndex * 8 + dayOfWeek(1..7) */
    private final Map<Integer, SchedulePatternSlot> slots;

    public SchedulePatternResolver(SchedulePattern pattern, List<SchedulePatternSlot> slotList) {
        this.cycleWeeks = Math.max(1, pattern.cycleWeeks());
        this.anchorEpochDay = pattern.anchorMonday().toEpochDay();
        this.userId = pattern.userId();
        this.slots = new HashMap<>();
        for (SchedulePatternSlot s : slotList) {
            this.slots.put(key(s.weekIndex(), s.dayOfWeek()), s);
        }
    }

    /** 그 날짜의 투영 스케줄(없으면 null → 기본값 폴백). */
    public WorkSchedule resolve(LocalDate date) {
        long weekOffset = Math.floorDiv(date.toEpochDay() - anchorEpochDay, 7L);
        int weekIndex = (int) Math.floorMod(weekOffset, (long) cycleWeeks);
        int dow = date.getDayOfWeek().getValue(); //1..7
        SchedulePatternSlot slot = slots.get(key(weekIndex, dow));
        if (slot == null) {
            return null;
        }
        if (slot.off()) {
            return WorkSchedule.projected(userId, date, null, null, false, true);
        }
        if (slot.startTime() == null || slot.endTime() == null) {
            return null; //불완전 슬롯은 폴백(방어)
        }
        return WorkSchedule.projected(userId, date, slot.startTime(), slot.endTime(),
                slot.crossesMidnight(), false);
    }

    private static int key(int weekIndex, int dayOfWeek) {
        return weekIndex * 8 + dayOfWeek;
    }
}
