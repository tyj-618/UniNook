package com.campuscircle.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("question_answer")
public class QuestionAnswerEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long questionId;
    private Long commentId;
    private Long answererId;
    private String status;
    private Long reviewedBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getCommentId() { return commentId; }
    public void setCommentId(Long commentId) { this.commentId = commentId; }
    public Long getAnswererId() { return answererId; }
    public void setAnswererId(Long answererId) { this.answererId = answererId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Long reviewedBy) { this.reviewedBy = reviewedBy; }
}
