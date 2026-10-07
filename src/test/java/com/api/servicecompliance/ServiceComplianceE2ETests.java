package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ServiceComplianceE2ETests {
    @Autowired TestRestTemplate client;
    @Autowired UserRepository userRepository;
    @Autowired ObligationRepository obligationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach void cleanDatabase(){obligationRepository.deleteAll(); userRepository.deleteAll();}

    @Test void shouldCompleteOperatorWorkflowThroughHttp() {
        User supervisor = userRepository.save(new User("Supervisor E2E", "supervisor-e2e@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR));
        User operator = userRepository.save(new User("Operator E2E", "operator-e2e@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR));

        String supervisorToken = login("supervisor-e2e@test.com");
        String operatorToken = login("operator-e2e@test.com");

        HttpHeaders supervisorHeaders = bearer(supervisorToken);
        String obligationBody = "{\"title\":\"Clean warehouse\",\"description\":\"Clean floor\",\"siteName\":\"Warehouse\",\"assignedOperatorId\":" + operator.getId() + ",\"dueAt\":\"2030-01-01T10:00:00Z\"}";
        ResponseEntity<Map> obligationResponse = client.exchange("/api/v1/obligations", HttpMethod.POST, new HttpEntity<>(obligationBody, jsonHeaders(supervisorToken)), Map.class);
        assertThat(obligationResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long obligationId = ((Number) obligationResponse.getBody().get("id")).longValue();

        ResponseEntity<String> assignedResponse = client.exchange("/api/v1/obligations", HttpMethod.GET, new HttpEntity<>(bearer(operatorToken)), String.class);
        assertThat(assignedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignedResponse.getBody()).contains("Clean warehouse");

        String executionBody = "{\"obligationId\":" + obligationId + ",\"result\":\"COMPLETED\",\"notes\":\"Completed in E2E\"}";
        ResponseEntity<Map> executionResponse = client.exchange("/api/v1/executions", HttpMethod.POST, new HttpEntity<>(executionBody, jsonHeaders(operatorToken)), Map.class);
        assertThat(executionResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long executionId = ((Number) executionResponse.getBody().get("id")).longValue();

        String evidenceBody = "{\"type\":\"PHOTO\",\"url\":\"https://example.com/evidence/photo-1.jpg\",\"description\":\"Warehouse cleaned\"}";
        ResponseEntity<Map> evidenceResponse = client.exchange("/api/v1/executions/" + executionId + "/evidence", HttpMethod.POST, new HttpEntity<>(evidenceBody, jsonHeaders(operatorToken)), Map.class);
        assertThat(evidenceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evidenceResponse.getBody().get("executionId")).isEqualTo(executionId.intValue());
    }

    private String login(String email) {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}";
        ResponseEntity<Map> response = client.postForEntity("/api/v1/auth/login", new HttpEntity<>(body, jsonHeaders(null)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("token");
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(token != null) headers.setBearerAuth(token); return headers;
    }
    private HttpHeaders bearer(String token) { HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(token); return headers; }
}
