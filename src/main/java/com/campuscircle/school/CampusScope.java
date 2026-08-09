package com.campuscircle.school;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;

/**
 * Discovery and AI retrieval boundaries. Direct links and interactions deliberately do not use this scope.
 */
public enum CampusScope {
    CAMPUS,
    UNIVERSITY,
    NEARBY_10,
    NEARBY_20,
    CITY;

    public static CampusScope resolve(CampusScope scope, Double legacyRadiusKm) {
        if (scope != null) {
            return scope;
        }
        if (legacyRadiusKm == null) {
            return NEARBY_10;
        }
        if (legacyRadiusKm <= 10) {
            return NEARBY_10;
        }
        if (legacyRadiusKm <= 20) {
            return NEARBY_20;
        }
        if (legacyRadiusKm <= 50) {
            return CITY;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "查看范围不合法");
    }
}
