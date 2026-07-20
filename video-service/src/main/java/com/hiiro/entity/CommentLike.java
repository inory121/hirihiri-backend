package com.hiiro.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 评论点赞记录表
 *
 * @author hiiro
 * @since 2025-06-26
 */
@Data
@TableName("comment_like")
@Tag(name = "CommentLike对象", description = "评论点赞记录表")
public class CommentLike implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @Schema(description = "用户ID", name = "uid")
    private Long uid;

    /**
     * 评论ID
     */
    @Schema(description = "评论ID", name = "commentId")
    private Long commentId;

    /**
     * 点赞时间
     */
    @Schema(description = "点赞时间", name = "createTime")
    private LocalDateTime createTime;
}
