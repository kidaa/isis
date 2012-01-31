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

package org.apache.isis.runtimes.dflt.objectstores.nosql;

import java.util.List;
import java.util.Map;

import org.apache.isis.core.commons.exceptions.UnexpectedCallException;
import org.apache.isis.core.metamodel.adapter.ObjectAdapter;
import org.apache.isis.core.metamodel.adapter.ResolveState;
import org.apache.isis.core.metamodel.adapter.oid.AggregatedOid;
import org.apache.isis.core.metamodel.adapter.oid.Oid;
import org.apache.isis.core.metamodel.adapter.version.Version;
import org.apache.isis.core.metamodel.facets.collections.modify.CollectionFacet;
import org.apache.isis.core.metamodel.facets.object.encodeable.EncodableFacet;
import org.apache.isis.core.metamodel.spec.ObjectSpecification;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociation;
import org.apache.isis.core.metamodel.spec.feature.ObjectAssociationContainer;
import org.apache.isis.core.metamodel.spec.feature.OneToManyAssociation;
import org.apache.isis.core.metamodel.spec.feature.OneToOneAssociation;
import org.apache.isis.runtimes.dflt.runtime.system.context.IsisContext;
import org.apache.isis.runtimes.dflt.runtime.system.persistence.AdapterManager;

class ObjectReader {

    public ObjectAdapter load(final StateReader reader, final KeyCreator keyCreator, final VersionCreator versionCreator, final Map<String, DataEncryption> dataEncrypters) {
        final String className = reader.readObjectType();
        final ObjectSpecification specification = IsisContext.getSpecificationLoader().loadSpecification(className);
        final String id = reader.readId();
        final Oid oid = keyCreator.oid(id);

        final ObjectAdapter object = getAdapter(specification, oid);
        if (object.getResolveState().isResolved()) {
            Version version = null;
            final String versionString = reader.readVersion();
            if (!versionString.equals("")) {
                final String user = reader.readUser();
                final String time = reader.readTime();
                version = versionCreator.version(versionString, user, time);
            }
            if (version.different(object.getVersion())) {
                // TODO - do we need to CHECK version and update
                throw new UnexpectedCallException();
            } else {
                return object;
            }
        }

        // TODO move lock to common method
        // object.setOptimisticLock(version);
        loadState(reader, keyCreator, versionCreator, dataEncrypters, object);
        return object;
    }

    public void update(final StateReader reader, final KeyCreator keyCreator, final VersionCreator versionCreator, final Map<String, DataEncryption> dataEncrypters, final ObjectAdapter object) {
        loadState(reader, keyCreator, versionCreator, dataEncrypters, object);
    }

    private void loadState(final StateReader reader, final KeyCreator keyCreator, final VersionCreator versionCreator, final Map<String, DataEncryption> dataEncrypters, final ObjectAdapter object) {
        final ResolveState resolveState = ResolveState.RESOLVING;
        object.changeState(resolveState);
        Version version = null;
        final String versionString = reader.readVersion();
        if (!versionString.equals("")) {
            final String user = reader.readUser();
            final String time = reader.readTime();
            version = versionCreator.version(versionString, user, time);
        }
        final String encryptionType = reader.readEncrytionType();
        readFields(reader, object, keyCreator, dataEncrypters.get(encryptionType));
        object.setOptimisticLock(version);
        object.changeState(resolveState.getEndState());
    }

    private void readFields(final StateReader reader, final ObjectAdapter object, final KeyCreator keyCreator, final DataEncryption dataEncrypter) {
        final ObjectAssociationContainer specification = object.getSpecification();
        final List<ObjectAssociation> associations = specification.getAssociations();
        for (final ObjectAssociation association : associations) {
            if (association.isNotPersisted()) {
                continue;
            }
            if (association.isOneToManyAssociation()) {
                readCollection(reader, keyCreator, dataEncrypter, (OneToManyAssociation) association, object);
            } else if (association.getSpecification().isValue()) {
                readValue(reader, dataEncrypter, (OneToOneAssociation) association, object);
            } else if (association.getSpecification().isAggregated()) {
                readAggregate(reader, keyCreator, dataEncrypter, (OneToOneAssociation) association, object);
            } else {
                readReference(reader, keyCreator, (OneToOneAssociation) association, object);
            }
        }
    }

    private void readAggregate(final StateReader reader, final KeyCreator keyCreator, final DataEncryption dataEncrypter, final OneToOneAssociation association, final ObjectAdapter object) {
        final String id = association.getId();
        final StateReader aggregateReader = reader.readAggregate(id);
        if (aggregateReader != null) {
            final String id2 = aggregateReader.readId();
            final AggregatedOid oid = new AggregatedOid(object.getOid(), id2);
            final ObjectAdapter fieldObject = restoreAggregatedObject(aggregateReader, oid, keyCreator, dataEncrypter);
            association.initAssociation(object, fieldObject);
        } else {
            association.initAssociation(object, null);
        }
    }

    private ObjectAdapter restoreAggregatedObject(final StateReader aggregateReader, final Oid oid, final KeyCreator keyCreator, final DataEncryption dataEncrypter) {
        final String objectType = aggregateReader.readObjectType();
        final ObjectSpecification specification = IsisContext.getSpecificationLoader().loadSpecification(objectType);
        final ObjectAdapter fieldObject = getAdapter(specification, oid);
        if (fieldObject.getResolveState().isGhost()) {
            final ResolveState resolveState = ResolveState.RESOLVING;
            fieldObject.changeState(resolveState);
            readFields(aggregateReader, fieldObject, keyCreator, dataEncrypter);
            fieldObject.changeState(resolveState.getEndState());
        }
        return fieldObject;
    }

    private void readValue(final StateReader reader, final DataEncryption dataEncrypter, final OneToOneAssociation association, final ObjectAdapter object) {
        final String fieldData = reader.readField(association.getId());
        if (fieldData != null) {
            if (fieldData.equals("null")) {
                association.initAssociation(object, null);
            } else {
                final EncodableFacet encodeableFacet = association.getSpecification().getFacet(EncodableFacet.class);
                final String decryptedData = dataEncrypter.decrypt(fieldData);
                final ObjectAdapter value = encodeableFacet.fromEncodedString(decryptedData);
                association.initAssociation(object, value);
            }
        }
    }

    private void readReference(final StateReader reader, final KeyCreator keyCreator, final OneToOneAssociation association, final ObjectAdapter object) {
        ObjectAdapter fieldObject;
        final String ref = reader.readField(association.getId());
        if (ref == null || ref.equals("null")) {
            fieldObject = null;
        } else {
            if (ref.equals("")) {
                throw new NoSqlStoreException("Invalid reference field (an empty string) in data for " + association.getName() + "  in " + object);
            }
            final Oid oid = keyCreator.oidFromReference(ref);
            final ObjectSpecification specification = keyCreator.specificationFromReference(ref);
            fieldObject = getAdapter(specification, oid);
        }
        try {
            association.initAssociation(object, fieldObject);
        } catch (IllegalArgumentException e) {
            throw new NoSqlStoreException("Failed to process field data for " + association.getName() + "  in " + object + ": " + ref);
        }
    }

    private void readCollection(final StateReader reader, final KeyCreator keyCreator, final DataEncryption dataEncrypter, final OneToManyAssociation association, final ObjectAdapter object) {
        final ObjectAdapter collection = association.get(object);
        final CollectionFacet facet = collection.getSpecification().getFacet(CollectionFacet.class);
        if (association.getSpecification().isAggregated()) {
            final List<StateReader> readers = reader.readCollection(association.getId());
            // String id = association.getId();
            final ObjectAdapter[] elements = new ObjectAdapter[readers.size()];
            int i = 0;
            for (final StateReader elementReader : readers) {
                final String id = elementReader.readId();
                final AggregatedOid oid = new AggregatedOid(object.getOid(), id);
                elements[i++] = restoreAggregatedObject(elementReader, oid, keyCreator, dataEncrypter);
            }
            facet.init(collection, elements);
        } else {
            final String referencesList = reader.readField(association.getId());
            if (referencesList == null || referencesList.length() == 0) {
                facet.init(collection, new ObjectAdapter[0]);
            } else {
                final ObjectAdapter[] elements = restoreElements(referencesList, keyCreator);
                facet.init(collection, elements);
            }
        }
    }

    private ObjectAdapter[] restoreElements(final String referencesList, final KeyCreator keyCreator) {
        final String[] references = referencesList.split("\\|");
        final ObjectAdapter[] elements = new ObjectAdapter[references.length];
        for (int i = 0; i < references.length; i++) {
            final ObjectSpecification specification = keyCreator.specificationFromReference(references[i]);
            final Oid oid = keyCreator.oidFromReference(references[i]);
            elements[i] = getAdapter(specification, oid);
        }
        return elements;
    }

    protected ObjectAdapter getAdapter(final ObjectSpecification specification, final Oid oid) {
        final AdapterManager objectLoader = IsisContext.getPersistenceSession().getAdapterManager();
        final ObjectAdapter adapter = objectLoader.getAdapterFor(oid);
        if (adapter != null) {
            return adapter;
        } else {
            return IsisContext.getPersistenceSession().recreateAdapter(oid, specification);
        }
    }
}