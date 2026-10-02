package com.lingjiuw.cms.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注在 Controller 方法上，自动记录操作日志到 sys_oper_log。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /** 所属模块，如：用户管理 */
    String module();

    /** 操作动作，如：新增用户 */
    String action();
}
