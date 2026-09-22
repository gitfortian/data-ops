import { Input, Popover } from 'antd';
import { Check, Search } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

export interface EmojiIconValue {
  emoji: string;
  background: string;
}

export const DEFAULT_EMOJI_ICON: EmojiIconValue = {
  emoji: '🤖',
  background: '#FFE7D6',
};

interface EmojiEntry {
  emoji: string;
  keywords: string;
}

interface EmojiGroup {
  label: string;
  items: EmojiEntry[];
}

const EMOJI_GROUPS: EmojiGroup[] = [
  {
    label: '常用',
    items: [
      { emoji: '👍', keywords: '赞 like good thumbs up' },
      { emoji: '😀', keywords: '开心 smile happy face' },
      { emoji: '😘', keywords: '亲吻 kiss love' },
      { emoji: '😍', keywords: '喜欢 love heart eyes' },
      { emoji: '😆', keywords: '大笑 laugh happy' },
      { emoji: '😜', keywords: '调皮 wink playful' },
      { emoji: '😅', keywords: '汗 smile sweat' },
      { emoji: '😂', keywords: '笑哭 joy tears' },
      { emoji: '😱', keywords: '惊讶 shock surprise' },
      { emoji: '🤖', keywords: '机器人 robot ai workflow' },
      { emoji: '✨', keywords: '闪光 sparkle magic' },
      { emoji: '🚀', keywords: '火箭 rocket launch' },
      { emoji: '⚡', keywords: '闪电 lightning fast' },
      { emoji: '🔥', keywords: '火 fire hot' },
      { emoji: '✅', keywords: '完成 check done success' },
      { emoji: '🎯', keywords: '目标 target goal' },
    ],
  },
  {
    label: '人物',
    items: [
      { emoji: '🙂', keywords: '微笑 smile person' },
      { emoji: '😎', keywords: '酷 cool sunglasses' },
      { emoji: '🤓', keywords: '程序员 nerd developer' },
      { emoji: '🧐', keywords: '观察 inspect monocle' },
      { emoji: '🤔', keywords: '思考 think' },
      { emoji: '🥳', keywords: '庆祝 party celebrate' },
      { emoji: '🙋', keywords: '举手 hand person' },
      { emoji: '👨‍💻', keywords: '开发 developer programmer code' },
      { emoji: '👩‍💻', keywords: '开发 developer programmer code' },
      { emoji: '🧑‍🔧', keywords: '工具 engineer worker' },
      { emoji: '🧑‍🚀', keywords: '宇航员 astronaut' },
      { emoji: '🕵️', keywords: '检查 inspect detective' },
    ],
  },
  {
    label: '对象',
    items: [
      { emoji: '🔀', keywords: '工作流 workflow shuffle flow' },
      { emoji: '🔁', keywords: '同步 sync repeat' },
      { emoji: '🗄️', keywords: '数据库 database storage' },
      { emoji: '📦', keywords: '包 package box' },
      { emoji: '📊', keywords: '数据 chart analytics' },
      { emoji: '🧩', keywords: '组件 component puzzle' },
      { emoji: '🔗', keywords: '连接 link connect' },
      { emoji: '🛠️', keywords: '工具 tools build' },
      { emoji: '📌', keywords: '固定 pin' },
      { emoji: '🧠', keywords: '智能 ai brain' },
      { emoji: '💡', keywords: '想法 idea light' },
      { emoji: '🛡️', keywords: '安全 security shield' },
      { emoji: '🔍', keywords: '搜索 search inspect' },
      { emoji: '📡', keywords: '实时 realtime signal' },
      { emoji: '⏱️', keywords: '定时 timer schedule' },
      { emoji: '🧪', keywords: '测试 test experiment' },
    ],
  },
];

const ICON_BACKGROUNDS = [
  '#FFE7D6',
  '#FDECC8',
  '#E8F7D9',
  '#DDF4EE',
  '#DCEEFF',
  '#E5E7FF',
  '#F0E3FF',
  '#FBE2EF',
  '#FFF1CC',
  '#EAF6D5',
  '#D9F2F4',
  '#D8EBFF',
  '#E3E1FF',
  '#EEE0F8',
  '#F7DFE6',
  '#FFE1D7',
];

const isEmojiIconValue = (value: unknown): value is EmojiIconValue => {
  if (!value || Array.isArray(value) || typeof value !== 'object') return false;
  const candidate = value as Partial<EmojiIconValue>;
  return Boolean(candidate.emoji?.trim() && candidate.background?.trim());
};

export const normalizeEmojiIconValue = (
  value: unknown,
  fallback: EmojiIconValue = DEFAULT_EMOJI_ICON,
): EmojiIconValue => (isEmojiIconValue(value) ? value : fallback);

interface EmojiIconProps {
  value?: EmojiIconValue;
  size?: number;
  className?: string;
  title?: string;
}

export const EmojiIcon = ({
  value,
  size = 40,
  className,
  title,
}: EmojiIconProps) => {
  const resolved = normalizeEmojiIconValue(value);

  return (
    <span
      aria-label={title || resolved.emoji}
      title={title}
      className={[
        'inline-flex shrink-0 select-none items-center justify-center overflow-hidden',
        className || '',
      ].join(' ')}
      style={{
        width: size,
        height: size,
        borderRadius: Math.max(10, Math.round(size * 0.27)),
        background: resolved.background,
        fontSize: Math.max(18, Math.round(size * 0.5)),
        lineHeight: 1,
      }}
    >
      {resolved.emoji}
    </span>
  );
};

interface EmojiIconPickerProps {
  value?: EmojiIconValue;
  onChange?: (value: EmojiIconValue) => void;
  disabled?: boolean;
  className?: string;
}

const EmojiIconPicker = ({
  value,
  onChange,
  disabled,
  className,
}: EmojiIconPickerProps) => {
  const resolvedValue = normalizeEmojiIconValue(value);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState<EmojiIconValue>(resolvedValue);
  const [keyword, setKeyword] = useState('');

  useEffect(() => {
    if (!open) setDraft(normalizeEmojiIconValue(value));
  }, [open, value]);

  const groups = useMemo(() => {
    const normalized = keyword.trim().toLowerCase();
    if (!normalized) return EMOJI_GROUPS;

    return EMOJI_GROUPS.map((group) => ({
      ...group,
      items: group.items.filter((item) =>
        `${item.emoji} ${item.keywords}`.toLowerCase().includes(normalized),
      ),
    })).filter((group) => group.items.length > 0);
  }, [keyword]);

  const handleOpenChange = (nextOpen: boolean) => {
    if (disabled) return;
    if (nextOpen) {
      setDraft(normalizeEmojiIconValue(value));
      setKeyword('');
    }
    setOpen(nextOpen);
  };

  const content = (
    <div className="w-[360px] overflow-hidden rounded-[14px] bg-white">
      <div className="px-3 pb-2 pt-3">
        <Input
          value={keyword}
          allowClear
          variant="filled"
          prefix={<Search size={14} className="text-[#98a2b3]" />}
          placeholder="搜索表情..."
          onChange={(event) => setKeyword(event.target.value)}
          className="!rounded-[9px]"
        />
      </div>

      <div className="max-h-[250px] overflow-y-auto border-y border-[#f0f1f3] px-3 py-2.5">
        {groups.length ? (
          groups.map((group) => (
            <section key={group.label} className="mb-3 last:mb-0">
              <div className="mb-1.5 text-[10px] font-semibold uppercase tracking-[0.04em] text-[#667085]">
                {group.label}
              </div>
              <div className="grid grid-cols-8 gap-1">
                {group.items.map((item) => {
                  const selected = draft.emoji === item.emoji;
                  return (
                    <button
                      key={`${group.label}-${item.emoji}`}
                      type="button"
                      title={item.keywords.split(' ')[0]}
                      className={[
                        'flex h-9 w-9 items-center justify-center rounded-[8px] border text-[21px] transition-all',
                        selected
                          ? 'border-[#6d7fe8] bg-[#f2f4ff] shadow-[0_0_0_2px_rgba(84,104,223,.09)]'
                          : 'border-transparent bg-transparent hover:bg-[#f5f6f8]',
                      ].join(' ')}
                      onClick={() => setDraft((current) => ({ ...current, emoji: item.emoji }))}
                    >
                      {item.emoji}
                    </button>
                  );
                })}
              </div>
            </section>
          ))
        ) : (
          <div className="flex h-24 items-center justify-center text-[12px] text-[#98a2b3]">
            没有找到匹配的表情
          </div>
        )}
      </div>

      <div className="px-3 py-3">
        <div className="mb-2 text-[10px] font-semibold uppercase tracking-[0.04em] text-[#667085]">
          选择样式
        </div>
        <div className="grid grid-cols-8 gap-1.5">
          {ICON_BACKGROUNDS.map((background) => {
            const selected = draft.background === background;
            return (
              <button
                key={background}
                type="button"
                aria-label={`背景 ${background}`}
                className={[
                  'relative flex h-9 items-center justify-center rounded-[8px] border transition-all',
                  selected
                    ? 'border-[#6d7fe8] shadow-[0_0_0_2px_rgba(84,104,223,.1)]'
                    : 'border-transparent hover:border-[#d9dce3]',
                ].join(' ')}
                style={{ background }}
                onClick={() => setDraft((current) => ({ ...current, background }))}
              >
                <span className="text-[17px] leading-none">{draft.emoji}</span>
                {selected ? (
                  <span className="absolute -right-1 -top-1 flex h-3.5 w-3.5 items-center justify-center rounded-full bg-[#5367dc] text-white shadow-sm">
                    <Check size={9} strokeWidth={2.4} />
                  </span>
                ) : null}
              </button>
            );
          })}
        </div>
      </div>

      <div className="flex gap-2 border-t border-[#f0f1f3] px-3 py-3">
        <button
          type="button"
          className="h-9 flex-1 rounded-[9px] border border-[#e3e5e9] bg-white text-[13px] font-medium text-[#4b5565] transition-colors hover:bg-[#f7f8fa]"
          onClick={() => {
            setDraft(normalizeEmojiIconValue(value));
            setOpen(false);
          }}
        >
          取消
        </button>
        <button
          type="button"
          className="h-9 flex-1 rounded-[9px] border border-[#1f2937] bg-[#1f2937] text-[13px] font-semibold text-white transition-colors hover:bg-[#111827]"
          onClick={() => {
            onChange?.(draft);
            setOpen(false);
          }}
        >
          确认
        </button>
      </div>
    </div>
  );

  return (
    <Popover
      open={open}
      trigger="click"
      placement="bottomLeft"
      arrow={false}
      content={content}
      overlayInnerStyle={{ padding: 0, borderRadius: 14 }}
      onOpenChange={handleOpenChange}
    >
      <button
        type="button"
        disabled={disabled}
        aria-label="选择图标"
        className={[
          'group/icon relative inline-flex shrink-0 rounded-[12px] border border-[#e4e7ec] bg-white p-0 shadow-[0_1px_2px_rgba(16,24,40,.03)] transition-all hover:border-[#cbd2dc] hover:shadow-[0_4px_12px_rgba(16,24,40,.08)] disabled:cursor-not-allowed disabled:opacity-50',
          className || '',
        ].join(' ')}
        onClick={() => !disabled && setOpen(true)}
      >
        <EmojiIcon value={resolvedValue} size={42} />
      </button>
    </Popover>
  );
};

export default EmojiIconPicker;
