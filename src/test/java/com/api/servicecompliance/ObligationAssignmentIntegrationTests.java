package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.SiteScope;
import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.application.ObligationAssignmentService;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.model.ObligationAssignmentHistory;
import com.api.servicecompliance.obligations.domain.repository.ObligationAssignmentHistoryRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ObligationAssignmentIntegrationTests {
    private static final long COMPANY_ID = 1L;
    private static final long SITE_ID = 1L;
    private static final long OTHER_SITE_ID = 25_001L;

    @Autowired ObligationAssignmentService assignments;
    @Autowired ObligationRepository obligations;
    @Autowired ObligationAssignmentHistoryRepository history;
    @Autowired UserRepository users;
    @Autowired SiteScopeRepository sites;

    private User supervisor;
    private User operatorOne;
    private User operatorTwo;

    @BeforeEach
    void setUp() {
        if (!sites.existsById(OTHER_SITE_ID)) {
            sites.save(new SiteScope(OTHER_SITE_ID, COMPANY_ID, "Other test site"));
        }
        supervisor = saveUser("Supervisor", Role.SUPERVISOR, SITE_ID);
        operatorOne = saveUser("Operator one", Role.OPERATOR, SITE_ID);
        operatorTwo = saveUser("Operator two", Role.OPERATOR, SITE_ID);
    }

    @Test
    void reassignsAnUnstartedObligationAndKeepsImmutableHistory() {
        Obligation obligation = saveObligation(operatorOne.getId());

        var response = assignments.assign(obligation.getId(),
                new ObligationAssignmentService.AssignmentRequest(operatorTwo.getId(), "Coverage change"),
                supervisor);

        assertThat(response.operatorId()).isEqualTo(operatorTwo.getId());
        assertThat(response.assignmentVersion()).isEqualTo(1);
        List<ObligationAssignmentHistory> entries =
                history.findAllByObligationIdOrderByAssignedAtAscIdAsc(obligation.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getPreviousOperatorId()).isEqualTo(operatorOne.getId());
        assertThat(entries.get(0).getNewOperatorId()).isEqualTo(operatorTwo.getId());
        assertThat(entries.get(0).getActorId()).isEqualTo(supervisor.getId());
        assertThat(entries.get(0).getReason()).isEqualTo("Coverage change");
    }

    @Test
    void rejectsInactiveAndOutOfScopeOperators() {
        Obligation obligation = saveObligation(operatorOne.getId());
        operatorTwo.suspend();
        users.saveAndFlush(operatorTwo);
        User otherSiteOperator = saveUser("Other site operator", Role.OPERATOR, OTHER_SITE_ID);

        assertThatThrownBy(() -> assignments.assign(obligation.getId(),
                new ObligationAssignmentService.AssignmentRequest(operatorTwo.getId(), "Inactive"), supervisor))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> assignments.assign(obligation.getId(),
                new ObligationAssignmentService.AssignmentRequest(otherSiteOperator.getId(), "Wrong site"), supervisor))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(history.findAllByObligationIdOrderByAssignedAtAscIdAsc(obligation.getId())).isEmpty();
    }

    @Test
    void rejectsReassignmentAfterExecutionStarted() {
        Obligation obligation = saveObligation(operatorOne.getId());
        obligation.markInProgress();
        obligations.saveAndFlush(obligation);

        assertThatThrownBy(() -> assignments.assign(obligation.getId(),
                new ObligationAssignmentService.AssignmentRequest(operatorTwo.getId(), "Too late"), supervisor))
                .isInstanceOfSatisfying(ApiConflictException.class,
                        conflict -> assertThat(conflict.getCode()).isEqualTo("OBLIGATION_NOT_ASSIGNABLE"));
    }

    @Test
    void serializesConcurrentReassignmentsWithoutCreatingTwoCurrentAssignees() throws Exception {
        Obligation obligation = saveObligation(operatorOne.getId());
        User operatorThree = saveUser("Operator three", Role.OPERATOR, SITE_ID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> assignAfterBarrier(
                    obligation.getId(), operatorTwo.getId(), "Concurrent A", ready, start));
            Future<?> second = executor.submit(() -> assignAfterBarrier(
                    obligation.getId(), operatorThree.getId(), "Concurrent B", ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        Obligation stored = obligations.findById(obligation.getId()).orElseThrow();
        assertThat(stored.getAssignedOperatorId()).isIn(operatorTwo.getId(), operatorThree.getId());
        assertThat(stored.getAssignmentVersion()).isEqualTo(2);
        assertThat(history.findAllByObligationIdOrderByAssignedAtAscIdAsc(obligation.getId())).hasSize(2);
    }

    private void assignAfterBarrier(Long obligationId, Long operatorId, String reason,
                                    CountDownLatch ready, CountDownLatch start) {
        try {
            ready.countDown();
            start.await();
            assignments.assign(obligationId,
                    new ObligationAssignmentService.AssignmentRequest(operatorId, reason), supervisor);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Obligation saveObligation(Long operatorId) {
        return obligations.saveAndFlush(new Obligation("Clean lobby", "Use approved product", "Site 1",
                operatorId, Instant.now().plusSeconds(3_600), COMPANY_ID, SITE_ID));
    }

    private User saveUser(String name, Role role, Long siteId) {
        String unique = UUID.randomUUID().toString();
        return users.saveAndFlush(new User(name, unique + "@test.example", "hash", role, COMPANY_ID, siteId));
    }
}
