package com.attendance.pro.billing;

import java.time.LocalDate;

/** 좌석 변동 이벤트 한 건(seat_change_event) — 변동일 + 변동 후 활성 좌석 수. */
public record SeatEvent(LocalDate eventDate, int activeSeats) {
}
