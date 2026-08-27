import { Component, computed, inject } from '@angular/core';
import { Api } from '../core/api';
import { asyncOnce } from '../core/async-state';
import { PropertyTable } from '../property-table';
import { Breadcrumbs, DocPage, Panel, Section } from '../ui/doc';
import { ErrorState, Spinner } from '../ui/states';

@Component({
  selector: 'app-property-groups-page',
  imports: [Breadcrumbs, DocPage, Panel, Section, PropertyTable, ErrorState, Spinner],
  template: `
    @let result = groups();
    @if (result.error) {
      <app-error-state [message]="result.error" />
    } @else if (result.loading) {
      <app-spinner />
    } @else {
      @let entries = result.data ?? [];
      <app-breadcrumbs [trail]="[{ label: 'Argus Properties', link: '/shapes' }, { label: 'Property groups' }]" />
      <app-doc-page
        heading="Property groups"
        lead="Sets of properties that lots of shapes share. Rather than repeating them on every shape, each shape lists the groups it inherits — so when you look at a shape's properties, the inherited ones are marked with the group they came from."
        [toc]="toc()"
      >
        <app-panel kind="note" heading="Why these are separate">
          <p>
            Almost every shape has an <code class="font-mono">id</code>, a name, and Camunda's async settings.
            Repeating those fifty times would bury the handful of properties that actually make a User Task different
            from a Service Task.
          </p>
        </app-panel>

        @for (group of entries; track group.id) {
          <app-section [anchor]="group.id" [heading]="group.title" [lead]="group.description">
            <p class="text-ink-subtle dark:text-bodydark2 mb-3 text-xs">
              Referenced as <code class="font-mono">{{ group.id }}</code> in a shape's
              <code class="font-mono">inherits</code> list.
            </p>
            <app-property-table [properties]="group.properties" />
          </app-section>
        }
      </app-doc-page>
    }
  `,
})
export class PropertyGroupsPage {
  private readonly api = inject(Api);
  protected readonly groups = asyncOnce(() => this.api.propertyGroups());

  protected readonly toc = computed(() =>
    (this.groups().data ?? []).map((group) => ({ id: group.id, label: group.title })),
  );
}
