package com.uninook.report;

import com.uninook.auth.CurrentUserService;
import com.uninook.common.ErrorCode;
import com.uninook.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class ReportService {

    private final CurrentUserService currentUserService;
    private final ReportMapper reportMapper;

    public ReportService(CurrentUserService currentUserService, ReportMapper reportMapper) {
        this.currentUserService = currentUserService;
        this.reportMapper = reportMapper;
    }

    @Transactional
    public Long create(String authorization, CreateReportRequest request) {
        Long reporterId = currentUserService.requireUserId(authorization);
        ReportTargetType targetType = parseTargetType(request.targetType());
        Long authorId = findTargetAuthor(targetType, request.targetId());
        if (authorId == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "举报对象不存在或已不可用");
        }
        if (reporterId.equals(authorId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不能举报自己发布的内容");
        }
        if (reportMapper.countPendingReports(reporterId, targetType.name(), request.targetId()) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "您已举报过该内容，处理完成前请勿重复提交");
        }

        ReportRecord report = new ReportRecord();
        report.setReporterId(reporterId);
        report.setTargetType(targetType.name());
        report.setTargetId(request.targetId());
        report.setReason(request.reason().trim());
        reportMapper.insert(report);
        return report.getId();
    }

    private Long findTargetAuthor(ReportTargetType targetType, Long targetId) {
        return targetType == ReportTargetType.POST
                ? reportMapper.findNormalPostAuthorId(targetId)
                : reportMapper.findNormalCommentAuthorId(targetId);
    }

    private ReportTargetType parseTargetType(String value) {
        try {
            return ReportTargetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "举报对象类型仅支持 POST 或 COMMENT");
        }
    }
}
