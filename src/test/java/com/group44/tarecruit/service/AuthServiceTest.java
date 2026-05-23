package com.group44.tarecruit.service;

import com.group44.tarecruit.data.UserRepository;
import com.group44.tarecruit.model.Role;
import com.group44.tarecruit.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void registersApplicantAndChangesPassword() {
        UserRepository repository = new UserRepository(tempDir.resolve("users.csv"));
        repository.saveAll(java.util.List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy", "amy@school.edu", "oldpass")));
        AuthService service = new AuthService(repository);

        UserAccount registered = service.registerApplicant("New Student", "new@school.edu", "secret12", "secret12");
        assertEquals(Role.APPLICANT, registered.role());
        assertEquals("new@school.edu", repository.findByEmail("new@school.edu").orElseThrow().email());

        UserAccount updated = service.changePassword("ta-1", "oldpass", "newpass1", "newpass1");
        assertEquals("newpass1", updated.password());
        assertEquals("newpass1", repository.findById("ta-1").orElseThrow().password());
    }

    @Test
    void rejectsMismatchedRegistrationPassword() {
        AuthService service = new AuthService(new UserRepository(tempDir.resolve("users.csv")));
        assertThrows(IllegalArgumentException.class, () ->
                service.registerApplicant("New Student", "new@school.edu", "secret12", "secret13"));
    }

    @Test
    void rejectsPasswordsLongerThanTwentyCharacters() {
        UserRepository repository = new UserRepository(tempDir.resolve("users.csv"));
        repository.saveAll(java.util.List.of(new UserAccount("ta-1", Role.APPLICANT, "Amy", "amy@school.edu", "oldpass")));
        AuthService service = new AuthService(repository);
        String tooLongPassword = "abcdefghijklmnopqrstuvwxyz";

        assertThrows(IllegalArgumentException.class, () ->
                service.registerApplicant("New Student", "new@school.edu", tooLongPassword, tooLongPassword));
        assertThrows(IllegalArgumentException.class, () ->
                service.changePassword("ta-1", "oldpass", tooLongPassword, tooLongPassword));
    }
}
