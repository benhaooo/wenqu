package com.wenqu.infra.storage.api;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Collection;

/** 对象存储。对象不公开，只能通过有时效的签名链接访问。 */
public interface ObjectStorage {

    void put(String key, InputStream content, long size, String contentType);

    /** 浏览器可直接访问的签名下载链接。 */
    URI signedUrl(String key, Duration ttl);

    void delete(Collection<String> keys);
}
