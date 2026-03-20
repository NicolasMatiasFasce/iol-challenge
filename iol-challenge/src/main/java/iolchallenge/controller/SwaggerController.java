package iolchallenge.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

/**
 * This controller redirects / to /swagger-ui/index.html#/ so http://localhost:9290 shows swagger
 */
@RestController
public class SwaggerController {

    @Operation(hidden = true)
    @GetMapping("/")
    public RedirectView redirectToSwagger() {
        return new RedirectView("/swagger-ui/index.html#/");
    }
}
