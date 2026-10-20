import { logicalSample } from '../../../../tests/fixtures/modelingLogical';
import { logicalDesignReminders, logicalDesignView } from './logicalDesign';

it('focuses only direct design neighbors without turning indirect relations into dependencies', () => {
  const detail = logicalSample();
  const view = logicalDesignView(detail, 11);
  expect(view.entities.map(item => item.entity.id)).toEqual([11, 12]);
  expect(view.relations.map(item => item.id)).toEqual([51]);
  expect(logicalDesignView(detail).entities).toHaveLength(3);
  expect(detail.relations).toHaveLength(2);
});

it('keeps unresolved endpoints explicit without inventing a graph node', () => {
  const detail = logicalSample();
  detail.relations.push({ id: 53, sourceEntityId: 11, targetEntityId: 999,
    relationType: 'ASSOCIATION', cardinality: 'ONE_TO_ONE' });
  const view = logicalDesignView(detail);
  expect(view.entities).toHaveLength(3);
  expect(view.relations).toHaveLength(2);
  expect(view.unresolved.map(item => item.id)).toEqual([53]);
});

it('locates missing declarations without treating a stored standard ID as validated', () => {
  const detail = logicalSample();
  const reminders = logicalDesignReminders(detail);
  expect(reminders.missingStandards).toEqual([{ entityId: 11, attributeId: 102 }]);
  expect(reminders.missingPrimary.map(item => item.entity.id)).toEqual([12, 13]);
  expect(reminders.pendingRelations.map(item => item.id)).toEqual([51]);
});

it('keeps large string endpoint identities exact when focusing an entity', () => {
  const detail = logicalSample();
  // Exercise a string Java Long returned at the API boundary, beyond safe JS integers.
  const entityId = '2109169776643895297' as unknown as number;
  detail.entities[0].entity.id = entityId;
  detail.relations[0].sourceEntityId = entityId;
  const view = logicalDesignView(detail, '2109169776643895297');
  expect(view.entities.map(item => item.entity.id)).toEqual(['2109169776643895297', 12]);
  expect(view.relations.map(item => item.id)).toEqual([51]);
  expect(view.unresolved).toEqual([]);
});
