package io.yak.ops.business.datasource.controller.v1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.yak.framework.common.Result;
import io.yak.framework.security.web.RequiresPermission;
import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.management.DataSourceDriverManager;
import io.yak.ops.common.bean.vo.datasource.DataSourceDriverUploadVO;
import io.yak.ops.common.constant.datasource.DataSourceConstants;
import io.yak.ops.common.constant.datasource.DataSourcePermissionCode;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 外置 JDBC 驱动包上传(Ticket 02):前端 DriverManager 组件的对接端点。 */
@Tag(name = "数据源驱动上传接口")
@RestController
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
@RequestMapping(DataSourceConstants.API_PREFIX + "/plugin/driver")
@RequiresPermission(DataSourcePermissionCode.READ)
public class DataSourceDriverController {

  private final DataSourceDriverManager driverManager;

  @Operation(summary = "上传 JDBC 驱动包")
  @PostMapping("/upload")
  @RequiresPermission(DataSourcePermissionCode.CREATE)
  public Result<DataSourceDriverUploadVO> upload(
      @RequestParam("pluginType") String pluginType, @RequestParam("file") MultipartFile file)
      throws IOException {
    String path = driverManager.upload(pluginType, file.getOriginalFilename(), file.getBytes());
    return Result.success(
        new DataSourceDriverUploadVO(pluginType, file.getOriginalFilename(), path));
  }
}
