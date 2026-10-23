package com.hwsuanfa.algo.security;

import com.hwsuanfa.algo.common.BizException;
import com.hwsuanfa.algo.common.ErrorCode;
import com.hwsuanfa.algo.entity.User;
import com.hwsuanfa.algo.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 按用户名加载登录主体。
 * 注意：这里查不到用户抛 UsernameNotFoundException，由 DaoAuthenticationProvider
 * 统一转成 BadCredentialsException，避免暴露「用户名是否存在」。
 */
@Service
@RequiredArgsConstructor
public class LoginUserService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new LoginUser(user);
    }

    /** 按 ID 加载（JWT 过滤器用，避免每次查两次库） */
    public LoginUser loadById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.of(ErrorCode.UNAUTHORIZED);
        }
        return new LoginUser(user);
    }
}
