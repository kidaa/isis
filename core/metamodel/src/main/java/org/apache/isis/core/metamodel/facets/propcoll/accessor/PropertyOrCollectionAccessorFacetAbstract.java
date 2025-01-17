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

package org.apache.isis.core.metamodel.facets.propcoll.accessor;

import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.mgr.AdapterManager;
import org.apache.isis.core.metamodel.deployment.DeploymentCategory;
import org.apache.isis.core.metamodel.facetapi.Facet;
import org.apache.isis.core.metamodel.facetapi.FacetAbstract;
import org.apache.isis.core.metamodel.facetapi.FacetHolder;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.SpecificationLoader;

public abstract class PropertyOrCollectionAccessorFacetAbstract extends FacetAbstract implements PropertyOrCollectionAccessorFacet {

    private final AdapterManager adapterManager;
    private final SpecificationLoader specificationLoader;

    public static Class<? extends Facet> type() {
        return PropertyOrCollectionAccessorFacet.class;
    }

    public PropertyOrCollectionAccessorFacetAbstract(
            final FacetHolder holder,
            final AdapterManager adapterManager,
            final SpecificationLoader specificationLoader) {
        super(type(), holder, Derivation.NOT_DERIVED);
        this.adapterManager = adapterManager;
        this.specificationLoader = specificationLoader;
    }

    @Override
    public abstract Object getProperty(
            ObjectAdapter inObject,
            final AuthenticationSession authenticationSession,
            final DeploymentCategory deploymentCategory);

    protected ObjectSpecification getSpecification(final Class<?> type) {
        return type != null ? getSpecificationLoader().loadSpecification(type) : null;
    }

    // //////////////////////////////////////

    protected AdapterManager getAdapterManager() {
        return adapterManager;
    }

    protected SpecificationLoader getSpecificationLoader() {
        return specificationLoader;
    }

}
