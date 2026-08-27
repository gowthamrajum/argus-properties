import type { Routes } from '@angular/router';

export const routes: Routes = [
  // Land on the explanations: the rest of the site assumes their vocabulary.
  { path: '', pathMatch: 'full', redirectTo: 'concepts' },
  {
    path: 'concepts',
    loadComponent: () => import('./pages/concepts-page').then((m) => m.ConceptsPage),
  },
  {
    path: 'shapes',
    loadComponent: () => import('./pages/shapes-page').then((m) => m.ShapesPage),
  },
  {
    path: 'shapes/:id',
    loadComponent: () => import('./pages/shape-detail-page').then((m) => m.ShapeDetailPage),
  },
  {
    path: 'listeners',
    loadComponent: () => import('./pages/listeners-page').then((m) => m.ListenersPage),
  },
  {
    path: 'properties',
    loadComponent: () => import('./pages/properties-page').then((m) => m.PropertiesPage),
  },
  {
    path: 'event-shapes',
    loadComponent: () => import('./pages/event-shapes-page').then((m) => m.EventShapesPage),
  },
  {
    path: 'property-groups',
    loadComponent: () => import('./pages/property-groups-page').then((m) => m.PropertyGroupsPage),
  },
  { path: '**', redirectTo: 'concepts' },
];
