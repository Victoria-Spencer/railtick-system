package org.rail.userservice.controller;

import org.rail.commonservice.result.Result;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.vo.UserLoginVO;
import org.rail.userservice.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-service") // TODO nginx处理，/api
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping("/v1/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginDTO userLoginDTO) {
        UserLoginVO userLoginVO = userService.login(userLoginDTO);
        return Result.success(userLoginVO);
    }
}
