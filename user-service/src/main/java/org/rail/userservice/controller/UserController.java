package org.rail.userservice.controller;

import org.rail.api.dto.UserIdCardDTO;
import org.rail.userservice.model.dto.UserLoginDTO;
import org.rail.userservice.model.dto.UserRegisterDTO;
import org.rail.userservice.model.dto.UserUpdateInfoDTO;
import org.rail.userservice.model.vo.UserInfoVO;
import org.rail.userservice.model.vo.UserUpdateVO;
import org.rail.userservice.model.vo.UserVO;
import org.rail.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @PostMapping("/register")
    public UserVO register(@RequestBody  @Validated UserRegisterDTO userRegisterDTO) {
        return userService.register(userRegisterDTO);
    }

    @PutMapping("/update")
    public UserUpdateVO update(@RequestBody  @Validated UserUpdateInfoDTO userUpdateInfoDTO) {
        return userService.update(userUpdateInfoDTO);
    }

    @GetMapping("/query")
    public UserInfoVO query() {
        return userService.query();
    }

    @PostMapping("/delete") // TODO 账号注销
    public void delete() {
    }

    @GetMapping("/user/id-card-info")
    public UserIdCardDTO getIdCardInfo() {
        return userService.getIdCardInfoById();
    }
}
