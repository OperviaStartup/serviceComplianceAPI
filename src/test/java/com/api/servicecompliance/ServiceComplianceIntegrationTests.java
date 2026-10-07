package com.api.servicecompliance;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.authentication.domain.repository.UserRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceComplianceIntegrationTests {
    @Autowired MockMvc mockMvc;
    ObjectMapper objectMapper = new ObjectMapper();
    @Autowired UserRepository userRepository;
    @Autowired ObligationRepository obligationRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach void cleanDatabase(){obligationRepository.deleteAll(); userRepository.deleteAll();}

    @Test void shouldRegisterLoginAndReadAuthenticatedProfile() throws Exception {
        String register = "{\"fullName\":\"Operario Demo\",\"email\":\"operator@test.com\",\"password\":\"Password123!\"}";
        String response = mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("OPERATOR")).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(response).get("token").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization","Bearer "+token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("operator@test.com"));
    }

    @Test void shouldRegisterExecutionOnlyForAssignedOperator() throws Exception {
        User assigned = userRepository.save(new User("Assigned", "assigned@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR));
        User another = userRepository.save(new User("Another", "another@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR));
        Obligation obligation = obligationRepository.save(new Obligation("Clean lobby", "Clean the main lobby", "Main site", assigned.getId(), Instant.now().plusSeconds(3600)));
        String token = login("another@test.com");
        String body = "{\"obligationId\":"+obligation.getId()+",\"result\":\"COMPLETED\",\"notes\":\"Done\"}";
        mockMvc.perform(post("/api/v1/executions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DOMAIN_ERROR"));
    }

    @Test void shouldCreateExecutionForAssignedOperator() throws Exception {
        User assigned = userRepository.save(new User("Assigned", "assigned2@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR));
        Obligation obligation = obligationRepository.save(new Obligation("Clean office", "Clean office area", "Office site", assigned.getId(), Instant.now().plusSeconds(3600)));
        String token = login("assigned2@test.com");
        String body = "{\"obligationId\":"+obligation.getId()+",\"result\":\"COMPLETED\",\"notes\":\"Completed\"}";
        mockMvc.perform(post("/api/v1/executions").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.obligationId").value(obligation.getId()));
    }

    @Test void shouldAllowSupervisorToCreateObligationAndRejectOperator() throws Exception {
        User supervisor = userRepository.save(new User("Supervisor", "supervisor@test.com", passwordEncoder.encode("Password123!"), Role.SUPERVISOR));
        User operator = userRepository.save(new User("Operator", "operator2@test.com", passwordEncoder.encode("Password123!"), Role.OPERATOR));
        String request = "{\"title\":\"Clean warehouse\",\"description\":\"Clean warehouse floor\",\"siteName\":\"Warehouse\",\"assignedOperatorId\":"+operator.getId()+",\"dueAt\":\"2030-01-01T10:00:00Z\"}";
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+login("operator2@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/obligations").header("Authorization","Bearer "+login("supervisor@test.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.assignedOperatorId").value(operator.getId()));
        mockMvc.perform(get("/api/v1/obligations").header("Authorization","Bearer "+login("operator2@test.com")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Clean warehouse"));
    }

    private String login(String email) throws Exception {
        String body = "{\"email\":\""+email+"\",\"password\":\"Password123!\"}";
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }
}
