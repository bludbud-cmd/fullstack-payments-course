package dev.paydeck.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginCodeRepository extends JpaRepository<LoginCode, UUID> {

    Optional<LoginCode> findByCodeHash(String codeHash);
}
