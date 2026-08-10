package com.uninook.school;

import com.uninook.common.entity.SchoolEntity;

import java.math.BigDecimal;

public record SchoolResponse(
        Long id,
        Long universityId,
        String name,
        String campusName,
        String province,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        Double distanceKm
) {

    public static SchoolResponse from(SchoolEntity school) {
        return from(school, null);
    }

    public static SchoolResponse from(SchoolEntity school, Double distanceKm) {
        return new SchoolResponse(
                school.getId(),
                school.getUniversityId(),
                school.getName(),
                school.getCampusName(),
                school.getProvince(),
                school.getCity(),
                school.getLatitude(),
                school.getLongitude(),
                distanceKm
        );
    }
}
