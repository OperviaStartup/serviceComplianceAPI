package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.application.ObligationAssignmentService;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationAssignmentHistoryRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ObligationAssignmentServiceTests {
    @Mock ObligationRepository obligations;
    @Mock ObligationAssignmentHistoryRepository history;
    @Mock UserRepository users;

    @Test
    void rejectsAnOperatorFromAnotherCompany() {
        User supervisor = new User("Supervisor", "supervisor@test.example", "hash",
                Role.SUPERVISOR, 1L, 1L);
        User foreignOperator = new User("Foreign", "foreign@test.example", "hash",
                Role.OPERATOR, 2L, 1L);
        Obligation obligation = new Obligation("Clean", "Clean", "Site 1", null,
                Instant.parse("2030-01-01T10:00:00Z"), 1L, 1L);
        when(obligations.lockInScope(10L, 1L, 1L)).thenReturn(Optional.of(obligation));
        when(users.findById(20L)).thenReturn(Optional.of(foreignOperator));

        ObligationAssignmentService service = new ObligationAssignmentService(obligations, history, users,
                Clock.fixed(Instant.parse("2026-11-12T12:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.assign(10L,
                new ObligationAssignmentService.AssignmentRequest(20L, "Invalid tenant"), supervisor))
                .isInstanceOf(AccessDeniedException.class);
    }
}
