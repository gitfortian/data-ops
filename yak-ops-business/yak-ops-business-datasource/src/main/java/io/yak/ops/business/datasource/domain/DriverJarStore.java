package io.yak.ops.business.datasource.domain;

/** 驱动 jar 落盘端口:gateway 侧提供本地存储实现,management 只依赖本接口。 */
public interface DriverJarStore {

  /** 存储驱动包并注册,返回相对存储根目录的路径(以 '/' 分隔)。 */
  String store(String dbType, String fileName, byte[] content);
}
