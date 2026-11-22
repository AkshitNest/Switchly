package live.switchly.api.service;

import live.switchly.api.exception.ConflictException;
import live.switchly.api.exception.NotFoundException;
import live.switchly.api.model.User;
import live.switchly.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void create_success() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPasswordHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = userService.create(" Alice ", " ALICE@EXAMPLE.COM ", "Password123");

        assertThat(user.getFullName()).isEqualTo("Alice");
        assertThat(user.getEmail()).isEqualTo("alice@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("encodedPasswordHash");
        assertThat(user.isEnabled()).isTrue();
        verify(userRepository).save(any(User.class));
    }

    @Test
    void create_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create("Alice", "alice@example.com", "Password123"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test
    void getById_existing_returnsUser() {
        UUID id = UUID.randomUUID();
        User user = new User(id, "Alice", "alice@example.com", "hash", true, true);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        assertThat(userService.getById(id)).isEqualTo(user);
    }

    @Test
    void getById_missing_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(id))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void updateProfile_updatesName() {
        UUID id = UUID.randomUUID();
        User existing = new User(id, "Old Name", "alice@example.com", "hash", true, true);
        when(userRepository.findById(id)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateProfile(id, "New Name");

        assertThat(updated.getFullName()).isEqualTo("New Name");
        assertThat(updated.getEmail()).isEqualTo("alice@example.com");
    }
}

