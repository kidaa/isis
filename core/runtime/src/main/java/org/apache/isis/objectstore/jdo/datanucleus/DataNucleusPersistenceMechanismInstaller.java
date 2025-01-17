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
package org.apache.isis.objectstore.jdo.datanucleus;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.datanucleus.PropertyNames;
import org.datanucleus.api.jdo.JDOPersistenceManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.isis.core.commons.components.Installer;
import org.apache.isis.core.commons.config.InstallerAbstract;
import org.apache.isis.core.commons.config.IsisConfiguration;
import org.apache.isis.core.metamodel.progmodel.ProgrammingModel;
import org.apache.isis.core.metamodel.services.ServicesInjectorSpi;
import org.apache.isis.core.metamodel.spec.SpecificationLoaderSpi;
import org.apache.isis.core.metamodel.specloader.validator.MetaModelValidatorComposite;
import org.apache.isis.core.runtime.installerregistry.installerapi.PersistenceMechanismInstaller;
import org.apache.isis.core.runtime.persistence.internal.RuntimeContextFromSession;
import org.apache.isis.core.runtime.system.DeploymentType;
import org.apache.isis.core.runtime.system.context.IsisContext;
import org.apache.isis.core.runtime.system.persistence.ObjectStore;
import org.apache.isis.core.runtime.system.persistence.PersistenceSessionFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.auditable.AuditableAnnotationInJdoApplibFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.auditable.AuditableMarkerInterfaceInJdoApplibFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.datastoreidentity.JdoDatastoreIdentityAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.discriminator.JdoDiscriminatorAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.embeddedonly.JdoEmbeddedOnlyAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.persistencecapable.JdoPersistenceCapableAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.query.JdoQueryAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.object.version.JdoVersionAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.prop.column.BigDecimalDerivedFromJdoColumnAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.prop.column.MandatoryFromJdoColumnAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.prop.column.MaxLengthDerivedFromJdoColumnAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.prop.notpersistent.JdoNotPersistentAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.facets.prop.primarykey.JdoPrimaryKeyAnnotationFacetFactory;
import org.apache.isis.objectstore.jdo.metamodel.specloader.validator.JdoMetaModelValidator;
import org.apache.isis.objectstore.jdo.service.RegisterEntities;

/**
 * Configuration files are read in the usual fashion (as per {@link Installer#getConfigurationResources()}, ie will consult all of:
 * <ul>
 * <li><tt>persistor_datanucleus.properties</tt>
 * <li><tt>persistor.properties</tt>
 * <li><tt>isis.properties</tt>
 * </ul>
 * 
 * <p>
 * With respect to configuration, all properties under {@value #DATANUCLEUS_CONFIG_PREFIX} prefix are passed
 * through verbatim to the DataNucleus runtime. For example:
 * <table>
 * <tr><th>Isis Property</th><th>DataNucleus Property</th></tr>
 * <tr><td><tt>isis.persistor.datanucleus.impl.datanucleus.foo.Bar</tt></td><td><tt>datanucleus.foo.Bar</tt></td></tr>
 * </table>
 *
 */
public class DataNucleusPersistenceMechanismInstaller extends InstallerAbstract implements PersistenceMechanismInstaller {

    private static final Logger LOG = LoggerFactory.getLogger(DataNucleusPersistenceMechanismInstaller.class);

    public static final String NAME = "datanucleus";

    public static final String CLASS_METADATA_LOADED_LISTENER_KEY = "classMetadataLoadedListener";
    static final String CLASS_METADATA_LOADED_LISTENER_DEFAULT = CreateSchemaObjectFromClassMetadata.class.getName();

    private static final String JDO_OBJECTSTORE_CONFIG_PREFIX = "isis.persistor.datanucleus";  // specific to the JDO objectstore
    private static final String DATANUCLEUS_CONFIG_PREFIX = "isis.persistor.datanucleus.impl"; // reserved for datanucleus' own config props

    public DataNucleusPersistenceMechanismInstaller() {
        super(PersistenceMechanismInstaller.TYPE, NAME);
    }

    //region > createPersistenceSessionFactory
    @Override
    public PersistenceSessionFactory createPersistenceSessionFactory(
            final DeploymentType deploymentType,
            final ServicesInjectorSpi servicesInjector,
            final IsisConfiguration configuration,
            final RuntimeContextFromSession runtimeContext) {

        DataNucleusPersistenceMechanismInstaller objectStoreFactory = this;
        return new PersistenceSessionFactory(deploymentType, servicesInjector, configuration, objectStoreFactory, runtimeContext);
    }
    //endregion


    //region > createObjectStore

    @Override
    public ObjectStore createObjectStore(final IsisConfiguration configuration) {
        final DataNucleusApplicationComponents applicationComponents = createDataNucleusApplicationComponentsIfRequired(configuration);
        return new DataNucleusObjectStore(applicationComponents);
    }

    //endregion

    //region > createDataNucleusApplicationComponentsIfRequired
    
    private DataNucleusApplicationComponents applicationComponents = null;

    private DataNucleusApplicationComponents createDataNucleusApplicationComponentsIfRequired(
            final IsisConfiguration configuration) {

        if (applicationComponents == null || applicationComponents.isStale()) {

            final IsisConfiguration jdoObjectstoreConfig = configuration.createSubset(JDO_OBJECTSTORE_CONFIG_PREFIX);

            final IsisConfiguration dataNucleusConfig = configuration.createSubset(DATANUCLEUS_CONFIG_PREFIX);
            final Map<String, String> datanucleusProps = dataNucleusConfig.asMap();
            addDataNucleusPropertiesIfRequired(datanucleusProps);

            final RegisterEntities registerEntities = new RegisterEntities(configuration.asMap());
            final Set<String> classesToBePersisted = registerEntities.getEntityTypes();

            applicationComponents = new DataNucleusApplicationComponents(jdoObjectstoreConfig, datanucleusProps, classesToBePersisted);
        }

        return applicationComponents;
    }

    private static void addDataNucleusPropertiesIfRequired(
            final Map<String, String> props) {
        
        // new feature in DN 3.2.3; enables dependency injection into entities
        putIfNotPresent(props, PropertyNames.PROPERTY_OBJECT_PROVIDER_CLASS_NAME, JDOStateManagerForIsis.class.getName());
        
        putIfNotPresent(props, "javax.jdo.PersistenceManagerFactoryClass", JDOPersistenceManagerFactory.class.getName());

        // previously we defaulted this property to "true", but that could cause the target database to be modified
        putIfNotPresent(props, PropertyNames.PROPERTY_SCHEMA_AUTOCREATE_SCHEMA, Boolean.FALSE.toString());

        putIfNotPresent(props, PropertyNames.PROPERTY_SCHEMA_VALIDATE_ALL, Boolean.TRUE.toString());
        putIfNotPresent(props, PropertyNames.PROPERTY_CACHE_L2_TYPE, "none");

        putIfNotPresent(props, PropertyNames.PROPERTY_PERSISTENCE_UNIT_LOAD_CLASSES, Boolean.TRUE.toString());

        String connectionFactoryName = props.get(PropertyNames.PROPERTY_CONNECTION_FACTORY_NAME);
        if(connectionFactoryName != null) {
            String connectionFactory2Name = props.get(PropertyNames.PROPERTY_CONNECTION_FACTORY2_NAME);
            String transactionType = props.get("javax.jdo.option.TransactionType");
            if(transactionType == null) {
                LOG.info("found non-JTA JNDI datasource (" + connectionFactoryName + ")");
                if(connectionFactory2Name != null) {
                    LOG.warn("found non-JTA JNDI datasource (" + connectionFactoryName + "); second '-nontx' JNDI datasource configured but will not be used (" + connectionFactory2Name +")");
                }
            } else
                LOG.info("found JTA JNDI datasource (" + connectionFactoryName + ")");
                if(connectionFactory2Name == null) {
                    // JDO/DN itself will (probably) throw an exception 
                    LOG.error("found JTA JNDI datasource (" + connectionFactoryName + ") but second '-nontx' JNDI datasource *not* found");
                } else {
                    LOG.info("... and second '-nontx' JNDI datasource found; " + connectionFactory2Name);
                }
            // nothing further to do
            return;
        } else {
            // use JDBC connection properties; put if not present
            LOG.info("did *not* find JNDI datasource; will use JDBC");
            
            putIfNotPresent(props, "javax.jdo.option.ConnectionDriverName", "org.hsqldb.jdbcDriver");
            putIfNotPresent(props, "javax.jdo.option.ConnectionURL", "jdbc:hsqldb:mem:test");
            putIfNotPresent(props, "javax.jdo.option.ConnectionUserName", "sa");        
            putIfNotPresent(props, "javax.jdo.option.ConnectionPassword", "");
        }
    }

    private static void putIfNotPresent(
            final Map<String, String> props,
            String key,
            String value) {
        if(!props.containsKey(key)) {
            props.put(key, value);
        }
    }
    //endregion

    //region > PersistenceSessionFactoryDelegate impl


    @Override
    public void refineProgrammingModel(ProgrammingModel programmingModel, IsisConfiguration configuration) {
        programmingModel.addFactory(
                JdoPersistenceCapableAnnotationFacetFactory.class, ProgrammingModel.Position.BEGINNING);
        programmingModel.addFactory(JdoDatastoreIdentityAnnotationFacetFactory.class);
        programmingModel.addFactory(JdoEmbeddedOnlyAnnotationFacetFactory.class);
        
        programmingModel.addFactory(JdoPrimaryKeyAnnotationFacetFactory.class);
        programmingModel.addFactory(JdoNotPersistentAnnotationFacetFactory.class);
        programmingModel.addFactory(JdoDiscriminatorAnnotationFacetFactory.class);
        programmingModel.addFactory(JdoVersionAnnotationFacetFactory.class);
        
        programmingModel.addFactory(JdoQueryAnnotationFacetFactory.class);
        
        programmingModel.addFactory(BigDecimalDerivedFromJdoColumnAnnotationFacetFactory.class);
        programmingModel.addFactory(MaxLengthDerivedFromJdoColumnAnnotationFacetFactory.class);
        // must appear after JdoPrimaryKeyAnnotationFacetFactory (above)
        // and also MandatoryFacetOnPropertyMandatoryAnnotationFactory
        // and also PropertyAnnotationFactory
        programmingModel.addFactory(MandatoryFromJdoColumnAnnotationFacetFactory.class);
        
        programmingModel.addFactory(AuditableAnnotationInJdoApplibFacetFactory.class);
        programmingModel.addFactory(AuditableMarkerInterfaceInJdoApplibFacetFactory.class);
    }

    @Override
    public void refineMetaModelValidator(MetaModelValidatorComposite metaModelValidator, IsisConfiguration configuration) {
        metaModelValidator.add(new JdoMetaModelValidator());
    }

    //endregion

    //region > dependencies

    protected SpecificationLoaderSpi getSpecificationLoader() {
        return IsisContext.getSpecificationLoader();
    }

    //endregion

    @Override
    public List<Class<?>> getTypes() {
        return listOf(PersistenceSessionFactory.class);
    }

}
