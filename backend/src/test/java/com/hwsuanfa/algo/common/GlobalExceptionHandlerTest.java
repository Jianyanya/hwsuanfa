package com.hwsuanfa.algo.common;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProtocolController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/protocol/json").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void wrongPathAndMissingQueryParametersReturn400() throws Exception {
        mvc.perform(get("/protocol/numbers/text"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/protocol/query"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mvc.perform(delete("/protocol/query"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(405));
    }

    @Test
    void unsupportedMediaReturns415() throws Exception {
        mvc.perform(post("/protocol/json").contentType(MediaType.TEXT_PLAIN).content("text"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value(415));
    }

    @RestController
    static class ProtocolController {
        @PostMapping(value = "/protocol/json", consumes = "application/json")
        ApiResult<Map<String, Object>> json(@RequestBody Map<String, Object> body) {
            return ApiResult.ok(body);
        }

        @GetMapping("/protocol/numbers/{id}")
        ApiResult<Long> number(@PathVariable Long id) {
            return ApiResult.ok(id);
        }

        @GetMapping("/protocol/query")
        ApiResult<Long> query(@RequestParam Long id) {
            return ApiResult.ok(id);
        }
    }
}
