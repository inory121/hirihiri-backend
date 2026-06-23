package com.hiiro.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 评论分页响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 根评论列表（当前页）
     */
    private List<CommentDTO> comments;

    /**
     * 根评论总数（用于前端计算总页数）
     */
    private long total;

    /**
     * 当前页码
     */
    private int page;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 是否还有更多数据
     */
    private boolean hasMore;
}
