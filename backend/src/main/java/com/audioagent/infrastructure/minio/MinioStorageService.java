package com.audioagent.infrastructure.minio;

import java.io.InputStream;

public interface MinioStorageService {

    /**
     * 上传文件。
     *
     * @param objectKey  MinIO 对象路径
     * @param inputStream 文件输入流
     * @param size 文件大小
     * @param contentType 文件类型
     */
    void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    );

    /**
     * 删除文件。
     */
    void delete(String objectKey);

    /**
     * 生成临时访问地址。
     */
    String generatePresignedUrl(String objectKey);

    /**
     * 使用指定 bucket 和有效期生成 GET 预签名地址。
     */
    String generatePresignedUrl(
            String bucketName,
            String objectKey,
            int expirySeconds
    );

    /**
     * 判断对象是否存在。
     */
    boolean exists(String objectKey);

    /**
     * 判断指定 bucket 中的对象是否存在。
     */
    boolean exists(String bucketName, String objectKey);

    /**
     * 获取文件输入流。
     * 调用方负责在使用完毕后关闭流。
     *
     * @param objectKey MinIO 对象路径
     * @return 文件输入流
     */
    InputStream getObject(String objectKey);

    /**
     * 从指定 bucket 获取文件流，调用方负责关闭。
     */
    InputStream getObject(String bucketName, String objectKey);
}
