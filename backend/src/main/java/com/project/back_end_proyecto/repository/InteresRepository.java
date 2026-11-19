package com.project.back_end_proyecto.repository;

import com.project.back_end_proyecto.entity.Interes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface InteresRepository extends JpaRepository<Interes, Long> {
    Optional<Interes> findByNombreIgnoreCase(String nombre);
    List<Interes> findAllByOrderByNombreAsc();
}
