package live.switchly.api.repository;

import live.switchly.api.entity.UserEntity;
import live.switchly.api.model.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@Profile("!in-memory")
@Transactional
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = jpaUserRepository.findById(user.getId())
                .orElseGet(() -> new UserEntity(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail().trim().toLowerCase(),
                        user.getPasswordHash(),
                        user.isEnabled(),
                        user.isEmailVerified()
                ));

        entity.setFullName(user.getFullName());
        entity.setEmail(user.getEmail().trim().toLowerCase());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setEnabled(user.isEnabled());
        entity.setEmailVerified(user.isEmailVerified());

        UserEntity saved = jpaUserRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return jpaUserRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        return jpaUserRepository.findByEmailIgnoreCase(email.trim()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        if (email == null) return false;
        return jpaUserRepository.existsByEmailIgnoreCase(email.trim());
    }

    private User toDomain(UserEntity entity) {
        return new User(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.isEnabled(),
                entity.isEmailVerified(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

