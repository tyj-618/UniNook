package com.campuscircle.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("question")
public class QuestionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sourceType;
    private Long sourceId;
    private Long askerId;
    private String questionText;
    private String status;
    private Long acceptedAnswerId;
    private Long subscriberCount;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public Long getAskerId() { return askerId; }
    public void setAskerId(Long askerId) { this.askerId = askerId; }
    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAcceptedAnswerId() { return acceptedAnswerId; }
    public void setAcceptedAnswerId(Long acceptedAnswerId) { this.acceptedAnswerId = acceptedAnswerId; }
    public Long getSubscriberCount() { return subscriberCount; }
    public void setSubscriberCount(Long subscriberCount) { this.subscriberCount = subscriberCount; }
}
