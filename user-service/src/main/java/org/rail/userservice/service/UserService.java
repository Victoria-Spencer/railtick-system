package org.rail.userservice.service;

import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.vo.UserLoginVO;

public interface UserService {

    /**
     * 登录
     * @param userLoginDTO
     * @return
     */
    UserLoginVO login(UserLoginDTO userLoginDTO);
}
