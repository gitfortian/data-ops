package io.yak.ops.business.semantic.preset;

import io.yak.ops.business.semantic.api.Standard;
import io.yak.ops.business.semantic.api.StandardKind;

/** 一条平台预置标准模板(无项目归属);初始化时复制为项目标准行。 */
public record PresetTemplate(
    Long id,
    StandardKind kind,
    String code,
    String name,
    String description,
    int sortOrder,
    Standard.KindFields fields) {}
