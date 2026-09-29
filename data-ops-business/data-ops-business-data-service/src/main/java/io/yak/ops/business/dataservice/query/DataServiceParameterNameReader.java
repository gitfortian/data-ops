package io.yak.ops.business.dataservice.query;

import java.util.List;

/** Read-side port for deriving the named parameters exposed by one Data Service SQL template. */
public interface DataServiceParameterNameReader {

  List<String> parameterNames(String sql);
}
