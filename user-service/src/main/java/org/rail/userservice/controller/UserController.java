package org.rail.userservice.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.annotation.OperationLog;
import org.rail.userservice.model.dto.UserLoginDTO;
import org.rail.userservice.model.dto.UserRegisterDTO;
import org.rail.userservice.model.dto.UserUpdateInfoDTO;
import org.rail.userservice.model.entity.User;
import org.rail.userservice.model.vo.UserVO;
import org.rail.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user-service")
@Validated
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/v1/login")
    public UserVO login(@RequestBody  @Validated UserLoginDTO userLoginDTO) {
        return userService.login(userLoginDTO);
    }

    @GetMapping("/logout")
    public void logout() {
        userService.logout();
    }

    @OperationLog(value = "用户注册", saveParam = true)
    @PostMapping("/register")
    public UserVO register(@RequestBody  @Validated UserRegisterDTO userRegisterDTO) {
        return userService.register(userRegisterDTO);
    }

    @OperationLog(value = "修改用户信息", saveParam = true)
    @PutMapping("/update")
    public UserVO update(@RequestBody  @Validated UserUpdateInfoDTO userUpdateInfoDTO) {
        return userService.update(userUpdateInfoDTO);
    }

    @GetMapping("/query")
    public User query(@RequestParam @NotNull(message = "用户ID不能为空") Long id) {
        return userService.getById(id);
    }

    @PostMapping("/deletion") // TODO 账号注销
    public void delete(@RequestParam @NotBlank(message = "用户名不能为空") String username) {
    }

    @OperationLog(value = "修改用户信息", saveParam = true)
    @GetMapping("/user/{id}")
    public UserIdCardDTO getIdCardInfo(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        return userService.getIdCardInfoById(id);
    }
}
