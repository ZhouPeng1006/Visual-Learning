package com.zp.visuallearningservice.controller;

import ast.AST;
import com.zp.visuallearningservice.models.CodeRequest;
import com.zp.visuallearningservice.models.Result;
import exception.FileException;
import exception.SyntaxException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import parser.Parser;
import utils.ErrorHandler;

/**
 * @author ZP
 * @date 2023/6/7 23:08
 * @description TODO
 */
@Controller
@ResponseBody
public class ASTVisualController {


    @PostMapping("/parse")
    public Result parseCode(@RequestBody CodeRequest codeRequest) throws FileException, SyntaxException {
        // 进行代码分析逻辑，并得到分析结果 result
        AST ast = Parser.parseOnlineCode(codeRequest.getCode(), new ErrorHandler("Visual-Learning"));
        //TODO: the ast needs to be wrapped into json format and returned to the front end
        Result result = new Result();
        // 设置分析结果
        result.setMessage("Analysis completed");
//        result.setData(/* 分析结果数据 */);

        return result;
    }
}
