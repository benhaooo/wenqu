package com.wenqu.auth.api;

import java.util.Collection;
import java.util.Map;

public interface UserApi {

    /** 按 id 批量查询用户，不存在的 id 不出现在结果中。 */
    Map<Long, UserSummary> findByIds(Collection<Long> ids);
}
