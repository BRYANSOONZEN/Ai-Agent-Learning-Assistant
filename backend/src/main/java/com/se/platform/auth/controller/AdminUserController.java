package com.se.platform.auth.controller;

import com.se.platform.auth.dto.AuthDtos.AdminUserView;
import com.se.platform.auth.dto.AuthDtos.CreateUserRequest;
import com.se.platform.auth.dto.AuthDtos.ResetPasswordRequest;
import com.se.platform.auth.dto.AuthDtos.StatusRequest;
import com.se.platform.auth.dto.AuthDtos.UserCounts;
import com.se.platform.auth.service.AdminUserService;
import com.se.platform.common.PageResult;
import com.se.platform.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 用户管理，只有管理员能调（见 AccessRules） */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    /** GET /api/admin/users?page=1&size=10&role=teacher&keyword=李 */
    @GetMapping
    public Result<PageResult<AdminUserView>> page(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String role,
                                                  @RequestParam(required = false) String keyword) {
        return Result.ok(service.page(page, size, role, keyword));
    }

    /** GET /api/admin/users/counts：各角色人数、禁用人数 */
    @GetMapping("/counts")
    public Result<UserCounts> counts() {
        return Result.ok(service.counts());
    }

    /** POST /api/admin/users：新建老师或学生 */
    @PostMapping
    public Result<AdminUserView> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(service.create(request));
    }

    /** PUT /api/admin/users/{id}/status：1 启用，0 禁用；禁用后这个人的令牌马上失效 */
    @PutMapping("/{id}/status")
    public Result<AdminUserView> status(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
        return Result.ok(service.setStatus(id, request.status()));
    }

    /** PUT /api/admin/users/{id}/password：重置密码，同时解除锁定 */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(id, request.password());
        return Result.ok();
    }
}
