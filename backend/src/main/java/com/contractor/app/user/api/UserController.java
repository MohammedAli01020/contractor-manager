package com.contractor.app.user.api;

import com.contractor.app.common.security.AuthenticatedUser;
import com.contractor.app.common.security.CurrentUser;
import com.contractor.app.user.api.dto.CreateUserRequest;
import com.contractor.app.user.api.dto.ResetPasswordRequest;
import com.contractor.app.user.api.dto.UpdateUserRequest;
import com.contractor.app.user.api.dto.UserResponse;
import com.contractor.app.user.domain.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "مستخدمو المقاول الحالي")
    public List<UserResponse> list() {
        return userService.list(CurrentUser.tenantId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "إضافة مستخدم للمقاول الحالي")
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(CurrentUser.tenantId(), request);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(CurrentUser.tenantId(), id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "تعديل الاسم والهاتف وتفعيل/تعطيل المستخدم")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        AuthenticatedUser me = CurrentUser.get();
        return userService.update(me.getTenantId(), id, request, me.getUserId());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "إعادة تعيين كلمة مرور مستخدم (تُنهي جلساته)")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(CurrentUser.tenantId(), id, request);
    }
}
