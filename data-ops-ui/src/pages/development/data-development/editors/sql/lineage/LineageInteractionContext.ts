import { createContext, useContext } from 'react';

export interface LineageInteractionState {
  hoveredNodeId?: string;
  hoveredFieldKey?: string;
  relatedNodeIds: ReadonlySet<string>;
  relatedEdgeIds: ReadonlySet<string>;
  relatedMappingIds: ReadonlySet<string>;
}

export const EMPTY_LINEAGE_INTERACTION: LineageInteractionState = {
  relatedNodeIds: new Set(),
  relatedEdgeIds: new Set(),
  relatedMappingIds: new Set(),
};
export const LineageInteractionContext = createContext<LineageInteractionState>(EMPTY_LINEAGE_INTERACTION);
export const useLineageInteraction = () => useContext(LineageInteractionContext);
