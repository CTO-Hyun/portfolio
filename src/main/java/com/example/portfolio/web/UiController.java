package com.example.portfolio.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 간단한 테스트 UI를 제공하는 Thymeleaf 뷰 컨트롤러다.
 */
@Controller
@RequestMapping
public class UiController {

    @GetMapping("/")
    public String root() {
        return "redirect:/ui";
    }

    @GetMapping("/ui")
    public String dashboard() {
        return "ui/dashboard";
    }
}
