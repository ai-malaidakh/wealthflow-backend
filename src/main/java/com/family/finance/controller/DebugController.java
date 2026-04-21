package com.family.finance.controller;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile("dev")
@RestController
@RequestMapping("/debug")
public class DebugController {

    @GetMapping("/sentry-test")
    public ResponseEntity<Map<String, Object>> sentryTest() {
        throw new RuntimeException("Sentry test exception");
    }
}
