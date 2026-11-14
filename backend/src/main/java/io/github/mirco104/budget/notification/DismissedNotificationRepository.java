package io.github.mirco104.budget.notification;

import io.github.mirco104.budget.user.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DismissedNotificationRepository extends JpaRepository<DismissedNotification, Long> {

    List<DismissedNotification> findByOwner(User owner);

    boolean existsByOwnerAndKey(User owner, String key);

    void deleteByOwner(User owner);
}
