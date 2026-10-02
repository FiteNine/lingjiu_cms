package com.lingjiuw.cms.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 路由回退：浏览器直接访问前端路由时返回 index.html，由前端路由接管。
 * 仅覆盖后台管理界面的路由，与 SecurityConfig 中放行的路径保持一致。
 */
@Controller
public class SpaForwardController {

    @GetMapping({"/login", "/dashboard", "/cms/**", "/system/**", "/ai/**", "/sites", "/sites/**"})
    public String forward() {
        return "forward:/index.html";
    }
}
