package io.yak.ops.business.agent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Agent 模块配置。模型密钥只应经环境变量注入，不落库、不进日志。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yak.agent")
public class AgentProperties {

  private final Model model = new Model();
  private final StateStore stateStore = new StateStore();
  private final Chat chat = new Chat();
  private final Turn turn = new Turn();
  private final Compaction compaction = new Compaction();
  private final Observability observability = new Observability();
  private final Memory memory = new Memory();
  private final Query query = new Query();
  private final Python python = new Python();
  private final Suggestions suggestions = new Suggestions();
  private final Execution execution = new Execution();

  @Getter
  @Setter
  public static class Execution {
    private int maxToolCalls = 32;
    private int maxFailuresPerTool = 3;
    private int maxModelInputChars = 120000;
  }

  @Getter
  @Setter
  public static class Suggestions {
    /** Source-owned validation and original-editor adoption only. */
    private boolean enabled = true;
  }

  /** 开发期直连允许的来源模式，逗号分隔；留空关闭 CORS。
   *  默认放行任意主机的 dev server 端口（8000）及前端构建服务端口（3000/80/8080）。 */
  private String corsAllowedOriginPatterns = "http://*:8000,https://*:8000,http://*:3000,http://*:80,http://*:8080";

  @Getter
  @Setter
  public static class Model {
    /** Current runtime supports openai, including compatible endpoints. */
    private String provider = "openai";
    private String name = "";
    private String baseUrl = "";
    private String apiKey = "";
    /** 思维链开关：high/medium/low；留空不启用。经网关的 DeepSeek-V4 需设置才会返回推理内容。 */
    private String reasoningEffort = "";
    /** Explicit provider capability declarations; compatible gateways must be verified before enabling. */
    private boolean nativeStructuredOutput = false;
    private boolean nativeStructuredOutputWithTools = false;
  }

  @Getter
  @Setter
  public static class StateStore {
    /** 为空时使用连接默认数据库。 */
    private String database = "";
    private String table = "agentscope_sessions";
  }

  @Getter
  @Setter
  public static class Chat {
    private int maxIters = 10;
    /** 单轮推理整体硬超时（秒）；<=0 表示不启用。 */
    private int turnTimeoutSeconds = 300;
    /** 单次模型调用硬超时（秒）。超时不重试；<=0 表示不启用闸门。 */
    private int llmCallTimeoutSeconds = 120;
    /** 可重试错误（5xx/网络类）的最大重试次数；每次尝试独立落步骤记录。 */
    private int llmMaxRetries = 3;
  }

  /**
   * 长对话压缩（框架 CompactionMiddleware）：跨轮上下文超窗前由 LLM 摘要收敛。
   *
   * <p>默认阈值说明：OpenAIChatModel 未覆写 {@code getContextWindowSize()}（返回 0），
   * 框架动态适配逻辑无法生效，因此必须通过 {@code contextWindowSize} 显式声明模型上下文窗口。
   *
   * <p>取值依据：内部接入模型（DeepSeek-V4 等）典型 context window 为 32k-128k；
   * 保守取 32000 作为默认值，确保低配模型也能触发压缩。生产环境按实际模型能力调整。
   */
  @Getter
  @Setter
  public static class Compaction {
    private boolean enabled = true;
    /** 工作区目录；留空使用系统临时目录下的固定子目录（内部部署够用）。 */
    private String workspaceDir = "";
    /**
     * 模型上下文窗口大小（token 数）。
     * 必须显式配置，因为 OpenAIChatModel 不报告此值（默认返回 0）。
     * <=0 表示不启用 token 维度触发（仅靠 triggerMessages 触发）。
     */
    private int contextWindowSize = 32000;
    /** 触发压缩的消息数阈值；消息数达到此值时触发压缩。 */
    private int triggerMessages = 50;
    /** 预留给系统提示词与模型输出的 token 数。 */
    private int reserved = 4000;
    /** 压缩后保留的消息数。 */
    private int keepMessages = 20;
  }

  /** 提交/执行分离配置：HTTP 线程只入队，Dispatcher/Executor 异步消费。 */
  @Getter
  @Setter
  public static class Turn {
    /** 后台执行线程池大小。 */
    private int workerPoolSize = 2;
    /** In-memory waiting tasks; the durable QUEUED row remains the retry source. */
    private int queueCapacity = 16;
    /** 排队扫描周期（毫秒）。提交侧另有即时唤醒，无需调小。 */
    private long queuePollMillis = 2000L;
    /** SSE 订阅端尾随轮询周期（毫秒）：事件帧先落库再被拉取，天然断线可续播。 */
    private long sseTailPollMillis = 200L;
    /** 订阅端单次最大补发帧数（防大轮次一次撑爆连接缓冲）。 */
    private int replayBatchSize = 100;
  }

  /**
   * 可观测性运行时治理（O4）：全部取值调用时现读、严禁静态缓存（v1.2 §七不变式 5），
   * 接入 Phase 2 配置热更（yak_config per-key）后即达"改值无重启生效"——键位已登记为首批候选。
   */
  @Getter
  @Setter
  public static class Observability {
    /** 观测总开关：false 时步骤记录零写入（trace 读路径不受影响，存量数据照常可查）。 */
    private boolean enabled = true;
    /** 载荷 INLINE 档单字段落库上限（字符；沿用历史 8000 截断语义）。 */
    private int maxInlineChars = 8000;
    /**
     * per-kind 开关覆盖：key=kind（如 TOOL_CALL / LLM_CALL），value=false 关闭该类记录；
     * 未登记的 kind 默认开启。调试期可只开高保真档。
     */
    private java.util.Map<String, Boolean> kindEnabled = new java.util.HashMap<>();
    /**
     * 排障升档：LLM 请求载荷临时改走 INLINE 档（默认 SUMMARY_HASH，原文不落库）。
     * 仅排障窗口开启，用完即关——原文落库会扩大敏感面（设计稿 §五）。
     */
    private boolean llmRequestInlineDebug = false;
    /** OpenTelemetry 分布式追踪（官方 TelemetryTracer，OTLP 导出到 Jaeger/Tempo）。 */
    private final Otel otel = new Otel();
    /** AgentScope Studio 可视化调试（独立 Web UI，默认关闭）。 */
    private final Studio studio = new Studio();

    @Getter
    @Setter
    public static class Otel {
      /** 总开关：false 时不注册 OTel Tracer（NoopTracer，零开销）。 */
      private boolean enabled = false;
      /** OTLP gRPC 端点（Jaeger/Tempo/Collector）。 */
      private String endpoint = "http://localhost:4317";
      /** 额外 OTLP 头（如鉴权），key=value。 */
      private java.util.Map<String, String> headers = new java.util.HashMap<>();
    }

    @Getter
    @Setter
    public static class Studio {
      /** 总开关：false 时不连接 Studio。 */
      private boolean enabled = false;
      /** Studio WebSocket 服务地址。 */
      private String serverUrl = "http://localhost:3000";
      /** Studio 追踪地址（留空沿用 serverUrl）。 */
      private String tracingUrl = "";
      /** 项目名（Studio 中归属）。 */
      private String project = "yak-agent";
      /** 运行名（如服务实例标识；留空自动生成）。 */
      private String runName = "";
    }
  }

  /**
   * 长期记忆（记忆线 M1，data-agent-long-term-memory-design.md）：
   * 写读闭环的最小配置集。取值全部调用时现读（Phase 2 配置热更首批候选键位）。
   */
  @Getter
  @Setter
  public static class Memory {
    /** 记忆总开关：false 时不提取、不召回（写读两侧全关）。 */
    private boolean enabled = true;
    /** 提取 THROTTLED 闸门：同会话两次提取的最小间隔（毫秒，默认 5min）。 */
    private long flushMinIntervalMillis = 300_000L;
    /** 单次注入检索条数上限。 */
    private int recallTopK = 8;
    /** 注入总字符预算（硬上限）。 */
    private int recallMaxChars = 2000;
    /** 记忆命中排序的半衰期（小时，默认 30 天）。 */
    private long decayHalfLifeHours = 720L;
    /** 低于该置信度的条目不进注入候选。 */
    private double minConfidence = 0.30;
  }

  @Getter
  @Setter
  public static class Query {
    private int defaultLimit = 200;
    private int maxLimit = 1000;
  }

  @Getter
  @Setter
  public static class Python {
    private boolean enabled = false;
    private String command = "python3";
    private int maxConcurrent = 5;
    private int timeoutSeconds = 30;
  }
}
