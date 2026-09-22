import { history } from '@umijs/max';
import { DigitalScreenTemplateView } from './components/DigitalScreenTemplateView';
import { useDigitalScreenTemplates } from './hooks/useDigitalScreenTemplates';

export default function DigitalScreenTemplatePage() {
  const templatePage = useDigitalScreenTemplates();

  return (
    <DigitalScreenTemplateView
      category={templatePage.category}
      keyword={templatePage.keyword}
      categories={templatePage.categories}
      templates={templatePage.templates}
      previewTemplate={templatePage.previewTemplate}
      selectedTemplate={templatePage.selectedTemplate}
      name={templatePage.name}
      description={templatePage.description}
      isCreating={templatePage.isCreating}
      onBack={() => history.push('/digital-screen')}
      onCategoryChange={templatePage.setCategory}
      onKeywordChange={templatePage.setKeyword}
      onPreviewChange={templatePage.setPreviewTemplate}
      onOpenCreate={templatePage.openCreate}
      onCloseCreate={templatePage.closeCreate}
      onNameChange={templatePage.setName}
      onDescriptionChange={templatePage.setDescription}
      onCreate={() => {
        void templatePage.createScreen().then((screen) => {
          if (screen) history.push(`/digital-screen/${screen.id}/edit`);
        });
      }}
    />
  );
}
