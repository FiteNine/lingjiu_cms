package com.lingjiuw.cms.module.cms.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 把 {@code Long} 计数按数值输出，覆盖 {@code JacksonConfig} 的全局 Long→String 策略。
 *
 * <p>全局策略只为了避免前端丢 id 精度，但计数（内容数 / 浏览量 / 统计总数）不是 id，
 * 前端（admin-ui 的 types/index.ts）按 number 声明它们：比较与求和需要数值形态。
 * 给计数字段标 {@code @JsonSerialize(using = LongNumberSerializer.class)} 即可。
 */
public class LongNumberSerializer extends JsonSerializer<Long> {

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if (value == null) {
            provider.defaultSerializeNull(gen);
        } else {
            gen.writeNumber(value);
        }
    }
}
