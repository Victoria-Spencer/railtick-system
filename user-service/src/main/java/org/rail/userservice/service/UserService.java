package org.rail.userservice.service;

import org.rail.api.dto.UserIdCardDTO;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.dto.UserRegisterDTO;
import org.rail.userservice.pojo.dto.UserUpdateInfoDTO;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserVO;

public interface UserService {

    /**
     * 登录
     * @param userLoginDTO 登录信息
     * @return 登录成功的用户信息，包括访问令牌
     */
    UserVO login(UserLoginDTO userLoginDTO);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 注册
     * @param userRegisterDTO 注册信息
     * @return 注册成功的用户信息，包括访问令牌
     */
    UserVO register(UserRegisterDTO userRegisterDTO);

    /**
     * 更新用户信息
     * @param userUpdateInfoDTO 更新信息
     * @return 更新后的用户信息，包括访问令牌
     */
    UserVO update(UserUpdateInfoDTO userUpdateInfoDTO);

    /**
     * 更加用户id查询用户信息
     * @param id 用户id
     * @return 用户信息
     */
    User getById(Long id);

    /**
     * 查询证件类型和证件号
     * @param id 用户id
     * @return 证件类型和证件号
     */
    UserIdCardDTO getIdCardInfoById(Long id);
}
