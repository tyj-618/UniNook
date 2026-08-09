package com.campuscircle.school;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.common.entity.SchoolEntity;
import com.campuscircle.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class SchoolService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double MAX_RADIUS_KM = 50.0;

    private final SchoolMapper schoolMapper;
    private final NearbySchoolCacheStore nearbySchoolCacheStore;

    public SchoolService(SchoolMapper schoolMapper, NearbySchoolCacheStore nearbySchoolCacheStore) {
        this.schoolMapper = schoolMapper;
        this.nearbySchoolCacheStore = nearbySchoolCacheStore;
    }

    public List<SchoolResponse> searchSchools(String keyword, int limit) {
        String cleanedKeyword = keyword == null ? "" : keyword.trim();
        if (cleanedKeyword.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "搜索关键词不能为空");
        }

        return schoolMapper.searchEnabledSchools(cleanedKeyword, limit).stream()
                .map(SchoolResponse::from)
                .toList();
    }

    public List<SchoolResponse> listNearbySchools(Long schoolId, double radiusKm) {
        SchoolEntity center = findEnabledSchool(schoolId);
        validateRadius(radiusKm);

        return nearbySchoolCacheStore.listNearbySchools(schoolId, radiusKm, () ->
                schoolMapper.findEnabledWithinBounds(
                                latitudeLowerBound(center, radiusKm),
                                latitudeUpperBound(center, radiusKm),
                                longitudeLowerBound(center, radiusKm),
                                longitudeUpperBound(center, radiusKm))
                        .stream()
                        .map(school -> SchoolResponse.from(school, distanceKm(center, school)))
                        .filter(response -> response.distanceKm() <= radiusKm)
                        .sorted(Comparator.comparing(SchoolResponse::distanceKm))
                        .toList());
    }

    public List<Long> listNearbySchoolIds(Long schoolId, double radiusKm) {
        return listNearbySchools(schoolId, radiusKm).stream()
                .map(SchoolResponse::id)
                .toList();
    }

    public List<Long> listScopeSchoolIds(Long schoolId, CampusScope scope) {
        SchoolEntity center = findEnabledSchool(schoolId);
        return switch (scope) {
            case CAMPUS -> List.of(center.getId());
            case UNIVERSITY -> center.getUniversityId() == null
                    ? List.of(center.getId())
                    : schoolMapper.findEnabledIdsByUniversityId(center.getUniversityId());
            case NEARBY_10 -> listNearbySchoolIds(center.getId(), 10);
            case NEARBY_20 -> listNearbySchoolIds(center.getId(), 20);
            case CITY -> schoolMapper.findEnabledIdsByCity(center.getCity());
        };
    }

    public List<String> listProvinces() {
        return schoolMapper.findEnabledProvinces();
    }

    public List<String> listCities(String province) {
        if (province == null || province.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "省份不能为空");
        }
        return schoolMapper.findEnabledCitiesByProvince(province.trim());
    }

    public List<SchoolResponse> listCampuses(String province, String city, String keyword, int limit) {
        if (province == null || province.isBlank() || city == null || city.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "省份和城市不能为空");
        }
        String cleanedKeyword = keyword == null ? null : keyword.trim();
        return schoolMapper.findEnabledCampuses(province.trim(), city.trim(), cleanedKeyword, limit).stream()
                .map(SchoolResponse::from)
                .toList();
    }

    public SchoolEntity findEnabledSchool(Long schoolId) {
        if (schoolId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "学校不能为空");
        }
        return schoolMapper.findEnabledById(schoolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "学校不存在或已禁用"));
    }

    private void validateRadius(double radiusKm) {
        if (radiusKm <= 0 || radiusKm > MAX_RADIUS_KM) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "查看范围必须在 0 到 50 公里之间");
        }
    }

    private double distanceKm(SchoolEntity source, SchoolEntity target) {
        double lat1 = toDouble(source.getLatitude());
        double lng1 = toDouble(source.getLongitude());
        double lat2 = toDouble(target.getLatitude());
        double lng2 = toDouble(target.getLongitude());

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(EARTH_RADIUS_KM * c * 100.0) / 100.0;
    }

    private double latitudeLowerBound(SchoolEntity center, double radiusKm) {
        return toDouble(center.getLatitude()) - radiusKm / 111.32;
    }

    private double latitudeUpperBound(SchoolEntity center, double radiusKm) {
        return toDouble(center.getLatitude()) + radiusKm / 111.32;
    }

    private double longitudeLowerBound(SchoolEntity center, double radiusKm) {
        return toDouble(center.getLongitude()) - longitudeDelta(center, radiusKm);
    }

    private double longitudeUpperBound(SchoolEntity center, double radiusKm) {
        return toDouble(center.getLongitude()) + longitudeDelta(center, radiusKm);
    }

    private double longitudeDelta(SchoolEntity center, double radiusKm) {
        double cosine = Math.cos(Math.toRadians(toDouble(center.getLatitude())));
        return radiusKm / (111.32 * Math.max(cosine, 0.01));
    }

    private double toDouble(BigDecimal value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "学校坐标缺失");
        }
        return value.doubleValue();
    }
}
