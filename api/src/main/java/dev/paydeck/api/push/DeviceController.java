package dev.paydeck.api.push;

import dev.paydeck.api.auth.CurrentUser;
import dev.paydeck.api.auth.User;
import dev.paydeck.api.auth.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Phones register here to get push notifications, and people choose which ones they want. */
@RestController
public class DeviceController {

    public record RegisterDeviceRequest(
            @NotBlank @Pattern(regexp = "ANDROID", message = "must be ANDROID") String platform,
            @NotBlank @Size(max = 4096) String token) {
    }

    public record UnregisterDeviceRequest(@NotBlank String token) {
    }

    public record NotificationSettings(boolean payments, boolean declines) {
    }

    private final DeviceRepository devices;
    private final UserRepository users;

    public DeviceController(DeviceRepository devices, UserRepository users) {
        this.devices = devices;
        this.users = users;
    }

    /** Safe to call on every launch: registering the same token again changes nothing. */
    @PostMapping("/api/devices")
    @Transactional
    public ResponseEntity<Void> register(CurrentUser user, @Valid @RequestBody RegisterDeviceRequest request) {
        devices.findByToken(request.token()).ifPresentOrElse(
                device -> device.moveTo(user.id()),
                () -> devices.save(new Device(user.id(), request.platform(), request.token())));
        return ResponseEntity.noContent().build();
    }

    /** Called when signing out, so the next person on this phone does not get your notifications. */
    @PostMapping("/api/devices/unregister")
    @Transactional
    public ResponseEntity<Void> unregister(CurrentUser user, @Valid @RequestBody UnregisterDeviceRequest request) {
        devices.findByToken(request.token())
                .filter(device -> device.getUserId().equals(user.id()))
                .ifPresent(devices::delete);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/notifications")
    @Transactional(readOnly = true)
    public NotificationSettings settings(CurrentUser user) {
        User stored = users.findById(user.id()).orElseThrow();
        return new NotificationSettings(stored.isNotifyPayments(), stored.isNotifyDeclines());
    }

    @PutMapping("/api/me/notifications")
    @Transactional
    public NotificationSettings changeSettings(CurrentUser user, @RequestBody NotificationSettings settings) {
        users.findById(user.id()).orElseThrow().setNotifications(settings.payments(), settings.declines());
        return settings;
    }
}
