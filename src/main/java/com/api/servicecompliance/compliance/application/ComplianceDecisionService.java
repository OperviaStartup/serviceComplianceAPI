package com.api.servicecompliance.compliance.application;

import com.api.servicecompliance.authentication.domain.model.User;
import com.api.servicecompliance.compliance.domain.model.ComplianceCase;
import com.api.servicecompliance.compliance.domain.model.ComplianceCriterion;
import com.api.servicecompliance.compliance.domain.model.ComplianceFinding;
import com.api.servicecompliance.compliance.domain.model.ComplianceOutcome;
import com.api.servicecompliance.compliance.domain.model.ComplianceResult;
import com.api.servicecompliance.compliance.domain.model.CriterionStatus;
import com.api.servicecompliance.compliance.domain.model.ExceptionDisposition;
import com.api.servicecompliance.compliance.domain.repository.ComplianceCaseRepository;
import com.api.servicecompliance.compliance.domain.repository.ComplianceResultRepository;
import com.api.servicecompliance.executions.domain.repository.ExecutionRepository;
import com.api.servicecompliance.obligations.domain.model.Obligation;
import com.api.servicecompliance.obligations.domain.repository.ObligationRepository;
import com.api.servicecompliance.shared.domain.ApiConflictException;
import com.api.servicecompliance.shared.domain.FieldValidationException;
import com.api.servicecompliance.shared.domain.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ComplianceDecisionService {
    private final ComplianceReviewService reviews;
    private final ExecutionRepository executions;
    private final ObligationRepository obligations;
    private final ComplianceCaseRepository cases;
    private final ComplianceResultRepository results;
    private final Clock clock;

    public ComplianceDecisionService(ComplianceReviewService reviews, ExecutionRepository executions,
                                     ObligationRepository obligations, ComplianceCaseRepository cases,
                                     ComplianceResultRepository results, Clock clock) {
        this.reviews = reviews;
        this.executions = executions;
        this.obligations = obligations;
        this.cases = cases;
        this.results = results;
        this.clock = clock;
    }

    @Transactional
    public ComplianceResultResponse decide(Long executionId, DecisionCommand command, User reviewer) {
        Long obligationId = executions.findObligationId(executionId)
                .orElseThrow(ComplianceDecisionService::notFound);
        Obligation obligation = obligations.lockInScope(
                        obligationId, reviewer.getCompanyId(), reviewer.getSiteId())
                .orElseThrow(ComplianceDecisionService::notFound);
        ComplianceReviewService.ComplianceReview review = reviews.review(executionId, reviewer);
        verifyReviewVersion(command.reviewVersion(), review);
        if (!review.complete()) {
            throw new ApiConflictException("REVIEW_INCOMPLETE",
                    "La revisión contiene información obligatoria faltante", review.execution().version());
        }

        validateDecision(command, review);
        Instant now = clock.instant();
        ComplianceCase complianceCase = cases.findByObligationId(obligation.getId())
                .orElseGet(() -> cases.saveAndFlush(new ComplianceCase(obligation.getId(),
                        obligation.getCompanyId(), obligation.getSiteId(), reviewer.getId(), now)));
        verifyExpectedVersion(command.expectedCaseVersion(), complianceCase.getDecisionVersion());

        ComplianceResult predecessor = results
                .findFirstByComplianceCaseIdOrderByDecisionVersionDesc(complianceCase.getId())
                .orElse(null);
        verifyReevaluation(command.reevaluationReason(), complianceCase.getDecisionVersion(), predecessor);

        long decisionVersion = complianceCase.appendDecision(now);
        cases.saveAndFlush(complianceCase);
        ComplianceResult result = results.saveAndFlush(new ComplianceResult(
                complianceCase.getId(), executionId, decisionVersion,
                predecessor == null ? null : predecessor.getId(), command.outcome(), review.reviewVersion(),
                review.execution().version(), review.obligation().sourcePlanId(),
                review.obligation().sourcePlanVersion(), review.obligation().sourceDefinitionId(),
                review.obligation().sourceDefinitionVersion(), command.explanation().trim(),
                decisionVersion == 1 ? null : command.reevaluationReason().trim(), reviewer.getId(), now,
                criteria(command.criteria()), findings(command.findings())));
        return response(complianceCase, result);
    }

    private static void verifyReviewVersion(String requested,
                                            ComplianceReviewService.ComplianceReview review) {
        if (!review.reviewVersion().equals(requested)) {
            throw new ApiConflictException("REVIEW_VERSION_CONFLICT",
                    "La versión revisada ya no es la versión vigente", review.execution().version());
        }
    }

    private static void verifyExpectedVersion(long expected, long current) {
        if (expected != current) {
            throw new ApiConflictException("VERSION_CONFLICT",
                    "El caso de cumplimiento cambió desde la última lectura", current);
        }
    }

    private static void verifyReevaluation(String reason, long currentVersion,
                                           ComplianceResult predecessor) {
        boolean hasReason = reason != null && !reason.isBlank();
        if (currentVersion == 0 && hasReason) {
            invalid("reevaluationReason", "No corresponde a la primera decisión");
        }
        if (currentVersion > 0 && (!hasReason || predecessor == null)) {
            invalid("reevaluationReason", "Es obligatorio para una reevaluación");
        }
    }

    private static void validateDecision(DecisionCommand command,
                                         ComplianceReviewService.ComplianceReview review) {
        Set<String> criterionCodes = new HashSet<>();
        if (command.criteria().stream().map(CriterionDecision::code).map(String::trim)
                .anyMatch(code -> !criterionCodes.add(code))) {
            invalid("criteria", "Los códigos de criterio no pueden repetirse");
        }

        Set<Long> reviewExceptionIds = review.exceptions().stream()
                .map(ComplianceReviewService.ExceptionReview::id).collect(Collectors.toSet());
        Map<Long, FindingDecision> exceptionFindings = new LinkedHashMap<>();
        for (FindingDecision finding : command.findings()) {
            boolean hasException = finding.exceptionId() != null;
            boolean hasDisposition = finding.exceptionDisposition() != null;
            if (hasException != hasDisposition) {
                invalid("findings", "exceptionId y exceptionDisposition deben enviarse juntos");
            }
            if (hasException && !reviewExceptionIds.contains(finding.exceptionId())) {
                invalid("findings", "Una excepción no pertenece a la revisión indicada");
            }
            if (hasException && exceptionFindings.putIfAbsent(finding.exceptionId(), finding) != null) {
                invalid("findings", "Cada excepción debe evaluarse una sola vez");
            }
        }

        boolean hasFailedCriterion = command.criteria().stream()
                .anyMatch(criterion -> criterion.status() == CriterionStatus.FAIL);
        boolean hasRejectedException = exceptionFindings.values().stream()
                .anyMatch(finding -> finding.exceptionDisposition() == ExceptionDisposition.REJECTED);
        boolean allExceptionsCovered = exceptionFindings.keySet().equals(reviewExceptionIds);

        switch (command.outcome()) {
            case COMPLIANT -> {
                if (!reviewExceptionIds.isEmpty() || hasFailedCriterion
                        || command.criteria().stream().anyMatch(value -> value.status() != CriterionStatus.PASS)) {
                    invalid("outcome", "COMPLIANT exige criterios aprobados y ninguna excepción");
                }
            }
            case EXCEPTION_ACCEPTED -> {
                boolean allAccepted = !exceptionFindings.isEmpty() && exceptionFindings.values().stream()
                        .allMatch(value -> value.exceptionDisposition() == ExceptionDisposition.ACCEPTED);
                if (reviewExceptionIds.isEmpty() || !allExceptionsCovered || !allAccepted || hasFailedCriterion) {
                    invalid("outcome", "EXCEPTION_ACCEPTED exige aceptar cada excepción y no tener criterios fallidos");
                }
            }
            case NON_COMPLIANT -> {
                if (!allExceptionsCovered || (!hasFailedCriterion && !hasRejectedException)) {
                    invalid("outcome", "NON_COMPLIANT exige un criterio fallido o una excepción rechazada");
                }
            }
        }
    }

    private static List<ComplianceCriterion> criteria(List<CriterionDecision> values) {
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> {
                    CriterionDecision value = values.get(index);
                    return new ComplianceCriterion(index + 1, value.code().trim(), value.status(),
                            trimToNull(value.notes()));
                }).toList();
    }

    private static List<ComplianceFinding> findings(List<FindingDecision> values) {
        return java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> {
                    FindingDecision value = values.get(index);
                    return new ComplianceFinding(index + 1, value.code().trim(), value.description().trim(),
                            value.exceptionId(), value.exceptionDisposition());
                }).toList();
    }

    private static ComplianceResultResponse response(ComplianceCase complianceCase, ComplianceResult result) {
        List<CriterionResponse> criteria = result.getCriteria().stream()
                .map(value -> new CriterionResponse(value.getCode(), value.getStatus(), value.getNotes()))
                .toList();
        List<FindingResponse> findings = result.getFindings().stream()
                .map(value -> new FindingResponse(value.getCode(), value.getDescription(),
                        value.getOperationalExceptionId(), value.getExceptionDisposition()))
                .toList();
        return new ComplianceResultResponse(complianceCase.getId(), complianceCase.getObligationId(),
                result.getId(), result.getExecutionId(), result.getDecisionVersion(),
                result.getPredecessorResultId(), result.getOutcome(), result.getSourceReviewVersion(),
                result.getSourceExecutionVersion(), result.getSourcePlanId(), result.getSourcePlanVersion(),
                result.getSourceDefinitionId(), result.getSourceDefinitionVersion(), result.getExplanation(),
                result.getReevaluationReason(), result.getDecidedBy(), result.getDecidedAt(), criteria, findings);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void invalid(String field, String message) {
        throw new FieldValidationException("La decisión de cumplimiento no es válida",
                Map.of(field, List.of(message)));
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("Ejecución de cumplimiento no encontrada");
    }

    public record DecisionCommand(
            @NotNull ComplianceOutcome outcome,
            @NotBlank @Size(max = 100) String reviewVersion,
            @NotNull @PositiveOrZero Long expectedCaseVersion,
            @NotEmpty @Size(max = 100) List<@Valid CriterionDecision> criteria,
            @NotNull @Size(max = 100) List<@Valid FindingDecision> findings,
            @NotBlank @Size(max = 4000) String explanation,
            @Size(max = 2000) String reevaluationReason) {}

    public record CriterionDecision(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
            @NotNull CriterionStatus status,
            @Size(max = 2000) String notes) {}

    public record FindingDecision(
            @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
            @NotBlank @Size(max = 2000) String description,
            Long exceptionId,
            ExceptionDisposition exceptionDisposition) {}

    public record ComplianceResultResponse(
            Long caseId, Long obligationId, Long resultId, Long executionId, long caseVersion,
            Long predecessorResultId, ComplianceOutcome outcome, String sourceReviewVersion,
            long sourceExecutionVersion, Long sourcePlanId, Integer sourcePlanVersion,
            Long sourceDefinitionId, Long sourceDefinitionVersion, String explanation,
            String reevaluationReason, Long decidedBy, Instant decidedAt,
            List<CriterionResponse> criteria, List<FindingResponse> findings) {}

    public record CriterionResponse(String code, CriterionStatus status, String notes) {}

    public record FindingResponse(String code, String description, Long exceptionId,
                                  ExceptionDisposition exceptionDisposition) {}
}
