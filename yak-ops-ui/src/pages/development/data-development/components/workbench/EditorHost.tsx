import type { DevelopmentEditorDefinition } from '../../editors/types';
import type { DevelopmentDirectory, DevelopmentNode } from '../../types';

interface EditorHostProps {
  node: DevelopmentNode;
  directory?: DevelopmentDirectory;
  definition: DevelopmentEditorDefinition;
  onRunContent?: (content: string) => void;
  running?: boolean;
}

const EditorHost = ({
  node,
  directory,
  definition,
  onRunContent,
  running,
}: EditorHostProps) => {
  const Editor = definition.Editor;

  return (
    <section className="min-w-0 flex-1 overflow-hidden bg-white">
      <Editor
        key={String(node.id)}
        node={node}
        directory={directory}
        onRunContent={onRunContent}
        running={running}
      />
    </section>
  );
};

export default EditorHost;
