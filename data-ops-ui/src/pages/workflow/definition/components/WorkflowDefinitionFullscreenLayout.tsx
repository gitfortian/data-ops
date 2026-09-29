import WorkflowDefinitionEditor from './WorkflowDefinitionEditor';

/**
 * Workflow Definition owns a full-screen editor shell. The layout deliberately
 * follows a professional canvas editor rather than a workflow-product sidebar:
 * one global toolbar, a compact asset rail, canvas workspace and docked inspector.
 */
export default function WorkflowDefinitionFullscreenPage() {
  return (
    <div className="workflow-definition-fullscreen-shell h-screen overflow-hidden bg-[#f5f6f8]">
      <WorkflowDefinitionEditor />
      <style>{`
        .workflow-definition-fullscreen-shell {
          --workflow-inspector-width: 340px;
        }

        .workflow-definition-fullscreen-shell > div:first-child {
          height: 100vh !important;
          min-height: 100vh !important;
          padding-top: 52px;
          box-sizing: border-box;
          background: #f5f6f8 !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar {
          position: fixed !important;
          inset: 0 0 auto 0 !important;
          z-index: 60 !important;
          width: 100vw !important;
          height: 52px !important;
          border-bottom: 1px solid #e8eaee !important;
          background: rgba(255,255,255,.98) !important;
          padding-left: 18px !important;
          padding-right: 14px !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar > div:first-child {
          display: flex !important;
          height: 100% !important;
          align-items: center !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar > div:first-child > div:first-child {
          max-width: 460px !important;
          font-size: 15px !important;
          line-height: 22px !important;
          font-weight: 600 !important;
          color: #101828 !important;
        }

        .workflow-definition-fullscreen-shell .workflow-editor-toolbar > div:first-child > div:nth-child(2) {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div[style],
        .workflow-definition-fullscreen-shell section > div.relative {
          background: #f5f6f8 !important;
        }

        .workflow-definition-fullscreen-shell .react-flow {
          background: #f7f8fa !important;
        }

        .workflow-definition-fullscreen-shell .react-flow__background pattern circle {
          fill: #dfe3e8 !important;
          opacity: .7;
        }

        .workflow-definition-fullscreen-shell .react-flow__minimap {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside {
          top: 0 !important;
          right: 0 !important;
          bottom: 0 !important;
          left: auto !important;
          width: var(--workflow-inspector-width) !important;
          border: 0 !important;
          border-left: 1px solid #e8eaee !important;
          border-radius: 0 !important;
          box-shadow: none !important;
          background: #fff !important;
          color: #344054 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > .react-flow {
          width: calc(100% - var(--workflow-inspector-width)) !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header {
          border-bottom: 1px solid #eef0f2 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header > div:nth-child(2) {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header > div:first-child {
          padding: 14px 16px 8px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header > div:first-child [class*="text-[14px]"] {
          font-size: 15px !important;
          font-weight: 600 !important;
          color: #101828 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header nav {
          height: 38px !important;
          padding-left: 16px !important;
          padding-right: 16px !important;
          gap: 20px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside > header nav button {
          font-size: 13px !important;
          font-weight: 600 !important;
          color: #344054 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside .mx-4.border-t {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside section {
          padding-top: 13px !important;
          padding-bottom: 13px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside .rounded-xl {
          border-radius: 8px !important;
        }

        /* Keep the inspector focused on labels and values instead of explanatory copy. */
        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[rgba(22,24,35,.36)]"],
        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[rgba(22,24,35,.38)]"] {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside section > div[class*="mb-3"][class*="flex"] > div > div[class*="text-[rgba(22,24,35,.42)]"] {
          display: none !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside details div[class*="mt-1"][class*="text-[9px]"][class*="text-[#98a2b3]"] {
          display: none !important;
        }

        /* Raise inspector typography one level and use stronger neutral colors. */
        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[12px]"] {
          font-size: 13px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[11px]"] {
          font-size: 12px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[10px]"] {
          font-size: 11px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[9px]"] {
          font-size: 10px !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[#344054]"] {
          color: #1d2939 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[#667085]"] {
          color: #475467 !important;
        }

        .workflow-definition-fullscreen-shell section > div:has(> aside) > aside [class*="text-[#98a2b3]"] {
          color: #667085 !important;
        }

        /*
         * Canvas controls use one stable anchor. The right inspector only changes
         * the ReactFlow viewport width; opening/closing a node must not shift the
         * top or bottom control groups.
         */
        .workflow-definition-fullscreen-shell [class*="absolute left-3 top-1/2"] {
          top: 12px !important;
          left: calc((100% - var(--workflow-inspector-width)) / 2) !important;
          bottom: auto !important;
          transform: translateX(-50%) !important;
          flex-direction: row !important;
          border-radius: 9px !important;
          box-shadow: 0 2px 10px rgba(22,24,35,.06) !important;
        }

        .workflow-definition-fullscreen-shell [class*="absolute bottom-2 left-1/2"] {
          bottom: 14px !important;
          left: calc((100% - var(--workflow-inspector-width)) / 2) !important;
          border-radius: 9px !important;
          box-shadow: 0 2px 10px rgba(22,24,35,.06) !important;
        }

        .workflow-definition-fullscreen-shell [class*="absolute left-3 top-1/2"] > div[class*="h-px"] {
          width: 1px !important;
          height: 18px !important;
          margin: 0 4px !important;
        }

        @media (max-width: 1180px) {
          .workflow-definition-fullscreen-shell {
            --workflow-inspector-width: 320px;
          }
        }
      `}</style>
    </div>
  );
}
