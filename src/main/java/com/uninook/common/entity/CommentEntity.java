package com.uninook.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("`comment`")
public class CommentEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private Long authorSchoolId;
    private String authorSchoolName;
    private String authorCampusName;
    private Long rootCommentId;
    private Long parentCommentId;
    private Long replyToUserId;
    private Integer likeCount;
    private String content;
    private Integer status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getAuthorSchoolId() {
        return authorSchoolId;
    }

    public void setAuthorSchoolId(Long authorSchoolId) {
        this.authorSchoolId = authorSchoolId;
    }

    public String getAuthorSchoolName() {
        return authorSchoolName;
    }

    public void setAuthorSchoolName(String authorSchoolName) {
        this.authorSchoolName = authorSchoolName;
    }

    public String getAuthorCampusName() {
        return authorCampusName;
    }

    public void setAuthorCampusName(String authorCampusName) {
        this.authorCampusName = authorCampusName;
    }

    public Long getRootCommentId() {
        return rootCommentId;
    }

    public void setRootCommentId(Long rootCommentId) {
        this.rootCommentId = rootCommentId;
    }

    public Long getParentCommentId() {
        return parentCommentId;
    }

    public void setParentCommentId(Long parentCommentId) {
        this.parentCommentId = parentCommentId;
    }

    public Long getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(Long replyToUserId) {
        this.replyToUserId = replyToUserId;
    }

    public Integer getLikeCount() { return likeCount; }
    public void setLikeCount(Integer likeCount) { this.likeCount = likeCount; }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
