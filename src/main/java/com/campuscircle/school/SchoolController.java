package com.campuscircle.school;

import com.campuscircle.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/schools")
public class SchoolController {

    private final SchoolService schoolService;

    public SchoolController(SchoolService schoolService) {
        this.schoolService = schoolService;
    }

    @GetMapping("/search")
    public ApiResponse<List<SchoolResponse>> searchSchools(
            @RequestParam @NotBlank String keyword,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return ApiResponse.success(schoolService.searchSchools(keyword, limit));
    }

    @GetMapping("/nearby")
    public ApiResponse<List<SchoolResponse>> listNearbySchools(
            @RequestParam Long schoolId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(50) double radiusKm) {
        return ApiResponse.success(schoolService.listNearbySchools(schoolId, radiusKm));
    }

    @GetMapping("/provinces")
    public ApiResponse<List<String>> listProvinces() {
        return ApiResponse.success(schoolService.listProvinces());
    }

    @GetMapping("/cities")
    public ApiResponse<List<String>> listCities(@RequestParam @NotBlank String province) {
        return ApiResponse.success(schoolService.listCities(province));
    }

    @GetMapping("/campuses")
    public ApiResponse<List<SchoolResponse>> listCampuses(
            @RequestParam @NotBlank String province,
            @RequestParam @NotBlank String city,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return ApiResponse.success(schoolService.listCampuses(province, city, keyword, limit));
    }
}
