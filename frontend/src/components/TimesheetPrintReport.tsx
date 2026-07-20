import type { Lang, MonthlyResponse, DailyAttendance } from '../api/types'

/** 분 → 소수 시간(엑셀과 동일 0.00). null은 빈칸. */
function fmtH(min: number | null): string {
  return min === null ? '' : (min / 60).toFixed(2)
}

/**
 * 근무표 인쇄 전용 레이아웃 — Apache POI 엑셀(DefaultAttendanceExcelExporter)과 동일한 양식.
 * 화면에는 보이지 않고(@media print에서만 노출) 인쇄→PDF 시 엑셀과 일치하는 표가 나온다.
 * 상단 머리(발행일·회사명·이름 라벨/값 + 결재란) · 전체 격자 테두리 · 주말/공휴일 행색 · 소수 시간 · 비고 · 합계.
 */
export function TimesheetPrintReport({
  monthly,
  tenantName,
  userName,
  department,
  stampEnabled,
  issueDate,
  lang,
}: {
  monthly: MonthlyResponse
  tenantName: string | null
  userName: string | null
  department: string | null
  stampEnabled: boolean
  issueDate: string
  lang: Lang
}) {
  const L = (ko: string, en: string, ja: string) => (lang === 'ENG' ? en : lang === 'JPN' ? ja : ko)

  const category = (d: DailyAttendance): string => {
    if (d.holiday) return d.holidayName ?? L('공휴일', 'Holiday', '祝日')
    if (d.leaveName) return d.leaveName //승인 휴가(#9)
    return d.dayOff ? L('휴무', 'Off', '休務') : L('근무', 'Work', '勤務')
  }
  const rowClass = (d: DailyAttendance): string => {
    const wd = new Date(d.date).getDay()
    if (d.holiday || wd === 0) return 'tsr-sun'
    if (wd === 6) return 'tsr-sat'
    return ''
  }

  const H = [
    L('날짜', 'Date', '日付'),
    L('구분', 'Type', '区分'),
    L('예정 출근', 'Sched. In', '予定出勤'),
    L('예정 퇴근', 'Sched. Out', '予定退勤'),
    L('실제 출근', 'Actual In', '実出勤'),
    L('실제 퇴근', 'Actual Out', '実退勤'),
    L('예정 근무', 'Sched. Hrs', '予定労働'),
    L('휴식', 'Break', '休憩'),
    L('실제 근무', 'Actual Hrs', '実労働'),
    L('비고', 'Remarks', '備考'),
  ]

  return (
    <div className="ts-report print-only">
      <div className="tsr-head">
        <table className="tsr-meta">
          <tbody>
            <tr><th>{L('발행일', 'Issued', '発行日')}</th><td>{issueDate}</td></tr>
            <tr><th>{L('회사명', 'Company', '会社名')}</th><td>{tenantName ?? ''}</td></tr>
            <tr><th>{L('부서명', 'Dept.', '部署名')}</th><td>{department ?? ''}</td></tr>
            <tr><th>{L('이름', 'Name', '氏名')}</th><td>{userName ?? ''}</td></tr>
          </tbody>
        </table>
        {stampEnabled && (
          <table className="tsr-stamp">
            <tbody>
              <tr>
                <td className="tsr-stamp-label" rowSpan={2}>{L('결재', 'Approval', '決裁')}</td>
                <th>{L('인사담당자', 'HR', '人事担当')}</th>
                <th>{L('총괄담당자', 'Manager', '総括担当')}</th>
              </tr>
              <tr><td /><td /></tr>
            </tbody>
          </table>
        )}
      </div>

      <table className="tsr-table">
        <thead>
          <tr>{H.map((h) => <th key={h}>{h}</th>)}</tr>
        </thead>
        <tbody>
          {monthly.days.map((d) => (
            <tr key={d.date} className={rowClass(d)}>
              <td>{d.date}</td>
              <td>{category(d)}</td>
              <td>{d.scheduleStart ?? ''}</td>
              <td>{d.scheduleEnd ?? ''}</td>
              <td>{d.stampIn ?? ''}</td>
              <td>{d.stampOut ?? ''}</td>
              <td className="num">{fmtH(d.scheduledMinutes)}</td>
              <td className="num">{fmtH(d.recognizedBreakMinutes)}</td>
              <td className="num">{fmtH(d.workMinutes)}</td>
              <td>{d.note ?? ''}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr className="tsr-total">
            <td>{L('합계', 'Total', '合計')}</td>
            <td colSpan={5} />
            <td className="num">{fmtH(monthly.totalScheduledMinutes)}</td>
            <td className="num">{fmtH(monthly.totalBreakMinutes)}</td>
            <td className="num">{fmtH(monthly.totalWorkMinutes)}</td>
            <td />
          </tr>
        </tfoot>
      </table>
    </div>
  )
}
