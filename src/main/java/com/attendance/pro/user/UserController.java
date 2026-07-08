package com.attendance.pro.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.attendance.pro.user.UserDtos.SignupRequest;
import com.attendance.pro.user.UserDtos.UserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 회원 API.
 */
@Tag(name = "User", description = "api.user.tag")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "api.user.signup.summary", description = "api.user.signup.description")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "api.user.signup.201"),
            @ApiResponse(responseCode = "400", description = "api.user.signup.400"),
            @ApiResponse(responseCode = "409", description = "api.user.signup.409")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest request) {
        return userService.signup(request);
    }

}
