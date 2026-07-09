package com.attendance.pro.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.MemberDtos.InviteResponse;
import com.attendance.pro.user.MemberDtos.MemberCreateRequest;
import com.attendance.pro.user.MemberDtos.MemberCreateResponse;
import com.attendance.pro.user.MemberDtos.MemberScheduleRequest;
import com.attendance.pro.user.MemberInviteService.InviteOutcome;

/**
 * 멤버 관리 서비스 테스트 — 초대 등록(INV)·삭제(DEL)·스케줄(SCH-U)·마지막 TENANT_ADMIN 보호(ADM).
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long TARGET_ID = 5L;
    private static final long ACTOR_ID = 1L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserTokenService userTokenService;
    @Mock
    private MemberInviteService memberInviteService;

    private MemberService service() {
        return new MemberService(userMapper, userTokenService, memberInviteService);
    }

    private static User user(long userId, Role role, UserStatus status) {
        return new User(userId, TENANT_ID, "user" + userId + "@acme.co.kr", "hash", null,
                "유저" + userId, null, LocalTime.of(9, 0), LocalTime.of(18, 0),
                role, status, false, LocalDateTime.now(), LocalDateTime.now());
    }

    private void expectLastAdminConflict(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException apiException = (ApiException) e;
                    assertThat(apiException.getStatus().value()).isEqualTo(409);
                    assertThat(apiException.getCode()).isEqualTo("LAST_TENANT_ADMIN");
                    assertThat(apiException.getMessageKey()).isEqualTo("member.last-admin");
                });
    }

    @Nested
    @DisplayName("마지막 TENANT_ADMIN 보호")
    class LastAdminGuard {

        @Test
        @DisplayName("ADM-01: 유일한 TENANT_ADMIN 강등은 409 LAST_TENANT_ADMIN")
        void demoteLastAdmin() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(1);

            expectLastAdminConflict(() -> service().updateRole(TENANT_ID, TARGET_ID, Role.MEMBER));
            verify(userMapper, never()).updateRole(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("ADM-02: 유일한 TENANT_ADMIN 비활성은 409 LAST_TENANT_ADMIN")
        void disableLastAdmin() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(1);

            expectLastAdminConflict(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.DISABLED));
            verify(userMapper, never()).updateStatus(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("ADM-03: TENANT_ADMIN 2명이면 1명 강등은 허용(과잉 차단 없음)")
        void demoteWhenTwoAdmins() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE));
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(2);
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of());

            var response = service().updateRole(TENANT_ID, TARGET_ID, Role.MEMBER);

            assertThat(response.role()).isEqualTo(Role.MEMBER);
            verify(userMapper).updateRole(TENANT_ID, TARGET_ID, Role.MEMBER);
        }

        @Test
        @DisplayName("ADM-04: 유일한 TENANT_ADMIN이 자기 자신을 강등/비활성해도 동일 규칙으로 409")
        void selfDemoteAlsoGuarded() {
            //자기 자신도 대상 지정과 동일 경로 — userId만 본인일 뿐 규칙이 같다
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(1);

            expectLastAdminConflict(() -> service().updateRole(TENANT_ID, TARGET_ID, Role.MEMBER));
            expectLastAdminConflict(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.DISABLED));
        }

        @Test
        @DisplayName("ADM-05: TA 2명 중 1명 DISABLED — 남은 활성 1명 강등은 409(카운트 기준은 ACTIVE)")
        void countsOnlyActiveAdmins() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            //DISABLED 관리자는 카운트에서 제외되므로 활성 1명
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(1);

            expectLastAdminConflict(() -> service().updateRole(TENANT_ID, TARGET_ID, Role.MEMBER));
        }

        @Test
        @DisplayName("MEMBER 승격(PROMOTE)은 카운트 검사 없이 허용된다")
        void promoteNotGuarded() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of());

            var response = service().updateRole(TENANT_ID, TARGET_ID, Role.TENANT_ADMIN);

            assertThat(response.role()).isEqualTo(Role.TENANT_ADMIN);
            verify(userMapper, never()).countActiveTenantAdmins(anyLong());
        }
    }

    @Nested
    @DisplayName("상태/역할 변경 검증")
    class ChangeValidation {

        @Test
        @DisplayName("타 테넌트 userId는 404 MEMBER_NOT_FOUND(존재 비노출 — 2중 조건 조회가 null)")
        void crossTenantUserIdNotFound() {
            when(userMapper.findById(TENANT_ID, 999L)).thenReturn(null);

            assertThatThrownBy(() -> service().updateStatus(TENANT_ID, 999L, UserStatus.DISABLED))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(404);
                        assertThat(apiException.getCode()).isEqualTo("MEMBER_NOT_FOUND");
                    });
        }

        @Test
        @DisplayName("PENDING 상태 지정은 400 MEMBER_STATUS_INVALID")
        void pendingStatusRejected() {
            assertThatThrownBy(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.PENDING))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_STATUS_INVALID"));
        }

        @Test
        @DisplayName("INV-07: PENDING 대상의 상태 변경은 400 — ACTIVE 전이는 토큰 경유 단일 경로")
        void pendingTargetStatusChangeRejected() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.PENDING));

            assertThatThrownBy(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.ACTIVE))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(400);
                        assertThat(apiException.getCode()).isEqualTo("MEMBER_STATUS_INVALID");
                    });
            assertThatThrownBy(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.DISABLED))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_STATUS_INVALID"));
            verify(userMapper, never()).updateStatus(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("정지(DISABLED) 시 유효 토큰도 전멸된다(잔존 링크 무력화)")
        void disableInvalidatesTokens() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.DISABLED));
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of());

            service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.DISABLED);

            verify(userTokenService).invalidateAll(TENANT_ID, TARGET_ID);
        }

        @Test
        @DisplayName("SYSTEM_ADMIN 역할 지정은 400 MEMBER_ROLE_INVALID")
        void systemAdminRoleRejected() {
            assertThatThrownBy(() -> service().updateRole(TENANT_ID, TARGET_ID, Role.SYSTEM_ADMIN))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_ROLE_INVALID"));
        }

        @Test
        @DisplayName("SYSTEM_ADMIN 계정을 대상으로 한 비활성/강등은 404(존재 비노출 — 운영사 계정 잠금 차단)")
        void systemAdminTargetHidden() {
            //V4 이관으로 DEFAULT 테넌트에 운영사 계정이 공존하는 상황
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.SYSTEM_ADMIN, UserStatus.ACTIVE));

            assertThatThrownBy(() -> service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.DISABLED))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(404);
                        assertThat(apiException.getCode()).isEqualTo("MEMBER_NOT_FOUND");
                    });
            assertThatThrownBy(() -> service().updateRole(TENANT_ID, TARGET_ID, Role.MEMBER))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));
            verify(userMapper, never()).updateStatus(anyLong(), anyLong(), any());
            verify(userMapper, never()).updateRole(anyLong(), anyLong(), any());
        }

        @Test
        @DisplayName("비활성 → 활성 복귀는 카운트 검사 없이 허용")
        void enableDisabledMember() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.DISABLED))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE));
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of());

            var response = service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.ACTIVE);

            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
            verify(userMapper, never()).countActiveTenantAdmins(anyLong());
        }
    }

    @Nested
    @DisplayName("멤버 초대 등록(INV)")
    class Create {

        private MemberCreateRequest request(String workStart, String workEnd) {
            return new MemberCreateRequest("hong@acme.co.kr", "홍길동", "DEV01", workStart, workEnd);
        }

        @Test
        @DisplayName("INV-01(U): 등록은 항상 MEMBER/PENDING + 초대 발송, initialPassword 부재(계약 자체에 없음)")
        void createRegistersPendingAndSendsInvite() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(7L);
                return 1;
            });
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
            when(memberInviteService.sendInvite(TENANT_ID, 7L, "hong@acme.co.kr", "홍길동", "김관리"))
                    .thenReturn(new InviteOutcome(true, expiresAt));

            MemberCreateResponse response = service().create(TENANT_ID, request(null, null), "김관리");

            assertThat(response.userId()).isEqualTo(7L);
            assertThat(response.role()).isEqualTo(Role.MEMBER);
            assertThat(response.status()).isEqualTo(UserStatus.PENDING);
            assertThat(response.mailSent()).isTrue();
            assertThat(response.inviteExpiresAt()).isEqualTo(expiresAt);
            assertThat(captured[0].getStatus()).isEqualTo(UserStatus.PENDING);
            //플레이스홀더 해시는 BCrypt 형태(사용 불능 — 원문 미보관)
            assertThat(captured[0].getPasswordHash()).startsWith("$2");
        }

        @Test
        @DisplayName("SCH-U-02: workStart/End 미지정은 09:00/18:00으로 저장·응답")
        void createDefaultsWorkSchedule() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(7L);
                return 1;
            });
            when(memberInviteService.sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(new InviteOutcome(true, LocalDateTime.now()));

            MemberCreateResponse response = service().create(TENANT_ID, request(null, null), "김관리");

            assertThat(captured[0].getDefaultWorkStart()).isEqualTo(LocalTime.of(9, 0));
            assertThat(captured[0].getDefaultWorkEnd()).isEqualTo(LocalTime.of(18, 0));
            assertThat(response.workStart()).isEqualTo("09:00");
            assertThat(response.workEnd()).isEqualTo("18:00");
        }

        @Test
        @DisplayName("지정한 workStart/End(10:00~19:00)가 저장·응답에 반영된다")
        void createWithCustomSchedule() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(7L);
                return 1;
            });
            when(memberInviteService.sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(new InviteOutcome(true, LocalDateTime.now()));

            MemberCreateResponse response = service().create(TENANT_ID, request("10:00", "19:00"), "김관리");

            assertThat(captured[0].getDefaultWorkStart()).isEqualTo(LocalTime.of(10, 0));
            assertThat(response.workStart()).isEqualTo("10:00");
            assertThat(response.workEnd()).isEqualTo("19:00");
        }

        @Test
        @DisplayName("SCH-U-01: workStart >= workEnd 등록은 400 WORK_TIME_INVALID_RANGE")
        void invalidWorkRangeRejected() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);

            assertThatThrownBy(() -> service().create(TENANT_ID, request("18:00", "09:00"), "김관리"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(400);
                        assertThat(apiException.getCode()).isEqualTo("WORK_TIME_INVALID_RANGE");
                        assertThat(apiException.getMessageKey()).isEqualTo("member.work-time.invalid-range");
                    });
            verify(userMapper, never()).insert(any(UserCreate.class));
        }

        @Test
        @DisplayName("INV-06: 메일 발송 실패에도 멤버는 생성 유지 + mailSent=false(Tx 분리)")
        void mailFailureStillCreatesMember() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                inv.getArgument(0, UserCreate.class).setUserId(7L);
                return 1;
            });
            //MemberInviteService가 발송 예외를 삼키고 mailSent=false로 돌려주는 계약
            when(memberInviteService.sendInvite(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(new InviteOutcome(false, LocalDateTime.now().plusHours(72)));

            MemberCreateResponse response = service().create(TENANT_ID, request(null, null), "김관리");

            assertThat(response.mailSent()).isFalse();
            verify(userMapper).insert(any(UserCreate.class));
        }

        @Test
        @DisplayName("테넌트 내 이메일 중복(활성 행 기준)은 409 EMAIL_DUPLICATED")
        void duplicateEmailRejected() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(true);

            assertThatThrownBy(() -> service().create(TENANT_ID, request(null, null), "김관리"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(409);
                        assertThat(apiException.getCode()).isEqualTo("EMAIL_DUPLICATED");
                        assertThat(apiException.getMessageKey()).isEqualTo("member.email.duplicated");
                    });
        }

        @Test
        @DisplayName("최초 TENANT_ADMIN 등록: TENANT_ADMIN/PENDING + 플레이스홀더 해시(초기 비밀번호 없음)")
        void registerPendingAdmin() {
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(100L);
                return 1;
            });

            long adminUserId = service().registerPendingAdmin(TENANT_ID, "admin@acme.co.kr", "김관리");

            assertThat(adminUserId).isEqualTo(100L);
            assertThat(captured[0].getRole()).isEqualTo(Role.TENANT_ADMIN);
            assertThat(captured[0].getStatus()).isEqualTo(UserStatus.PENDING);
            assertThat(captured[0].getPasswordHash()).startsWith("$2");
        }
    }

    @Nested
    @DisplayName("초대 재발송(INV-05)")
    class Resend {

        @Test
        @DisplayName("PENDING 대상 재발송은 신규 만료시각과 함께 200 계약")
        void resendToPending() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.PENDING));
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(72);
            when(memberInviteService.sendInvite(eq(TENANT_ID), eq(TARGET_ID), anyString(), anyString(), eq("김관리")))
                    .thenReturn(new InviteOutcome(true, expiresAt));

            InviteResponse response = service().resendInvite(TENANT_ID, TARGET_ID, "김관리");

            assertThat(response.userId()).isEqualTo(TARGET_ID);
            assertThat(response.mailSent()).isTrue();
            assertThat(response.inviteExpiresAt()).isEqualTo(expiresAt);
        }

        @Test
        @DisplayName("ACTIVE/DISABLED 대상 재발송은 409 MEMBER_NOT_PENDING")
        void resendToNonPendingRejected() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE));

            assertThatThrownBy(() -> service().resendInvite(TENANT_ID, TARGET_ID, "김관리"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(409);
                        assertThat(apiException.getCode()).isEqualTo("MEMBER_NOT_PENDING");
                        assertThat(apiException.getMessageKey()).isEqualTo("member.invite.not-pending");
                    });
        }

        @Test
        @DisplayName("INV-09(U): 타 테넌트 userId 재발송은 404(존재 비노출)")
        void resendCrossTenantHidden() {
            when(userMapper.findById(TENANT_ID, 999L)).thenReturn(null);

            assertThatThrownBy(() -> service().resendInvite(TENANT_ID, 999L, "김관리"))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("멤버 삭제(DEL)")
    class Delete {

        @Test
        @DisplayName("DEL-01(U): 소프트 삭제 + 유효 토큰 전멸(같은 Tx)")
        void deleteSoftDeletesAndInvalidatesTokens() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE));

            service().delete(TENANT_ID, ACTOR_ID, TARGET_ID);

            verify(userMapper).softDelete(TENANT_ID, TARGET_ID);
            verify(userTokenService).invalidateAll(TENANT_ID, TARGET_ID);
        }

        @Test
        @DisplayName("DEL-03a: 자기 자신 삭제는 400 MEMBER_SELF_DELETE")
        void selfDeleteRejected() {
            assertThatThrownBy(() -> service().delete(TENANT_ID, ACTOR_ID, ACTOR_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(400);
                        assertThat(apiException.getCode()).isEqualTo("MEMBER_SELF_DELETE");
                        assertThat(apiException.getMessageKey()).isEqualTo("member.delete.self");
                    });
            verify(userMapper, never()).softDelete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("DEL-03b: 마지막 활성 TENANT_ADMIN 삭제는 409 LAST_TENANT_ADMIN")
        void lastAdminDeleteRejected() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.TENANT_ADMIN, UserStatus.ACTIVE));
            when(userMapper.countActiveTenantAdmins(TENANT_ID)).thenReturn(1);

            expectLastAdminConflict(() -> service().delete(TENANT_ID, ACTOR_ID, TARGET_ID));
            verify(userMapper, never()).softDelete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("DEL-03c: SYSTEM_ADMIN 대상/타 테넌트 삭제는 404(존재 비노출)")
        void systemAdminOrCrossTenantHidden() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.SYSTEM_ADMIN, UserStatus.ACTIVE));
            assertThatThrownBy(() -> service().delete(TENANT_ID, ACTOR_ID, TARGET_ID))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));

            when(userMapper.findById(TENANT_ID, 999L)).thenReturn(null);
            assertThatThrownBy(() -> service().delete(TENANT_ID, ACTOR_ID, 999L))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));
            verify(userMapper, never()).softDelete(anyLong(), anyLong());
        }

        @Test
        @DisplayName("PENDING 멤버 삭제는 허용(오송신 수습 경로)")
        void pendingMemberDeletable() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.PENDING));

            service().delete(TENANT_ID, ACTOR_ID, TARGET_ID);

            verify(userMapper).softDelete(TENANT_ID, TARGET_ID);
        }
    }

    @Nested
    @DisplayName("개인 기본 스케줄 수정(SCH-U)")
    class Schedule {

        @Test
        @DisplayName("스케줄 수정은 저장 후 갱신 행을 돌려준다(PENDING 행에도 허용)")
        void updateSchedule() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.PENDING));
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of());

            service().updateSchedule(TENANT_ID, TARGET_ID, new MemberScheduleRequest("10:00", "19:00"));

            verify(userMapper).updateWorkSchedule(TENANT_ID, TARGET_ID,
                    LocalTime.of(10, 0), LocalTime.of(19, 0));
        }

        @Test
        @DisplayName("SCH-U-01: workStart >= workEnd 수정은 400 WORK_TIME_INVALID_RANGE")
        void invalidRangeRejected() {
            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.MEMBER, UserStatus.ACTIVE));

            assertThatThrownBy(() -> service().updateSchedule(TENANT_ID, TARGET_ID,
                    new MemberScheduleRequest("19:00", "10:00")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("WORK_TIME_INVALID_RANGE"));
            verify(userMapper, never()).updateWorkSchedule(anyLong(), anyLong(), any(), any());
        }

        @Test
        @DisplayName("SCH-U-03: 타 테넌트/미존재/SYSTEM_ADMIN 대상 스케줄 수정은 404(존재 비노출)")
        void crossTenantOrSystemAdminHidden() {
            when(userMapper.findById(TENANT_ID, 999L)).thenReturn(null);
            assertThatThrownBy(() -> service().updateSchedule(TENANT_ID, 999L,
                    new MemberScheduleRequest("09:00", "18:00")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));

            when(userMapper.findById(TENANT_ID, TARGET_ID))
                    .thenReturn(user(TARGET_ID, Role.SYSTEM_ADMIN, UserStatus.ACTIVE));
            assertThatThrownBy(() -> service().updateSchedule(TENANT_ID, TARGET_ID,
                    new MemberScheduleRequest("09:00", "18:00")))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("MEMBER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("멤버 목록")
    class ListMembers {

        @Test
        @DisplayName("PENDING 행은 유효 INVITE 토큰의 만료시각을 동봉, 그 외는 null")
        void listCarriesInviteExpiry() {
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            when(userMapper.findByTenant(TENANT_ID)).thenReturn(java.util.List.of(
                    user(2L, Role.MEMBER, UserStatus.PENDING),
                    user(3L, Role.MEMBER, UserStatus.ACTIVE)));
            when(userTokenService.findActiveInviteExpiries(TENANT_ID)).thenReturn(Map.of(2L, expiresAt));

            var members = service().list(TENANT_ID);

            assertThat(members).hasSize(2);
            assertThat(members.get(0).inviteExpiresAt()).isEqualTo(expiresAt);
            assertThat(members.get(0).workStart()).isEqualTo("09:00");
            assertThat(members.get(1).inviteExpiresAt()).isNull();
        }
    }

}
