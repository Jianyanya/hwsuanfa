package com.hwsuanfa.algo.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RegisterPropertiesTest {
    @Test
    void disabledRegistrationAllowsUnsetCodes() {
        RegisterProperties properties = new RegisterProperties();
        properties.setEnabled(false);
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void identicalConfiguredCodesAreRejected() {
        RegisterProperties properties = new RegisterProperties();
        properties.setAdminCode("test-only-invite");
        properties.setMemberCode("test-only-invite");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void memberRegistrationAllowsAdminCodeToBeRemoved() {
        RegisterProperties properties = new RegisterProperties();
        properties.setMemberCode("test-only-member-invite");
        assertDoesNotThrow(properties::validate);
    }
}
