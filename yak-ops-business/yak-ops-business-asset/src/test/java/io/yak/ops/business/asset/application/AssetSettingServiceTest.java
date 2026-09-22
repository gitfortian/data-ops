package io.yak.ops.business.asset.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.asset.dao.mapper.AssetSettingMapper;
import io.yak.ops.common.bean.po.asset.AssetSettingPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** KV 设置:缺省值容错 + 有则改之无则加勉。 */
class AssetSettingServiceTest {

  private AssetSettingMapper mapper;
  private AssetSettingService service;

  @BeforeEach
  void setUp() {
    mapper = Mockito.mock(AssetSettingMapper.class);
    service = new AssetSettingService(mapper);
  }

  @Test
  void getIntFallsBackOnMissingOrBadValue() {
    when(mapper.selectOne(any())).thenReturn(null);
    assertEquals(7, service.getInt(1L, "k", 7));

    AssetSettingPO bad = new AssetSettingPO();
    bad.setSettingValue("abc");
    when(mapper.selectOne(any())).thenReturn(bad);
    assertEquals(7, service.getInt(1L, "k", 7));

    AssetSettingPO good = new AssetSettingPO();
    good.setSettingValue("14");
    when(mapper.selectOne(any())).thenReturn(good);
    assertEquals(14, service.getInt(1L, "k", 7));

    when(mapper.selectOne(any())).thenReturn(null);
    assertNull(service.get(1L, "missing"));
  }

  @Test
  void putInsertsWhenMissingUpdatesWhenPresent() {
    when(mapper.selectOne(any())).thenReturn(null);
    service.put(1L, "k", "v1");
    ArgumentCaptor<AssetSettingPO> inserted = ArgumentCaptor.forClass(AssetSettingPO.class);
    verify(mapper).insert(inserted.capture());
    assertEquals("v1", inserted.getValue().getSettingValue());

    AssetSettingPO existing = new AssetSettingPO();
    existing.setSettingValue("old");
    when(mapper.selectOne(any())).thenReturn(existing);
    service.put(1L, "k", "v2");
    verify(mapper).updateById(existing);
    assertEquals("v2", existing.getSettingValue());
  }
}
