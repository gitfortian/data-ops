import React from 'react';

export interface YakOpsEmptyProps
  extends Omit<
    React.ImgHTMLAttributes<HTMLImageElement>,
    'src' | 'alt' | 'width' | 'height'
  > {
  /** 插画宽度 */
  width?: number | string;
  /** 插画高度 */
  height?: number | string;
  /** @deprecated PNG 插画不再支持动态强调色，仅保留用于兼容现有调用方。 */
  primaryColor?: string;
  /** 空状态文案，同时作为图片无障碍标题。 */
  title?: string;
  /** 空状态补充说明，仅用于无障碍描述。 */
  description?: string;
  /** 是否展示插画下方的空状态文案，默认在传入 description 时开启。 */
  showCaption?: boolean;
}

/**
 * Yak Ops 空状态插画。
 *
 * 统一使用 public/empty.png，并按需在插画下方展示一句空状态文案。
 */
const YakOpsEmpty: React.FC<YakOpsEmptyProps> = ({
  width = 220,
  height = 150,
  primaryColor: _primaryColor,
  title = 'Yak Ops 暂无数据',
  description,
  showCaption,
  style,
  ...props
}) => {
  const alt = description ? `${title}，${description}` : title;
  const shouldShowCaption = showCaption ?? description !== undefined;

  return (
    <div className="inline-flex flex-col items-center justify-center text-center">
      <img
        src="/empty.png"
        width={width}
        height={height}
        alt={alt}
        draggable={false}
        style={{ objectFit: 'contain', ...style }}
        {...props}
      />

      {shouldShowCaption ? (
        <div className="mt-2 max-w-[320px] px-3 text-[13px] leading-5 text-[#667085]">
          {title}
        </div>
      ) : null}
    </div>
  );
};

export default YakOpsEmpty;
