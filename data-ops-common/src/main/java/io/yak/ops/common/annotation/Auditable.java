package io.yak.ops.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 为写接口声明可读的审计语义；缺省项由审计兜底拦截器从 HTTP method + 路径推导。
 * 消费方为 boot 层的 Web 审计拦截器，未标注的写接口仍会被兜底留痕。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Auditable {

  /** 可读操作名，如“发布模型”。为空时用推导名称。 */
  String name() default "";

  /** 操作类型码（MODULE_ACTION 规范，见 AuditOperationTypes）。为空时用推导类型。 */
  String type() default "";

  /** 资源类型，如 MODEL。为空时用推导资源类型。 */
  String resourceType() default "";

  /** 资源标识来源的路径变量/参数名，如 id。为空时取同名路径变量。 */
  String resourceIdParam() default "";

  /** true 时把脱敏后的请求体写入审计 metadata_json.payload。默认不记录请求体。 */
  boolean recordPayload() default false;

  /** true 时完全跳过审计兜底（内部心跳、批处理入口等自带留痕的接口）。 */
  boolean ignore() default false;
}
