package com.example.hello.controller;

import com.example.hello.dto.ApiResponse;
import com.example.hello.entity.User;
import com.example.hello.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        User user = userService.register(username, password);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Registered successfully", data));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request) {
        String username = requestBody.get("username");
        String password = requestBody.get("password");
        User user = userService.login(username, password);

        // Store user info in session
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());

        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        log.info("User logged in via session: {}", username);
        return ResponseEntity.ok(ApiResponse.success("Login successful", data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok(ApiResponse.success("Logged out successfully", null));
    }

    @GetMapping("/currentUser")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCurrentUser(HttpServletRequest request,
                                                                            Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("userId") != null) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", session.getAttribute("userId"));
            userInfo.put("username", session.getAttribute("username"));
            return ResponseEntity.ok(ApiResponse.success(userInfo));
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof OAuth2User oauth2User) {
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("username", oauth2User.getAttribute("name"));
            userInfo.put("email", oauth2User.getAttribute("email"));
            userInfo.put("provider", "google");
            return ResponseEntity.ok(ApiResponse.success(userInfo));
        }

        return ResponseEntity.ok(ApiResponse.error(401, "Not logged in"));
    }
}
