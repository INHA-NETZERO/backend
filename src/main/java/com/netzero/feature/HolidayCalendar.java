package com.netzero.feature;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.MonthDay;

/**
 * Identifies Korean public holidays.
 * Includes fixed holidays (solar) and lunar holidays (approximated with fixed dates for 2026).
 */
@Component
public class HolidayCalendar {

    /**
     * Returns true if the given date is a Korean public holiday (excluding weekends).
     * Weekends are handled separately via dayOfWeek feature.
     *
     * Fixed holidays (solar calendar):
     * - 1월1일 (신정, New Year's Day)
     * - 3월1일 (삼일절, Independence Movement Day)
     * - 5월5일 (어린이날, Children's Day)
     * - 8월15일 (광복절, Liberation Day)
     * - 10월3일 (개천절, National Foundation Day)
     * - 10월9일 (한글날, Hangeul Day)
     * - 12월25일 (성탄절, Christmas)
     *
     * Lunar holidays (approximated as fixed dates for 2026):
     * - 설날 (Lunar New Year): 2026-01-29 to 2026-01-31
     * - 추석 (Chuseok / Mid-Autumn Festival): 2026-09-16 to 2026-09-18
     */
    public boolean isHoliday(LocalDate date) {
        int month = date.getMonthValue();
        int dayOfMonth = date.getDayOfMonth();
        int year = date.getYear();

        // Fixed solar calendar holidays
        if ((month == 1 && dayOfMonth == 1) ||      // 신정
            (month == 3 && dayOfMonth == 1) ||      // 삼일절
            (month == 5 && dayOfMonth == 5) ||      // 어린이날
            (month == 8 && dayOfMonth == 15) ||     // 광복절
            (month == 10 && dayOfMonth == 3) ||     // 개천절
            (month == 10 && dayOfMonth == 9) ||     // 한글날
            (month == 12 && dayOfMonth == 25)) {    // 성탄절
            return true;
        }

        // Lunar holidays (hardcoded for 2026)
        if (year == 2026) {
            // 설날: 2026-01-29 to 2026-01-31
            if (month == 1 && dayOfMonth >= 29 && dayOfMonth <= 31) {
                return true;
            }
            // 추석: 2026-09-16 to 2026-09-18
            if (month == 9 && dayOfMonth >= 16 && dayOfMonth <= 18) {
                return true;
            }
        }

        return false;
    }
}
