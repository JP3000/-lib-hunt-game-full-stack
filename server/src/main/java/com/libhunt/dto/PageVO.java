package com.libhunt.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

@Schema(description = "分页结果")
public record PageVO<T>(
        long total,
        long page,
        long size,
        List<T> records
) {

    public static <E, T> PageVO<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageVO<>(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(mapper).toList());
    }
}
