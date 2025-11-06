package org.rail.userservice.service.impl;

import org.rail.commonservice.exception.BusinessException;
import org.rail.userservice.mapper.UserMapper;
import org.rail.userservice.pojo.dto.UserLoginDTO;
import org.rail.userservice.pojo.entity.User;
import org.rail.userservice.pojo.vo.UserLoginVO;
import org.rail.userservice.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 登录
     * @param userLoginDTO
     * @return
     */
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        // 根据用户名查询用户信息
        User user = userMapper.findByUsernameOrMailOrPhone(userLoginDTO.getUsernameOrMailOrPhone());

        // 判断用户是否存在
        if(user == null) {
            throw new BusinessException("用户不存在");
        }

        // 校验密码
        if(!user.getPassword().equals(userLoginDTO.getPassword())) {
            throw new BusinessException("密码错误");
        }

        // 封装返回用户信息
        UserLoginVO userLoginVO = new UserLoginVO();
        BeanUtils.copyProperties(user, userLoginVO);

        // TODO 令牌校验
        userLoginVO.setAccessToken("1");

        return userLoginVO;
    }
}
