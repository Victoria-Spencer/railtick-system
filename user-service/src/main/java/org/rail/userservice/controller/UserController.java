package org.rail.userservice.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.rail.api.dto.UserIdCardDTO;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.result.Result;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.dto.UserRegisterDTO;
import org.rail.userservice.pojo.dto.UserUpdateInfoDTO;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserVO;
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
    public Result<UserVO> login(@RequestBody  @Validated UserLoginDTO userLoginDTO) {
        UserVO userVO = userService.login(userLoginDTO);
        return Result.success(userVO);
    }

    @GetMapping("/logout")
    public Result<Void> logout() {
        userService.logout();
        return Result.success();
    }

    @OperationLog(value = "用户注册", saveParam = true)
    @PostMapping("/register")
    public Result<UserVO> register(@RequestBody  @Validated UserRegisterDTO userRegisterDTO) {
        UserVO userVO = userService.register(userRegisterDTO);
        return Result.success(userVO);
    }

    @OperationLog(value = "修改用户信息", saveParam = true)
    @PutMapping("/update")
    public Result<UserVO> update(@RequestBody  @Validated UserUpdateInfoDTO userUpdateInfoDTO) {
        UserVO userVO = userService.update(userUpdateInfoDTO);
        return Result.success(userVO);
    }

    @GetMapping("/query")
    public Result<User> query(@RequestParam @NotNull(message = "用户ID不能为空") Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping("/deletion") // TODO 账号注销
    public Result<Void> delete(@RequestParam @NotBlank(message = "用户名不能为空") String username) {
        return null;
    }

    @OperationLog(value = "修改用户信息", saveParam = true)
    @GetMapping("/user/{id}")
    public Result<UserIdCardDTO> getIdCardInfo(@PathVariable @NotNull(message = "用户ID不能为空") Long id) {
        UserIdCardDTO userIdCardDTO = userService.getIdCardInfoById(id);
        return Result.success(userIdCardDTO);
    }
}
