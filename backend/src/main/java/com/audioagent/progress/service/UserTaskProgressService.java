package com.audioagent.progress.service;

import com.audioagent.common.api.PageResult;
import com.audioagent.progress.vo.UserTaskProgressVO;

public interface UserTaskProgressService {

    PageResult<UserTaskProgressVO> list(Long userId, int current, int size);

    UserTaskProgressVO get(Long userId, Long taskId);
}
