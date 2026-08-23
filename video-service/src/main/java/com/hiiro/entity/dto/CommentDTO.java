package com.hiiro.entity.dto;

import com.alibaba.fastjson2.JSONObject;
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
    private Long id;

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
    private Long parentId;

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
     * 评论内容中 @ 的用户列表（uid→用户名/头像），前端据此零请求渲染可点击 @提及
     */
    @Schema(description = "评论中@的用户列表", name = "mentionUsers")
    private List<JSONObject> mentionUsers;

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
     * 当前用户是否点赞
     */
    @Schema(description = "当前用户是否点赞", name = "liked")
    private Boolean liked;

    /**
     * 当前用户是否点踩
     */
    @Schema(description = "当前用户是否点踩", name = "disliked")
    private Boolean disliked;

    /**
     * UP主是否点赞
     */
    @Schema(description = "UP主是否点赞", name = "upLiked")
    private Boolean upLiked;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", name = "createTime")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

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
