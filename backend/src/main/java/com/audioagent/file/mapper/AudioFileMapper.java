package com.audioagent.file.mapper;

import com.audioagent.file.entity.AudioFile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AudioFileMapper extends BaseMapper<AudioFile> {

    @Select("""
            SELECT * FROM audio_file
            WHERE id = #{fileId}
              AND user_id = #{userId}
              AND file_status = #{availableStatus}
              AND deleted = 0
            FOR UPDATE
            """)
    AudioFile selectOwnedAvailableForUpdate(
            @Param("userId") Long userId,
            @Param("fileId") Long fileId,
            @Param("availableStatus") int availableStatus);
}
