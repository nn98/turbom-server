package com.nextstep.domain.businesstype;

import java.util.Set;

public record RelatedTypeKeys(Set<BusinessTypeKey> keys) {

    public static RelatedTypeKeys none() {
        return new RelatedTypeKeys(Set.of());
    }

    public boolean pairsWith(BusinessTypeKey other) {
        return keys.contains(other);
    }

    public Set<BusinessTypeKey> values() {
        return keys;
    }
}
