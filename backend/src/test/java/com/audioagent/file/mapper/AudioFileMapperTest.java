package com.audioagent.file.mapper;

import com.audioagent.common.enums.FileStatus;
import com.audioagent.file.entity.AudioFile;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AudioFileMapperTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE audio_file (
                        id BIGINT PRIMARY KEY,
                        user_id BIGINT NOT NULL,
                        original_name VARCHAR(255) NOT NULL,
                        file_status TINYINT NOT NULL,
                        deleted TINYINT NOT NULL
                    )
                    """);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO audio_file
                         (id, user_id, original_name, file_status, deleted)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setLong(1, 20L);
            statement.setLong(2, 7L);
            statement.setString(3, "meeting.wav");
            statement.setInt(4, FileStatus.AVAILABLE.getCode());
            statement.setInt(5, 0);
            statement.executeUpdate();
        }

        Environment environment = new Environment(
                "test", new JdbcTransactionFactory(), dataSource);
        MybatisConfiguration configuration =
                new MybatisConfiguration(environment);
        configuration.addMapper(AudioFileMapper.class);
        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder()
                .build(configuration);
    }

    @Test
    void selectsAvailableFileStoredWithNumericEnumValue() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            AudioFile file = session.getMapper(AudioFileMapper.class)
                    .selectOwnedAvailableForUpdate(
                            7L, 20L, FileStatus.AVAILABLE.getCode());

            assertNotNull(file);
            assertEquals(20L, file.getId());
            assertEquals(FileStatus.AVAILABLE, file.getFileStatus());
        }
    }
}
