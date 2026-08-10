package com.uninook.school;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.uninook.common.entity.SchoolEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

public interface SchoolMapper extends BaseMapper<SchoolEntity> {

    @Select("""
            SELECT id, university_id AS universityId, name, campus_name AS campusName,
                   province, city, latitude, longitude, status
            FROM school
            WHERE id = #{schoolId} AND status = 0
            """)
    Optional<SchoolEntity> findEnabledById(@Param("schoolId") Long schoolId);

    @Select("""
            SELECT id, university_id AS universityId, name, campus_name AS campusName,
                   province, city, latitude, longitude, status
            FROM school
            WHERE status = 0
              AND (name LIKE CONCAT('%', #{keyword}, '%')
                   OR campus_name LIKE CONCAT('%', #{keyword}, '%')
                   OR city LIKE CONCAT('%', #{keyword}, '%')
                   OR province LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY name
            LIMIT #{limit}
            """)
    List<SchoolEntity> searchEnabledSchools(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("""
            SELECT id, university_id AS universityId, name, campus_name AS campusName,
                   province, city, latitude, longitude, status
            FROM school
            WHERE status = 0
              AND latitude BETWEEN #{minLatitude} AND #{maxLatitude}
              AND longitude BETWEEN #{minLongitude} AND #{maxLongitude}
            ORDER BY name
            """)
    List<SchoolEntity> findEnabledWithinBounds(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude);

    @Select("SELECT id FROM school WHERE university_id = #{universityId} AND status = 0 ORDER BY id")
    List<Long> findEnabledIdsByUniversityId(@Param("universityId") Long universityId);

    @Select("SELECT id FROM school WHERE city = #{city} AND status = 0 ORDER BY id")
    List<Long> findEnabledIdsByCity(@Param("city") String city);

    @Select("SELECT DISTINCT province FROM school WHERE status = 0 ORDER BY province")
    List<String> findEnabledProvinces();

    @Select("SELECT DISTINCT city FROM school WHERE province = #{province} AND status = 0 ORDER BY city")
    List<String> findEnabledCitiesByProvince(@Param("province") String province);

    @Select("""
            SELECT id, university_id AS universityId, name, campus_name AS campusName,
                   province, city, latitude, longitude, status
            FROM school
            WHERE status = 0
              AND province = #{province}
              AND city = #{city}
              AND (#{keyword} IS NULL OR #{keyword} = ''
                   OR name LIKE CONCAT('%', #{keyword}, '%')
                   OR campus_name LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY name, campus_name, id
            LIMIT #{limit}
            """)
    List<SchoolEntity> findEnabledCampuses(
            @Param("province") String province,
            @Param("city") String city,
            @Param("keyword") String keyword,
            @Param("limit") int limit);
}
