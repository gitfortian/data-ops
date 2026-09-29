package io.yak.ops.business.asset.application.reader;

/**
 * Extension reader for asset governance views.
 *
 * Domain facts stay in their owning modules and asset only consumes
 * extension information for aggregated views.
 */
public interface AssetExtensionReader<T> {

    String type();

    T read(String assetKey);
}
