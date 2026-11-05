package com.api.servicecompliance.serviceplanning.application;

public interface SiteAuthorizationPort {
    boolean belongsToCompany(Long siteId, Long companyId);
}
