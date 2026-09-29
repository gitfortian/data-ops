package io.yak.ops.business.datasource.config;

/**
 * 数据源连接凭证的静态加密端口(Ticket 05)。
 *
 * <p>端口放在 config 包是因为唯一的生产消费方是 DAO 读写钩子，而 DAO 层只允许依赖 config；
 * 实现位于 gateway/adapter，这样加密失败可以按业务异常(41017/41018)抛出。
 *
 * <p>约定：未配置密钥时整体退化为明文直通；密文以 {@code ENC:} 前缀自描述，因此读到的历史明文行
 * 仍可解密，下一次写入自然升级为密文（惰性升级）。
 */
public interface CredentialCipher {

  /** 密文前缀；JSON 列不可能以它开头，因此可以无歧义地区分明文与密文。 */
  String ENCRYPTED_PREFIX = "ENC:";

  /** 是否已配置可用密钥；false 表示明文兼容模式。 */
  boolean isEnabled();

  /** 加密以落库的值：明文直通(无密钥)、空值与已加密值原样返回。 */
  String encrypt(String value);

  /** 解密从库里读出的值：无前缀按明文放行；已加密但缺密钥时抛 41017。 */
  String decrypt(String value);
}
