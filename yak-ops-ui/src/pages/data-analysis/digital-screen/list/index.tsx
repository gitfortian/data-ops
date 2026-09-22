import { history } from '@umijs/max';
import { DigitalScreenListView } from './components/DigitalScreenListView';
import { useDigitalScreenList } from './hooks/useDigitalScreenList';

export default function DigitalScreenListPage() {
  const {
    screens,
    filteredScreens,
    statusItems,
    status,
    keyword,
    isLoading,
    setStatus,
    setKeyword,
    resetFilters,
    duplicateScreen,
    removeScreen,
  } = useDigitalScreenList();

  return (
    <DigitalScreenListView
      screens={screens}
      filteredScreens={filteredScreens}
      statusItems={statusItems}
      status={status}
      keyword={keyword}
      isLoading={isLoading}
      onStatusChange={setStatus}
      onKeywordChange={setKeyword}
      onResetFilters={resetFilters}
      onCreate={() => history.push('/digital-screen/new')}
      onEdit={(screen) => history.push(`/digital-screen/${screen.id}/edit`)}
      onPreview={(screen) => history.push(`/digital-screen/${screen.id}`)}
      onDuplicate={(screen) => {
        void duplicateScreen(screen).then((duplicated) => {
          if (duplicated) history.push(`/digital-screen/${duplicated.id}/edit`);
        });
      }}
      onDelete={(screen) => void removeScreen(screen)}
    />
  );
}
