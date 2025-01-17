/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.metamodel.facets;

import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.mgr.AdapterManager;
import org.apache.isis.core.metamodel.facets.collections.modify.CollectionFacet;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;

public final class CollectionUtils {
    private CollectionUtils() {
    }

    public static Object[] getCollectionAsObjectArray(final Object option, final ObjectSpecification spec, final AdapterManager adapterMap) {
        final ObjectAdapter collection = adapterMap.adapterFor(option);
        final CollectionFacet facet = CollectionFacet.Utils.getCollectionFacetFromSpec(collection);
        final Object[] optionArray = new Object[facet.size(collection)];
        int j = 0;
        for (final ObjectAdapter nextElement : facet.iterable(collection)) {
            optionArray[j++] = nextElement != null? nextElement.getObject(): null;
        }
        return optionArray;
    }

}
