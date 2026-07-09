package com.attendance.pro.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.auth.LoginUser;
import com.attendance.pro.auth.SessionUser;
import com.attendance.pro.user.MemberDtos.MemberCreateRequest;
import com.attendance.pro.user.MemberDtos.MemberCreateResponse;
import com.attendance.pro.user.MemberDtos.MemberResponse;
import com.attendance.pro.user.MemberDtos.MemberRoleRequest;
import com.attendance.pro.user.MemberDtos.MemberStatusRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 멤버 관리 API (TENANT_ADMIN 전용 — SYSTEM_ADMIN도 403, RoleInterceptor 화이트리스트).
 * tenantId는 항상 세션({@code @LoginUser SessionUser})에서 꺼낸다 — 요청에 테넌트 식별자 없음.
 */
@Tag(name = "Member", description = "api.member.tag")
@RestController
@RequestMapping("/api/v1/tenant/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "api.member.list.summary")
    @GetMapping
    public List<MemberResponse> list(@LoginUser SessionUser user) {
        return memberService.list(user.tenantId());
    }

    @Operation(summary = "api.member.create.summary", description = "api.member.create.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.member.create.201"),
            @ApiResponse(responseCode = "409", description = "api.member.create.409")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberCreateResponse create(@LoginUser SessionUser user,
            @Valid @RequestBody MemberCreateRequest request) {
        return memberService.create(user.tenantId(), request);
    }

    @Operation(summary = "api.member.status.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "api.member.status.409")
    })
    @PutMapping("/{userId}/status")
    public MemberResponse updateStatus(@LoginUser SessionUser user,
            @PathVariable("userId") long userId,
            @Valid @RequestBody MemberStatusRequest request) {
        return memberService.updateStatus(user.tenantId(), userId, request.status());
    }

    @Operation(summary = "api.member.role.summary")
    @ApiResponses({
            @ApiResponse(responseCode = "409", description = "api.member.role.409")
    })
    @PutMapping("/{userId}/role")
    public MemberResponse updateRole(@LoginUser SessionUser user,
            @PathVariable("userId") long userId,
            @Valid @RequestBody MemberRoleRequest request) {
        return memberService.updateRole(user.tenantId(), userId, request.role());
    }

}
