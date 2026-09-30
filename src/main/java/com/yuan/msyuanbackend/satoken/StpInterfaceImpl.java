package com.yuan.msyuanbackend.satoken;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.yuan.msyuanbackend.model.entity.User;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.yuan.msyuanbackend.constant.UserConstant.USER_LOGIN_STATE;

@Component
public class StpInterfaceImpl implements StpInterface {


    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        User user = (User) StpUtil.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (user == null || user.getUserRole() == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(user.getUserRole());
    }
}
