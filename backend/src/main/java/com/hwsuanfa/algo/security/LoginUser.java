package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security 的登录主体。
 *
 * ⚠️ 权限模型的关键点：
 *   - 站主是全局角色，用 ROLE_SITE_OWNER 表达；
 *   - 队长**不是**全局角色，不进 authorities，而是按队伍关系实时判断
 *     （见 TeamPermission 工具类）。这样更换队长后旧队长权限立即失效，
 *     不需要重新登录即可生效。
 */
@Getter
public class LoginUser implements UserDetails {

    private final User user;

    public LoginUser(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getNickname() {
        return user.getNickname();
    }

    /** 是否站主 —— 全站唯一管理账号 */
    public boolean isSiteOwner() {
        return "SITE_OWNER".equals(user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 只放站主角色；队员/队长的区分走业务层
        return isSiteOwner()
                ? List.of(new SimpleGrantedAuthority("ROLE_SITE_OWNER"))
                : List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(user.getStatus());
    }
}
