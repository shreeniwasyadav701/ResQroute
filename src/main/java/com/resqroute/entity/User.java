package com.resqroute.entity;

import com.resqroute.enums.Role;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_username", columnNames = "username"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String username;

    @Column(nullable = false, length = 120)
    private String email;

    @Column(nullable = false, length = 200)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public User() {
    }

    public User(String username, String email, String password, Role role) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
