/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.query;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.platform.query.api.AbstractPageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PredicateDefinition;
import org.nuxeo.ecm.platform.query.api.PredicateFieldDefinition;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;

/**
 * @since 3.4.2
 */
public class ComplexListPropertyPageProvider extends AbstractPageProvider<Map<String, Object>> {

    private static final long serialVersionUID = 1L;

    /**
     * Cache of the whole map of items
     */
    protected List<Map<String, Object>> items;

    /**
     * Cache of the current items in given page
     */
    protected List<Map<String, Object>> currentItems;

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCurrentPage() {
        if (currentItems == null) {
            currentItems = new ArrayList<>();

            try {
                // resolve parameters to get items if not cached
                if (items == null) {
                    Object[] parameters = getParameters();
                    if (parameters == null) {
                        throw new NuxeoException("First parameter needed to resolve " + "the list of items");
                    }
                    items = (List<Map<String, Object>>) parameters[0];
                }

                List<Map<String, Object>> allItems = new ArrayList<>();

                boolean noFilter = true;
                PageProviderDefinition def = getDefinition();
                WhereClauseDefinition wc = def.getWhereClause();
                if (wc != null) {
                    // filter using simple predicates for now
                    DocumentModel doc = getSearchDocumentModel();
                    if (doc == null) {
                        throw new NuxeoException(String.format("Cannot build query of provider '%s': "
                                + "no search document model is set", getName()));
                    }
                    PredicateDefinition[] predicates = wc.getPredicates();
                    if (predicates != null) {
                        Map<String, Object> filters = new HashMap<>();
                        for (PredicateDefinition pred : predicates) {
                            // handle only exact matches for now
                            String operator = null;
                            String operatorField = pred.getOperatorField();
                            String operatorSchema = pred.getOperatorSchema();
                            String parameter = pred.getParameter();
                            PredicateFieldDefinition[] values = pred.getValues();
                            Object value = NXQLQueryBuilder.getRawValue(doc, values[0]);
                            if (value == null) {
                                // value not provided: ignore predicate
                                continue;
                            }
                            filters.put(parameter, value);
                        }
                        // apply filters on items
                        if (!filters.isEmpty()) {
                            noFilter = false;
                            for (Map<String, Object> item : items) {
                                for (Map.Entry<String, Object> filter : filters.entrySet()) {
                                    if (matches(item, filter.getKey(), filter.getValue())) {
                                        allItems.add(item);
                                    } else {
                                        // perform AND between predicates
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }

                if (noFilter && items != null) {
                    allItems.addAll(items);
                }

                // handle sort
                if (sortInfos != null) {
                    Collections.sort(allItems, new MapComparator(sortInfos));
                }

                // handle max page size and offset
                long minMaxPageSize = getMinMaxPageSize();
                long offset = getCurrentPageOffset();

                long resultsCount = allItems.size();
                setResultsCount(resultsCount);
                int index = 0;
                if (offset < resultsCount) {
                    index = Long.valueOf(offset).intValue();
                }
                int pos = 0;
                for (int i = index; i < allItems.size() && pos < minMaxPageSize; i++) {
                    pos += 1;
                    currentItems.add(allItems.get(i));
                }
            } catch (NuxeoException e) {
                error = e;
                errorMessage = e.getMessage();
                log.warn(e.getMessage(), e);
            }
        }
        return currentItems;
    }

    protected boolean matches(Map<String, Object> item, String key, Object value) {
        if (item.containsKey(key) && value.equals(item.get(key))) {
            return true;
        }
        return false;
    }

    @Override
    protected void pageChanged() {
        currentItems = null;
        super.pageChanged();
    }

    @Override
    public void refresh() {
        items = null;
        currentItems = null;
        super.refresh();
    }

    static final Collator collator = Collator.getInstance();

    static {
        collator.setStrength(Collator.PRIMARY); // case+accent independent
    }

    public class MapComparator implements Comparator<Map<String, Object>> {

        protected List<SortInfo> sortInfos;

        public MapComparator(List<SortInfo> sortInfos) {
            super();
            this.sortInfos = sortInfos;
        }

        @Override
        public int compare(Map<String, Object> arg0, Map<String, Object> arg1) {
            if (sortInfos != null) {
                for (SortInfo sortInfo : sortInfos) {
                    String sortColumn = sortInfo.getSortColumn();
                    boolean sortAsc = sortInfo.getSortAscending();
                    int res = compare(arg0, arg1, sortColumn, sortAsc);
                    if (res == 0) {
                        continue;
                    } else {
                        return res;
                    }
                }
            }
            return 0;
        }

        protected int compare(Map<String, Object> arg0, Map<String, Object> arg1, String sortColumn, boolean asc) {
            Object v1 = arg0.get(sortColumn);
            Object v2 = arg1.get(sortColumn);
            if (v1 == null && v2 == null) {
                return 0;
            } else if (v1 == null) {
                return asc ? -1 : 1;
            } else if (v2 == null) {
                return asc ? 1 : -1;
            }
            final int cmp;
            if (v1 instanceof Long && v2 instanceof Long) {
                cmp = ((Long) v1).compareTo((Long) v2);
            } else if (v1 instanceof Integer && v2 instanceof Integer) {
                cmp = ((Integer) v1).compareTo((Integer) v2);
            } else {
                cmp = collator.compare(v1.toString(), v2.toString());
            }
            return asc ? cmp : -cmp;
        }

    }

}
