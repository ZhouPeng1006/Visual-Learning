package com.zp.visuallearningservice.controller;

import com.zp.visuallearningservice.models.CodeRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * @author ZP
 * @date 2023/6/7 23:08
 * @description TODO
 */
@Controller
@ResponseBody
public class ASTVisualController {
    @PostMapping("/parse")
    public String parseCode(@RequestBody CodeRequest codeRequest) {
        System.out.println("/parse post ok");
        // 处理代码的逻辑
        // ...

        return "OK";
    }
}
