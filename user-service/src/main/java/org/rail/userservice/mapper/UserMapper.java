package org.rail.userservice.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.rail.userservice.pojo.entity.User;

@Mapper
public interface UserMapper {

    /**
     * 根据id查询用户信息
     * @param usernameOrMailOrPhone 用户名、邮箱或手机号
     * @return 用户信息
     */
    User findByUsernameOrMailOrPhone(String usernameOrMailOrPhone);

    /**
     * 新增用户
     * @param user 用户信息
     */
    @Insert("insert into" +
            " user(username, real_name, password, " +
            "id_type, id_card, phone, email, create_time, update_time)" +
            " values(#{username}, #{realName}, #{password}, #{idType}, " +
            "#{idCard}, #{phone}, #{email}, #{createTime}, #{updateTime})")
    void insert(User user);

    /**
     * 更新用户信息
     * @param user 用户信息
     */
    void update(User user);

    @Select("select * from `user` where id = #{userId}")
    User getById(long userId);
}
