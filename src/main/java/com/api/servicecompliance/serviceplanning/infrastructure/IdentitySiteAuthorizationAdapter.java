package com.api.servicecompliance.serviceplanning.infrastructure;

import com.api.servicecompliance.authentication.domain.repository.SiteScopeRepository;
import com.api.servicecompliance.serviceplanning.application.SiteAuthorizationPort;
import org.springframework.stereotype.Component;

@Component
public class IdentitySiteAuthorizationAdapter implements SiteAuthorizationPort {
    private final SiteScopeRepository sites;

    public IdentitySiteAuthorizationAdapter(SiteScopeRepository sites) {
        this.sites = sites;
    }

    @Override
    public boolean belongsToCompany(Long siteId, Long companyId) {
        return sites.existsByIdAndCompanyId(siteId, companyId);
    }
}
