package io.github.medtahabri.expenseflow.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Approves this user's claims; when null, the finance team does. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    private User manager;

    protected User() {
    }

    public User(String email, String fullName, String passwordHash, Role role, User manager) {
        this.email = email.toLowerCase();
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.manager = manager;
    }

    public boolean isFinance() {
        return getRole() == Role.FINANCE;
    }

    /**
     * True when {@code other} reports to this user. Goes through the getters:
     * {@code other} is often a lazy Hibernate proxy, whose fields are empty.
     */
    public boolean manages(User other) {
        User otherManager = other.getManager();
        return otherManager != null && otherManager.getId().equals(getId());
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public User getManager() {
        return manager;
    }
}
