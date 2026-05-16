package org.rail.userservice.model.vo;

import lombok.Data;

@Data
public class UserUpdateVO {

    private Long id;
    private String username;
    private String phone;
    private String email;
    private String userType;
    private String postCode;
    private String address;
}
