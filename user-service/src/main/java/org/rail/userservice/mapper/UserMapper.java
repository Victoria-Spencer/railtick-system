package org.rail.userservice.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.rail.userservice.model.entity.User;

@Mapper
public interface UserMapper {

    /**
     * 根据用户名/邮箱/手机号查询用户
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

    /**
     * 根据用户ID查询用户信息，排除已删除的账号
     * @param userId 用户ID
     * @return 用户信息
     */
    @Select("select * from `user` where id = #{userId} AND is_deleted = 0")
    User getById(long userId);

    /**
     * 恢复账号：将已删除账号改为正常状态，同时更新修改时间
     * @param userId 用户ID
     */
    @Update("UPDATE `user` SET is_deleted = 0, update_time = NOW() WHERE id = #{userId}")
    void restoreUser(Long userId);
}
