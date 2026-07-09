package com.attendance.pro.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.attendance.pro.common.ApiException;
import com.attendance.pro.user.MemberDtos.MemberCreateRequest;
import com.attendance.pro.user.MemberDtos.MemberCreateResponse;

/**
 * 멤버 관리 서비스 테스트 — 마지막 TENANT_ADMIN 보호(ADM-01~05)·등록·검증 규칙.
 */
@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    private static final long TENANT_ID = 10L;
    private static final long TARGET_ID = 5L;

    @Mock
    private UserMapper userMapper;

    private MemberService service() {
        return new MemberService(userMapper);
    }

    private static User user(long userId, Role role, UserStatus status) {
        return new User(userId, TENANT_ID, "user" + userId + "@acme.co.kr", "hash",
                "유저" + userId, null, role, status, false, LocalDateTime.now(), LocalDateTime.now());
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

            var response = service().updateStatus(TENANT_ID, TARGET_ID, UserStatus.ACTIVE);

            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
            verify(userMapper, never()).countActiveTenantAdmins(anyLong());
        }
    }

    @Nested
    @DisplayName("멤버 등록")
    class Create {

        @Test
        @DisplayName("등록은 항상 MEMBER/ACTIVE, 초기 비밀번호는 서버 생성·패턴 충족·BCrypt 저장")
        void createGeneratesInitialPassword() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(false);
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(7L);
                return 1;
            });

            MemberCreateResponse response = service().create(TENANT_ID,
                    new MemberCreateRequest("hong@acme.co.kr", "홍길동", "DEV01"));

            assertThat(response.userId()).isEqualTo(7L);
            assertThat(response.role()).isEqualTo(Role.MEMBER);
            assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(response.initialPassword())
                    .hasSize(12)
                    .matches(MemberDtos.PASSWORD_PATTERN);
            //저장은 평문이 아닌 BCrypt 해시
            assertThat(captured[0].getTenantId()).isEqualTo(TENANT_ID);
            assertThat(captured[0].getPasswordHash()).isNotEqualTo(response.initialPassword());
            assertThat(new BCryptPasswordEncoder()
                    .matches(response.initialPassword(), captured[0].getPasswordHash())).isTrue();
        }

        @Test
        @DisplayName("테넌트 내 이메일 중복은 409 EMAIL_DUPLICATED")
        void duplicateEmailRejected() {
            when(userMapper.existsByEmail(TENANT_ID, "hong@acme.co.kr")).thenReturn(true);

            assertThatThrownBy(() -> service().create(TENANT_ID,
                    new MemberCreateRequest("hong@acme.co.kr", "홍길동", null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> {
                        ApiException apiException = (ApiException) e;
                        assertThat(apiException.getStatus().value()).isEqualTo(409);
                        assertThat(apiException.getCode()).isEqualTo("EMAIL_DUPLICATED");
                        assertThat(apiException.getMessageKey()).isEqualTo("member.email.duplicated");
                    });
        }

        @Test
        @DisplayName("최초 TENANT_ADMIN 발급: role=TENANT_ADMIN/ACTIVE로 저장하고 초기 비밀번호를 돌려준다")
        void registerInitialAdmin() {
            final UserCreate[] captured = new UserCreate[1];
            when(userMapper.insert(any(UserCreate.class))).thenAnswer(inv -> {
                captured[0] = inv.getArgument(0);
                captured[0].setUserId(100L);
                return 1;
            });

            MemberService.InitialAdmin admin =
                    service().registerInitialAdmin(TENANT_ID, "admin@acme.co.kr", "김관리");

            assertThat(admin.userId()).isEqualTo(100L);
            assertThat(admin.initialPassword()).matches(MemberDtos.PASSWORD_PATTERN);
            assertThat(captured[0].getRole()).isEqualTo(Role.TENANT_ADMIN);
            assertThat(captured[0].getStatus()).isEqualTo(UserStatus.ACTIVE);
        }
    }

}
