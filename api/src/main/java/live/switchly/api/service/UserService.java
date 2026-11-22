package live.switchly.api.service;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.User;
import live.switchly.api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User create(String fullName, String email, String rawPassword) {
        String normalizedEmail = normalizeEmail(email);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered: " + normalizedEmail);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        User user = new User(
                UUID.randomUUID(),
                fullName.trim(),
                normalizedEmail,
                passwordHash,
                true,
                false
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(normalizeEmail(email));
    }

    public User updateProfile(UUID userId, String fullName) {
        User user = getById(userId);
        User updated = new User(
                user.getId(),
                fullName.trim(),
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled(),
                user.isEmailVerified(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        return userRepository.save(updated);
    }

    public static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase();
    }
}

