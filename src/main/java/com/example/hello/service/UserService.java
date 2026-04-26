package com.example.hello.service;

import com.example.hello.entity.User;
import com.example.hello.exception.BusinessException;
import com.example.hello.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BusinessException(409, "Username already exists");
        }
        // BCrypt hash — never store plaintext passwords
        User newUser = new User(username, passwordEncoder.encode(password));
        log.info("User registered: {}", username);
        return userRepository.save(newUser);
    }

    public User login(String username, String password) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isEmpty()) {
            throw new BusinessException(401, "User not found");
        }
        User user = optionalUser.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(401, "Invalid password");
        }
        log.info("User logged in: {}", username);
        return user;
    }
}
