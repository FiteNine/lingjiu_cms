package com.lingjiuw.cms.module.cms.publish.template;

/**
 * 参数类型（§3.3）。参数值**永远是字面量**，不做字段插值（§1.3）。
 */
public enum ParamType {

    STRING,
    INT,
    /** 只接受 {@code 0} {@code 1} {@code true} {@code false}（忽略大小写），其余 → E1002（§3.3）。 */
    BOOL,
    /** 取值必须落在 {@link ParamSpec#enumValues()} 里，越界 → E1002。 */
    ENUM
}
