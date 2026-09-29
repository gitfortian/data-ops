import {
  Database,
  File,
  FileCode2,
  FileJson,
  FileText,
  Folder,
  Package,
  Terminal,
} from 'lucide-react';
import type { CSSProperties, FC } from 'react';

type LucideIconType = FC<{ size?: number; style?: CSSProperties; className?: string }>;

interface FileIconConfig {
  icon: LucideIconType;
  color: string;
}

const DEFAULT_FILE_ICON: FileIconConfig = { icon: File, color: '#667085' };

const SUFFIX_ICON_MAP: Record<string, FileIconConfig> = {
  // 编程语言
  py:     { icon: FileCode2, color: '#3776ab' },
  java:   { icon: FileCode2, color: '#e76f00' },
  js:     { icon: FileCode2, color: '#f7df1e' },
  ts:     { icon: FileCode2, color: '#3178c6' },
  tsx:    { icon: FileCode2, color: '#3178c6' },
  // 构建产物
  jar:    { icon: Package,   color: '#7c3aed' },
  // 数据与配置
  sql:    { icon: Database,  color: '#00758f' },
  json:   { icon: FileJson,  color: '#d97706' },
  yaml:   { icon: FileCode2, color: '#cb171e' },
  yml:    { icon: FileCode2, color: '#cb171e' },
  xml:    { icon: FileCode2, color: '#e37933' },
  properties: { icon: FileText, color: '#667085' },
  conf:   { icon: FileText,  color: '#667085' },
  hocon:  { icon: FileText,  color: '#667085' },
  // 脚本
  sh:     { icon: Terminal,  color: '#4eaa25' },
  bash:   { icon: Terminal,  color: '#4eaa25' },
  ps1:    { icon: Terminal,  color: '#5391fe' },
  psm1:   { icon: Terminal,  color: '#5391fe' },
  // 文档与文本
  md:     { icon: FileText,  color: '#083fa1' },
  txt:    { icon: FileText,  color: '#667085' },
  log:    { icon: FileText,  color: '#98a2b3' },
  csv:    { icon: FileText,  color: '#217346' },
};

const lookupIcon = (suffix?: string): FileIconConfig => {
  if (!suffix) return DEFAULT_FILE_ICON;
  return SUFFIX_ICON_MAP[suffix.toLowerCase()] ?? DEFAULT_FILE_ICON;
};

interface FileSuffixIconProps {
  suffix?: string;
  size?: number;
  className?: string;
}

/** 根据文件后缀渲染带颜色的文件类型图标。 */
export const FileSuffixIcon: FC<FileSuffixIconProps> = ({
  suffix,
  size = 19,
  className,
}) => {
  const cfg = lookupIcon(suffix);
  const Icon = cfg.icon;
  return <Icon size={size} style={{ color: cfg.color }} className={className} />;
};

interface DirectoryIconProps {
  size?: number;
  className?: string;
}

/** 文件夹图标。 */
export const DirectoryIcon: FC<DirectoryIconProps> = ({ size = 19, className }) => (
  <Folder size={size} className={className} />
);
