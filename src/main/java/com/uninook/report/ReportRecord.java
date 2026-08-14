package com.uninook.report;

public class ReportRecord {
    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReporterId() { return reporterId; }
    public void setReporterId(Long reporterId) { this.reporterId = reporterId; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
