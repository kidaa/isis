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

package org.apache.isis.core.runtime.installerregistry.installerapi;

import org.apache.isis.core.commons.components.Installer;
import org.apache.isis.core.commons.config.IsisConfiguration;
import org.apache.isis.core.commons.config.IsisConfigurationBuilderAware;
import org.apache.isis.core.metamodel.facetapi.MetaModelRefiner;
import org.apache.isis.core.metamodel.services.ServicesInjectorSpi;
import org.apache.isis.core.runtime.persistence.ObjectStoreFactory;
import org.apache.isis.core.runtime.persistence.internal.RuntimeContextFromSession;
import org.apache.isis.core.runtime.system.DeploymentType;
import org.apache.isis.core.runtime.system.persistence.PersistenceSession;
import org.apache.isis.core.runtime.system.persistence.PersistenceSessionFactory;

/**
 * Installs a {@link PersistenceSession} during system start up.
 */
public interface PersistenceMechanismInstaller extends Installer, ObjectStoreFactory, IsisConfigurationBuilderAware,
        MetaModelRefiner {

    static String TYPE = "persistor";

    PersistenceSessionFactory createPersistenceSessionFactory(
            final DeploymentType deploymentType,
            final ServicesInjectorSpi servicesInjector,
            final IsisConfiguration configuration, final RuntimeContextFromSession runtimeContext);

}
