package com.family.finance.controller;

import com.family.finance.dto.LoginRequest;
import com.family.finance.dto.RegisterRequest;
import com.family.finance.dto.TokenResponse;
import com.family.finance.entity.Family;
import com.family.finance.entity.FamilyMember;
import com.family.finance.entity.User;
import com.family.finance.repository.FamilyMemberRepository;
import com.family.finance.repository.FamilyRepository;
import com.family.finance.repository.UserRepository;
import com.family.finance.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        // Create user
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user = userRepository.save(user);

        // Create family and make user the admin
        Family family = new Family();
        family.setName(request.familyName());
        family = familyRepository.save(family);

        FamilyMember membership = new FamilyMember();
        membership.setFamily(family);
        membership.setUser(user);
        membership.setRole(FamilyMember.Role.ADMIN);
        familyMemberRepository.save(membership);

        List<UUID> familyIds = List.of(family.getId());
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TokenResponse.of(accessToken, refreshToken,
                        jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        User user = (User) auth.getPrincipal();
        List<UUID> familyIds = familyMemberRepository.findActiveByUserId(user.getId())
                .stream()
                .map(m -> m.getFamily().getId())
                .toList();

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), familyIds);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId(), familyIds);

        return ResponseEntity.ok(TokenResponse.of(accessToken, refreshToken,
                jwtTokenProvider.getAccessTokenExpiryMs(), user.getId(), familyIds));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@RequestHeader("X-Refresh-Token") String refreshToken) {
        Claims claims;
        try {
            claims = jwtTokenProvider.validateAndParseClaims(refreshToken);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
        }

        UUID userId = jwtTokenProvider.extractUserId(claims);
        List<UUID> familyIds = jwtTokenProvider.extractFamilyIds(claims);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId, familyIds);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId, familyIds);

        return ResponseEntity.ok(TokenResponse.of(newAccessToken, newRefreshToken,
                jwtTokenProvider.getAccessTokenExpiryMs(), userId, familyIds));
    }
}
