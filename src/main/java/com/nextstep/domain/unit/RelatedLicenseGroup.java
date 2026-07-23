package com.nextstep.domain.unit;

import com.nextstep.domain.businesstype.BusinessTypeKey;
import com.nextstep.domain.tenancy.Tenancy;
import java.util.List;

public record RelatedLicenseGroup(List<BusinessTypeKey> businessTypeKeys, List<Tenancy> tenancies) {
}
