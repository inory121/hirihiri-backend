package com.hiiro.entity.dto;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 评论主id
     */
    @Schema(description = "评论主id", name = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 评论的视频id
     */
    @Schema(description = "评论的视频id", name = "vid")
    private Long vid;

    /**
     * 发送者id
     */
    @Schema(description = "发送者", name = "user")
    private UserDTO user;

    /**
     * 根节点评论的id,如果为0表示为根节点
     */
    @Schema(description = "根节点评论的id,如果为0表示为根节点", name = "rootId")
    private Integer rootId;

    /**
     * 被回复的评论id，只有root_id为0时才允许为0，表示根评论
     */
    @Schema(description = "被回复的评论id，只有root_id为0时才允许为0，表示根评论", name = "parentId")
    private Integer parentId;

    /**
     * 回复目标用户id
     */
    @Schema(description = "回复目标用户id", name = "toUserId")
    private UserDTO toUser;

    /**
     * 回复列表
     */
    @Schema(description = "回复列表")
    private List<CommentDTO> replies;

    /**
     * 评论内容
     */
    @Schema(description = "评论内容", name = "content")
    private String content;

    /**
     * 点赞数
     */
    @Schema(description = "点赞数", name = "like")
    @TableField("`like`")
    private Integer like;

    /**
     * 点踩数
     */
    @Schema(description = "点踩数", name = "dislike")
    private Integer dislike;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createDate")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createDate;

    /**
     * 是否置顶 0普通 1置顶
     */
    @Schema(description = "是否置顶 0普通 1置顶", name = "isTop")
    private Byte isTop;

    /**
     * 软删除 0未删除 1已删除
     */
    @Schema(description = "软删除 0未删除 1已删除", name = "isDeleted")
    private Byte isDeleted;
}
