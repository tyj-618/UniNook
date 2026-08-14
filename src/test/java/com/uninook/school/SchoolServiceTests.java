package com.uninook.school;

import com.uninook.common.entity.SchoolEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolServiceTests {

    @Test
    void returnsEveryCampusProvidedByTheCatalogForTheSelectedCity() {
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        NearbySchoolCacheStore cacheStore = mock(NearbySchoolCacheStore.class);
        SchoolService schoolService = new SchoolService(schoolMapper, cacheStore);
        when(schoolMapper.findEnabledCampuses("江苏省", "南京市", null, 50)).thenReturn(List.of(
                school(1L, 1L, "南京大学", "仙林校区"),
                school(5L, 1L, "南京大学", "鼓楼校区"),
                school(2L, 2L, "东南大学", "九龙湖校区"),
                school(6L, 2L, "东南大学", "四牌楼校区"),
                school(3L, 3L, "南京航空航天大学", "将军路校区")
        ));

        List<SchoolResponse> campuses = schoolService.listCampuses("江苏省", "南京市", null, 50);

        assertThat(campuses)
                .extracting(SchoolResponse::campusName)
                .containsExactly("仙林校区", "鼓楼校区", "九龙湖校区", "四牌楼校区", "将军路校区");
        verify(schoolMapper).findEnabledCampuses("江苏省", "南京市", null, 50);
    }

    private SchoolEntity school(Long id, Long universityId, String name, String campusName) {
        SchoolEntity entity = new SchoolEntity();
        entity.setId(id);
        entity.setUniversityId(universityId);
        entity.setName(name);
        entity.setCampusName(campusName);
        entity.setProvince("江苏省");
        entity.setCity("南京市");
        entity.setLatitude(new BigDecimal("32.000000"));
        entity.setLongitude(new BigDecimal("118.000000"));
        entity.setStatus(0);
        return entity;
    }
}
