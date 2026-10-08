package com.yuan.msyuanbackend.model.dto.question;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionEsDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 题目 id（同时也是 ES 文档的 _id）
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 推荐答案
     */
    private String answer;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 创建时间（毫秒时间戳）
     */
    private Long createTime;

    /**
     * 编辑时间（毫秒时间戳）
     */
    private Long editTime;

    /**
     * 更新时间（毫秒时间戳）
     */
    private Long updateTime;
}
