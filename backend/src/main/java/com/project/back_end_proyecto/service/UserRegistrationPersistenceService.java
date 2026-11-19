package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.repository.UserRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("oracle")
public class UserRegistrationPersistenceService {
    private final UserRepository userRepository;

    public UserRegistrationPersistenceService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void saveNewUser(User user) {
        userRepository.saveAndFlush(user);
    }
}
