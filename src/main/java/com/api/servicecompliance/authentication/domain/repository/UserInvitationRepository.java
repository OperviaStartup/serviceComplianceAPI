package com.api.servicecompliance.authentication.domain.repository;

import com.api.servicecompliance.authentication.domain.model.InvitationStatus;
import com.api.servicecompliance.authentication.domain.model.UserInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserInvitationRepository extends JpaRepository<UserInvitation, String> {
    Optional<UserInvitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from UserInvitation invitation where invitation.tokenHash = :tokenHash")
    Optional<UserInvitation> lockByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from UserInvitation invitation where invitation.id = :id")
    Optional<UserInvitation> lockById(@Param("id") String id);

    long countByCompanyIdAndStatusAndExpiresAtAfter(Long companyId, InvitationStatus status, Instant now);

    boolean existsByCompanyIdAndEmailIgnoreCaseAndStatusAndExpiresAtAfter(
            Long companyId, String email, InvitationStatus status, Instant now);

    boolean existsByCompanyIdAndDocumentNumberAndStatusAndExpiresAtAfter(
            Long companyId, String documentNumber, InvitationStatus status, Instant now);
}
