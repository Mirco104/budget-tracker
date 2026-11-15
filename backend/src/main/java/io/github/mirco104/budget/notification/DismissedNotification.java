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

/**
 * A notification of the bell the user marked "Erledigt" (27h). The key names the subject (an account, an item, the
 * plan, a series), the value what the notification said then; one row per subject, a new "Erledigt" overwrites it.
 */
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

    @Column(name = "dismissed_value", nullable = false, length = 200)
    private String value;

    protected DismissedNotification() {
    }

    public DismissedNotification(User owner, String key, String value) {
        this.owner = owner;
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
