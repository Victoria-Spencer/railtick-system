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
     * @param userLoginDTO
     * @return
     */
    UserVO login(UserLoginDTO userLoginDTO);

    /**
     * 退出登录
     */
    void logout();

    /**
     * 注册
     * @param userRegisterDTO
     * @return
     */
    UserVO register(UserRegisterDTO userRegisterDTO);

    /**
     * 更新用户信息
     * @param userUpdateInfoDTO
     * @return
     */
    UserVO update(UserUpdateInfoDTO userUpdateInfoDTO);

    /**
     * 更加用户id查询用户信息
     * @param id
     * @return
     */
    User getById(Long id);

    /**
     * 查询证件类型和证件号
     * @param id
     * @return
     */
    UserIdCardDTO getIdCardInfoById(Long id);
}
