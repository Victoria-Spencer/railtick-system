package org.rail.userservice.controller;

import org.rail.commonservice.result.Result;
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
@RequestMapping("/api/user-service") // TODO nginx处理，/api
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/v1/login")
    public Result<UserVO> login(@Validated @RequestBody UserLoginDTO userLoginDTO) {
        UserVO userVO = userService.login(userLoginDTO);
        return Result.success(userVO);
    }

    @GetMapping("/logout")
    public Result logout() {
        userService.logout();
        return Result.success();
    }

    @PostMapping("/register")
    public Result<UserVO> register(@Validated @RequestBody UserRegisterDTO userRegisterDTO) {
        UserVO userVO = userService.register(userRegisterDTO);
        return Result.success(userVO);
    }

    @PutMapping("/update")
    public Result<UserVO> update(@Validated @RequestBody UserUpdateInfoDTO userUpdateInfoDTO) {
        UserVO userVO = userService.update(userUpdateInfoDTO);
        return Result.success(userVO);
    }

    @GetMapping("/query")
    public Result<User> query(@RequestParam Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping("/deletion") // TODO 账号注销
    public Result delete(@RequestParam String username) {
        return null;
    }
}
