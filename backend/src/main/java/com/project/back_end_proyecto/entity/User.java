package com.project.back_end_proyecto.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "USUARIO",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_USUARIO_CORREO",
                columnNames = "CORREO"
        )
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_USUARIO", nullable = false)
    private Long id;

    @Column(name = "NOMBRE", nullable = false, length = 100)
    private String name;

    @Column(name = "CORREO", nullable = false, length = 150)
    private String email;

    @Column(name = "CONTRASENA", nullable = false, length = 255)
    private String password;

    protected User() {
    }

    public User(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    @JsonIgnore
    public String getPassword() {
        return password;
    }
}
