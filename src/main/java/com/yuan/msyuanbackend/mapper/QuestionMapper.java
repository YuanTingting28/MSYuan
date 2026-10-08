package com.yuan.msyuanbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuan.msyuanbackend.model.entity.Question;
import com.yuan.msyuanbackend.model.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * 用户数据库操作
 */
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 增量同步用:查出某个游标之后被 【新增或修改】的题目
     * 为什么不用简单的updateTime -> #{time}
     * MySql 的datetime 只精确到秒， “大于”会把同一秒的记录漏掉
     * 所以用updateTime + id 的符合游标，配合调用方把水位线回退 1 秒 保证不漏也不死循环
     * 意：这是自己写的 SQL，MyBatis-Plus 的逻辑删除拦截不到它，
     * 复合游标方案
     * 用 (updateTime, id) 组成复合游标：
     * sql
     * where updateTime > #{time}
     * or (updateTime = #{time} and id > #{id})
     * 这样即使同一秒内有多个 id，也能通过 id 继续往后翻，不重不漏。
     * * 所以这里必须自己写 isDelete = 0。
     * 增量同步的核心问题是：如何知道哪些数据变了？ 答案是用 updateTime 作为水位线（游标）。
     */
    @Select("select * from question where isDelete = 0 "
            + "and (updateTime>#{time} or (updateTime = #{time} and id > #{id}))"
            + "order by updateTime asc,id asc limit #{size} ")
    List<Question> listChangeAfter(@Param("time") Date time, @Param("id") Long id, @Param("size") int size);

    /**
     * 增量同步用：查出某个游标之后被【逻辑删除】的题目 id（用来从 ES 里删掉）
     */
    @Select("select id,updateTime from question where isDelete = 1 "
            + "and (updateTime > #{time} or (updateTime = #{time} and id > #{id}))"
            + "order by updateTime asc, id asc limit #{size}")
    List<Question> listDeletedAfter(@Param("time") Date time, @Param("id") Long id, @Param("size") int size);
}




