package iolchallenge.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FaviconController {

    @Hidden
    @GetMapping("favicon.ico")
    @ResponseBody
    void returnNoFavicon() {
        //This exists as, since SpringBoot 3.x, no default favicon is returned as it was considered an information
        //leak https://github.com/spring-projects/spring-boot/issues/17925 and there is no way to disable it.
    }
}