package com.audioagent.common.api;

import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long current;
    private final long size;
    private final long total;
    private final long pages;

    public PageResult(List<T> records, long current, long size, long total) {
        this.records = records != null ? records : Collections.emptyList();
        this.current = current;
        this.size = size;
        this.total = total;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long current, long size, long total) {
        return new PageResult<>(records, current, size, total);
    }

    public static <T> PageResult<T> empty(long current, long size) {
        return new PageResult<>(Collections.emptyList(), current, size, 0);
    }
}
