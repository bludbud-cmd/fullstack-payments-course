package dev.paydeck.api.push;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByToken(String token);

    List<Device> findByUserId(UUID userId);

    @Transactional
    void deleteByToken(String token);
}
