package com.figureerp.module.message.controller;

import com.figureerp.common.result.PageResult;
import com.figureerp.common.result.Result;
import com.figureerp.module.message.converter.MessageDict;
import com.figureerp.module.message.dto.MessageHandleRequest;
import com.figureerp.module.message.dto.MessageQuery;
import com.figureerp.module.message.dto.MessageSendRequest;
import com.figureerp.module.message.service.MessageService;
import com.figureerp.module.message.vo.MessageSummaryVO;
import com.figureerp.module.message.vo.MessageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 个人消息接口（右上角用户菜单 → 个人消息）。
 * <p>
 * 权限：所有接口都需要登录；消息按接收人隔离，Service 里强制只操作当前用户的消息。
 * 只有「发送消息」额外要求管理员或主管。
 */
@Tag(name = "个人消息", description = "站内消息：分页查询、未读统计、标记已读、处理、删除与发送")
@RestController
@RequestMapping("/api/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @Operation(summary = "分页查询我的消息", description = "可按类型/状态/优先级过滤，关键字匹配标题与正文")
    @GetMapping("/page")
    public Result<PageResult<MessageVO>> page(@Valid MessageQuery q) {
        return Result.success(messageService.page(q));
    }

    @Operation(summary = "未读统计", description = "总数 / 未读数 / 紧急未读 / 已处理 / 按类型统计，前端轮询这一个接口")
    @GetMapping("/summary")
    public Result<MessageSummaryVO> summary() {
        return Result.success(messageService.summary());
    }

    @Operation(summary = "消息类型字典", description = "新增消息种类时前端下拉自动跟上，不用改前端代码")
    @GetMapping("/types")
    public Result<List<Map<String, String>>> types() {
        List<Map<String, String>> list = MessageDict.typeEntries().stream()
                .map(e -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("code", e.getKey());
                    item.put("text", e.getValue());
                    return item;
                })
                .toList();
        return Result.success(list);
    }

    @Operation(summary = "消息详情", description = "未读消息查看详情时自动标记为已读")
    @GetMapping("/{id}")
    public Result<MessageVO> detail(@PathVariable Long id) {
        return Result.success(messageService.detail(id));
    }

    @Operation(summary = "标记已读")
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        messageService.markRead(id);
        return Result.success("已标记为已读", null);
    }

    @Operation(summary = "全部标为已读", description = "可传 msgType 只处理某一类型")
    @PutMapping("/read-all")
    public Result<Integer> markAllRead(@RequestParam(required = false) String msgType) {
        int updated = messageService.markAllRead(msgType);
        return Result.success(updated > 0 ? "已标记 " + updated + " 条为已读" : "没有未读消息", updated);
    }

    @Operation(summary = "处理消息", description = "状态置为已处理，可带处理备注")
    @PutMapping("/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody(required = false) MessageHandleRequest req) {
        messageService.handle(id, req == null ? null : req.getRemark());
        return Result.success("已处理", null);
    }

    @Operation(summary = "删除消息", description = "逻辑删除，只能删自己的消息")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageService.delete(id);
        return Result.success("已删除", null);
    }

    @Operation(summary = "发送消息", description = "管理员或主管给指定员工发一条站内消息，用于运营通知与联调")
    @PostMapping
    public Result<Long> send(@Valid @RequestBody MessageSendRequest req) {
        return Result.success("发送成功", messageService.send(req));
    }
}
