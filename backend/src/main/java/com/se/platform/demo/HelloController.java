package com.se.platform.demo;

import com.se.platform.common.BusinessException;
import com.se.platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务自检接口，前缀 /api/hello：检查后端是否正常运行，以及统一返回格式和错误处理是否生效。
 */
@RestController
@RequestMapping("/api/hello")
public class HelloController {

    /**
     * GET /api/hello                → {"code":200,"message":"ok","data":"hello, 同学"}
     * GET /api/hello?name=张三       → {"code":200,"message":"ok","data":"hello, 张三"}
     *
     * <p>{@code @RequestParam}：从查询参数（? 后面的部分）取值；defaultValue 是不传时的默认值。
     */
    @GetMapping
    public Result<String> hello(@RequestParam(defaultValue = "同学") String name) {
        return Result.ok("hello, " + name);
    }

    /**
     * GET /api/hello/courses/1   → 成功
     * GET /api/hello/courses/99  → {"code":404,"message":"课程不存在","data":null}
     * GET /api/hello/courses/abc → {"code":400,...}（id 不是数字，由全局异常处理返回）
     *
     * <p>{@code @PathVariable}：从路径里取值，{id} 对应参数 id。
     * 演示抛出业务异常后，GlobalExceptionHandler 怎样把它变成统一格式。
     */
    @GetMapping("/courses/{id}")
    public Result<String> course(@PathVariable Long id) {
        if (id != 1L) {
            throw BusinessException.notFound("课程");
        }
        return Result.ok("软件工程导论");
    }
}
