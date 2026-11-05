package com.api.servicecompliance.authentication.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {
    @Query("select s.userId from RefreshSession s where s.tokenHash = :hash")
    Optional<Long> findUserByHash(@Param("hash") String hash);
    @Query("select s.familyId from RefreshSession s where s.tokenHash = :hash")
    Optional<String> findFamilyByHash(@Param("hash") String hash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :hash")
    Optional<RefreshSession> lockByHash(@Param("hash") String hash);
    @Modifying
    @Query("update RefreshSession s set s.revoked = true where s.familyId = :family")
    void revokeFamily(@Param("family") String family);
    @Modifying
    @Query("update RefreshSession s set s.revoked = true where s.userId = :userId")
    void revokeUser(@Param("userId") Long userId);
}
