package io.github.mirco104.budget.notification;

import io.github.mirco104.budget.user.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DismissedNotificationRepository extends JpaRepository<DismissedNotification, Long> {

    List<DismissedNotification> findByOwner(User owner);

    Optional<DismissedNotification> findByOwnerAndKey(User owner, String key);

    void deleteByOwnerAndKey(User owner, String key);

    void deleteByOwner(User owner);
}
