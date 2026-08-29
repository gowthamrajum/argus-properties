import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import type { Observable } from 'rxjs';
import type {
  Behaviour,
  CategoryEntry,
  Concept,
  EventShape,
  ListenerType,
  Rule,
  RuleQuery,
  RuleRequest,
  PropertiesResponse,
  PropertyGroup,
  PropertyUsage,
  Shape,
  ShapesResponse,
} from './models';

export interface ShapeQuery {
  category?: string;
  q?: string;
  executable?: boolean;
}

/** Every call the app makes, in one place. Paths mirror the service's resource tree. */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1';

  shapes(query: ShapeQuery = {}): Observable<ShapesResponse> {
    let params = new HttpParams();
    if (query.category) params = params.set('category', query.category);
    if (query.q) params = params.set('q', query.q);
    if (query.executable !== undefined) params = params.set('executable', query.executable);
    return this.http.get<ShapesResponse>(`${this.base}/shapes`, { params });
  }

  shape(id: string): Observable<Shape> {
    return this.http.get<Shape>(`${this.base}/shapes/${id}`);
  }

  properties(id: string, options: { own?: boolean; namespace?: string } = {}): Observable<PropertiesResponse> {
    let params = new HttpParams();
    if (options.own) params = params.set('own', true);
    if (options.namespace) params = params.set('namespace', options.namespace);
    return this.http.get<PropertiesResponse>(`${this.base}/shapes/${id}/properties`, { params });
  }

  /** What the shape does at runtime: how it executes, how it can fail, how it recovers. */
  behaviour(id: string): Observable<Behaviour> {
    return this.http.get<Behaviour>(`${this.base}/shapes/${id}/behaviour`);
  }

  /** Every concrete event shape - position crossed with definition, the ~49 a palette offers. */
  eventShapes(): Observable<EventShape[]> {
    return this.http.get<EventShape[]>(`${this.base}/event-shapes`);
  }

  /** Every distinct property, and how many shapes carry it. */
  propertyIndex(filters: { kind?: string; namespace?: string; q?: string } = {}): Observable<PropertyUsage[]> {
    let params = new HttpParams();
    if (filters.kind) params = params.set('kind', filters.kind);
    if (filters.namespace) params = params.set('namespace', filters.namespace);
    if (filters.q) params = params.set('q', filters.q);
    return this.http.get<PropertyUsage[]>(`${this.base}/properties`, { params });
  }

  /** One property, with every shape it applies to. */
  propertyUsage(name: string): Observable<PropertyUsage> {
    return this.http.get<PropertyUsage>(`${this.base}/properties/${encodeURIComponent(name)}`);
  }

  /** The listener families and every event each one offers. */
  listeners(): Observable<ListenerType[]> {
    return this.http.get<ListenerType[]>(`${this.base}/listeners`);
  }

  // ------------------------------------------------------------------ rules (CRUD)

  rules(query: RuleQuery = {}): Observable<Rule[]> {
    let params = new HttpParams();
    if (query.shape) params = params.set('shape', query.shape);
    if (query.kind) params = params.set('kind', query.kind);
    if (query.severity) params = params.set('severity', query.severity);
    if (query.enabled !== undefined) params = params.set('enabled', query.enabled);
    return this.http.get<Rule[]>(`${this.base}/rules`, { params });
  }

  rulesForShape(shapeId: string): Observable<Rule[]> {
    return this.http.get<Rule[]>(`${this.base}/shapes/${shapeId}/rules`);
  }

  createRule(request: RuleRequest): Observable<Rule> {
    return this.http.post<Rule>(`${this.base}/rules`, request);
  }

  updateRule(id: number, request: RuleRequest): Observable<Rule> {
    return this.http.put<Rule>(`${this.base}/rules/${id}`, request);
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/rules/${id}`);
  }

  categories(): Observable<CategoryEntry[]> {
    return this.http.get<CategoryEntry[]>(`${this.base}/categories`);
  }

  concepts(): Observable<Concept[]> {
    return this.http.get<Concept[]>(`${this.base}/concepts`);
  }

  propertyGroups(): Observable<PropertyGroup[]> {
    return this.http.get<PropertyGroup[]>(`${this.base}/property-groups`);
  }
}
