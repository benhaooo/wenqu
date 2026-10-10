package com.wenqu.auth.service;

import com.wenqu.auth.api.UserApi;
import com.wenqu.auth.api.UserSummary;
import com.wenqu.auth.domain.User;
import com.wenqu.auth.repository.UserRepository;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserQueryService implements UserApi {

    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getById(long id) {
        return userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
    }

    @Override
    public Map<Long, UserSummary> findByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIds(ids).stream()
                .map(user -> new UserSummary(user.id(), user.email(), user.displayName()))
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }
}
