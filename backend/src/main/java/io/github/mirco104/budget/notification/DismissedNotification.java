package io.github.mirco104.budget.notification;

import io.github.mirco104.budget.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A notification of the bell the user marked "Erledigt" (27h); its key names what it was about. */
@Entity
@Table(name = "dismissed_notifications")
public class DismissedNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "notification_key", nullable = false, length = 200)
    private String key;

    protected DismissedNotification() {
    }

    public DismissedNotification(User owner, String key) {
        this.owner = owner;
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
